package io.github.zopulus.coogle

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.core.content.edit
import io.github.zopulus.coogle.ui.CoogleScreen
import io.github.zopulus.coogle.ui.CoogleState
import io.github.zopulus.coogle.ui.CoogleTheme
import io.github.zopulus.coogle.ui.CompanionState
import io.github.zopulus.coogle.ui.DialogKind
import io.github.libxposed.service.XposedService
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    companion object {
        // Preserve request order when an Activity is destroyed/recreated with work pending.
        private val worker = Executors.newSingleThreadExecutor()
        private val settingsWorker = Executors.newSingleThreadExecutor()
        // Main-thread state survives Activity recreation together with the queued saves.
        private var savingKeys by mutableStateOf(emptySet<String>())
    }
    private var preferences: SharedPreferences? = null
    private var companionCheckedThisResume = false
    private val main = Handler(Looper.getMainLooper())
    private val preferenceRefresh = Runnable { if (!isDestroyed) syncPreferences() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in CoogleState.featureKeys) {
            main.removeCallbacks(preferenceRefresh)
            main.post(preferenceRefresh)
        }
    }
    private var frameworkResolved = false
    private val connectionTimeout = Runnable {
        if (!frameworkResolved && !isDestroyed) {
            frameworkResolved = true
            state = state.copy(connected = false)
        }
    }
    private val uiCache by lazy { getSharedPreferences("ui_state", MODE_PRIVATE) }
    private var state by mutableStateOf(CoogleState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cached = if (uiCache.getBoolean("features_saved", false))
            CoogleState.featureKeys.associateWith { uiCache.getBoolean(it, FeaturePolicy.defaultValue(it)) } else emptyMap()
        val companion = runCatching { CompanionState.valueOf(uiCache.getString("companion", "UNKNOWN")!!) }
            .getOrDefault(CompanionState.UNKNOWN)
        state = state.copy(features = cached, companion = companion,
            assistant = AssistantSelection.summary(this), iconHidden = iconHidden())
        SettingsConnection.connect(this)
        main.postDelayed(connectionTimeout, 700)
        setContent {
            CoogleTheme {
                CoogleScreen(state, savingKeys, ::changeSetting, ::openAssistant, ::changeIconVisibility,
                    { showDialog(DialogKind.STATUS); if (state.connected == true) checkCompanion() }, ::openRepository,
                    ::confirmDialog, ::dismissDialog,
                    { showDialog(DialogKind.RESTART) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        companionCheckedThisResume = false
        state = state.copy(assistant = AssistantSelection.summary(this), iconHidden = iconHidden())
        refreshKnownCompanion()
    }

    override fun onDestroy() {
        main.removeCallbacks(connectionTimeout)
        main.removeCallbacks(preferenceRefresh)
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        SettingsConnection.detach(this)
        super.onDestroy()
    }

    fun onFrameworkChanged(service: XposedService?) {
        if (isDestroyed) return
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferences = runCatching { service?.getRemotePreferences(FeatureConfig.GROUP) }.getOrNull()
        preferences?.registerOnSharedPreferenceChangeListener(preferenceListener)
        if (service != null || frameworkResolved) {
            frameworkResolved = true
            main.removeCallbacks(connectionTimeout)
            if (preferences == null) state = state.copy(connected = false) else syncPreferences()
        }
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            refreshKnownCompanion()
    }

    private fun syncPreferences() {
        val settings = preferences ?: return
        runCatching { CoogleState.featureKeys.associateWith { settings.getBoolean(it, FeaturePolicy.defaultValue(it)) } }
            .onSuccess { values ->
                state = state.copy(connected = true, features = values)
                uiCache.edit {
                    putBoolean("features_saved", true)
                    values.forEach { (key, value) -> putBoolean(key, value) }
                }
            }.onFailure { state = state.copy(connected = false) }
    }

    private fun refreshKnownCompanion() {
        if (state.scopePending || companionCheckedThisResume) return
        val settings = preferences ?: return
        if (state.companion != CompanionState.UNKNOWN ||
            runCatching { CompanionSettings.shouldCheck(settings) }.getOrDefault(false)) checkCompanion()
    }

    private fun changeSetting(key: String, enabled: Boolean) {
        if (state.connected != true || state.scopePending || state.restarting || preferences == null || key in savingKeys) return
        if (key == FeaturePolicy.HEY_GOOGLE && (state.busy || !state.heyAvailable)) return
        if (state.features[key] == enabled) return
        if (enabled) SettingsConnection.requestScopes(key)
        else saveSetting(key, false)
    }

    fun onScopePending(pending: Boolean) {
        if (!isDestroyed) state = state.copy(scopePending = pending)
    }

    fun onScopeResult(key: String, message: String?) {
        if (isDestroyed) return
        if (message != null) { error(message); return }
        if (preferences == null || key in savingKeys) return
        if (key == FeaturePolicy.HEY_GOOGLE && !state.heyAvailable) return
        saveSetting(key, true)
    }

    private fun saveSetting(key: String, enabled: Boolean) {
        val settings = preferences ?: return
        if (key in savingKeys) return
        savingKeys += key
        val executor = if (key == FeaturePolicy.HEY_GOOGLE || key == FeaturePolicy.WALLET) worker else settingsWorker
        executor.execute {
            var supportState: CompanionState? = null
            val result = runCatching {
                if (key == FeaturePolicy.HEY_GOOGLE) {
                    supportState = CompanionState.valueOf(CompanionSettings.INSTANCE.save(settings, enabled))
                } else if (key == FeaturePolicy.WALLET) {
                    WalletSettings.save(enabled, settings.getBoolean(key, false), WalletPayment(applicationContext), WalletNativeGesture(applicationContext)) { value ->
                        settings.edit().putBoolean(key, value).commit()
                    }
                } else if (!settings.edit().putBoolean(key, enabled).commit()) {
                    throw IOException("配置未保存，请检查 LSPosed 连接。")
                }
            }
            runOnUiThread {
                savingKeys -= key
                if (isDestroyed) return@runOnUiThread
                result.onSuccess {
                    syncPreferences()
                    if (supportState != null) confirmCompanion(supportState)
                }.onFailure { error(it, "配置保存失败，请重试。") }
            }
        }
    }

    private fun checkCompanion() {
        val settings = preferences ?: return
        if (state.busy || state.companionChecking || state.scopePending) return
        companionCheckedThisResume = true
        state = state.copy(companionChecking = true)
        worker.execute {
            val result = runCatching {
                CompanionState.valueOf(CompanionSettings.INSTANCE.refresh(settings))
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                state = state.copy(companionChecking = false)
                result.onSuccess { installed ->
                    syncPreferences()
                    confirmCompanion(installed)
                }.onFailure {
                    state = state.copy(companionError = failureMessage(it,
                        "无法读取附属模块状态，请检查系统支持情况。"))
                }
            }
        }
    }

    private fun installCompanion() {
        val settings = preferences ?: return
        if (state.busy || state.restarting || state.scopePending || state.companionChecking) return
        state = state.beginCompanionInstall()
        worker.execute {
            val result = runCatching {
                CompanionState.valueOf(CompanionSettings.INSTANCE.install(applicationContext, settings))
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                state = state.copy(busy = false)
                syncPreferences()
                result.onSuccess { installed ->
                    confirmCompanion(installed)
                    showDialog(DialogKind.INSTALLED)
                }.onFailure {
                    // Without root, the cached installation status cannot be reconfirmed.
                    if (it is RootShell.Failure && (it.reason == RootShell.Reason.AUTHORIZATION ||
                        it.reason == RootShell.Reason.UNAVAILABLE))
                        state = state.copy(companionError = failureMessage(it, "无法读取附属模块状态。"))
                    error(it, "附属模块安装失败，请检查 KernelSU 是否正常运行。")
                }
            }
        }
    }

    private fun confirmCompanion(value: CompanionState) {
        state = state.copy(companion = value, companionError = "")
        uiCache.edit { putString("companion", value.name) }
    }

    private fun restartScopes() {
        if (state.restarting || state.busy || state.scopePending) return
        state = state.copy(restarting = true)
        // Capture all ordinary saves already queued, including those from a destroyed Activity.
        val pendingSettings = settingsWorker.submit { }
        worker.execute {
            val result = runCatching {
                pendingSettings.get()
                val command = ScopeRestart.command(SettingsConnection.currentScope(), android.os.Process.myUid() / 100000)
                RootShell.run(command, 20)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                state = state.copy(restarting = false)
                result.onSuccess {
                    Toast.makeText(this, "作用域已重启，请重新打开对应应用。", Toast.LENGTH_LONG).show()
                }.onFailure { error(it, "无法重启作用域，请检查系统支持情况。") }
            }
        }
    }

    private fun openAssistant() {
        runCatching { AssistantSelection.open(this) }.onFailure { error("无法打开系统语音助理选择页面。") }
    }

    private fun iconHidden() = packageManager.getComponentEnabledSetting(ComponentName(this, "$packageName.Launcher")) ==
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun changeIconVisibility(hidden: Boolean) {
        if (hidden) showDialog(DialogKind.HIDE_ICON) else setIconHidden(false)
    }

    private fun setIconHidden(hidden: Boolean) {
        runCatching {
            packageManager.setComponentEnabledSetting(ComponentName(this, "$packageName.Launcher"),
                if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP)
            state = state.copy(iconHidden = hidden)
        }.onFailure { error("无法修改桌面图标状态。") }
    }

    private fun confirmDialog() {
        if (state.dialog == DialogKind.STATUS) {
            installCompanion()
            return
        }
        val dialog = state.dialog
        dismissDialog()
        when (dialog) {
            DialogKind.RESTART -> restartScopes()
            DialogKind.HIDE_ICON -> setIconHidden(true)
            else -> Unit
        }
    }

    private fun showDialog(kind: DialogKind) { state = state.copy(dialog = kind) }
    private fun dismissDialog() { state = state.copy(dialog = DialogKind.NONE) }
    private fun error(message: String) { state = state.copy(dialog = DialogKind.ERROR, dialogText = message) }
    private fun error(failure: Throwable, fallback: String) {
        state = state.copy(dialog = if (failure is RootShell.Failure &&
            failure.reason == RootShell.Reason.AUTHORIZATION) DialogKind.ROOT_REQUIRED else DialogKind.ERROR,
            dialogText = failureMessage(failure, fallback))
    }
    private fun failureMessage(failure: Throwable, fallback: String): String {
        if (failure is RootShell.Failure) return failure.message ?: fallback
        // Only our explicit, localized I/O errors are user-facing; platform exceptions may be English.
        return if (failure is IOException && failure.message?.any { it in '\u4e00'..'\u9fff' } == true)
            failure.message!! else fallback
    }
    private fun openRepository() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zopulus/coogle"))) }
            .onFailure { error("请使用浏览器打开 github.com/zopulus/coogle。") }
    }

}
