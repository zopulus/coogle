package io.github.zopulus.coogle.migration

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import io.github.zopulus.coogle.FeatureConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.migration.compat.ModernHookBridge
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

/** Preserve the observed original initial-preview gate, without changing Google's auth result. */
internal object WalletPreviewHooks {
    private const val VERIFIED_APK = "cb824c84a17b28b5bb13e485af3460719ba209ece789e5a454813806bd18130e"
    private const val WALLET = "com.google.android.apps.walletnfcrel"
    private const val PRESENTATION = "com.google.android.apps.wallet.pmode.PresentationModeActivity"
    private const val QUICKDRAW = "com.google.android.apps.wallet.main.QUICKDRAW"
    private val paymentExtras = listOf("paymentCardInfo", "safePaymentCardInfo", "valuables", "eventType",
        "failedReason", "failedUiInfo", "merchantName", "isTransit", "idCardDocumentRequestBytes")
    private data class Initializing(val model: Any?, val preview: Boolean)
    private val initializing = ThreadLocal<ArrayDeque<Initializing>>()

    fun install(loader: ClassLoader, apk: String) {
        if (!FeatureConfig.enabled(FeaturePolicy.WALLET)) return
        runCatching {
            // This authentication-sensitive binding is verified against this complete APK only.
            // Updates retain Wallet's original behavior until their implementation is reviewed.
            val digest = MessageDigest.getInstance("SHA-256")
            File(apk).inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val verified = digest.digest().joinToString("") { "%02x".format(it) } == VERIFIED_APK
            if (!verified) {
                HookLog.w("WalletPreview", "Unverified Wallet version; original authentication retained")
                return
            }
            bindVerified(loader)
        }.onFailure { HookLog.w("WalletPreview", "Preview binding unavailable; original authentication retained", it) }
    }

    private fun bindVerified(loader: ClassLoader) {
        val modelType = ModernReflect.findClass("xir", loader)
        val callbackType = ModernReflect.findClass("xht", loader)
        val resultType = ModernReflect.findClass("qag", loader)
        val recent = ModernReflect.findField(resultType, "a")
        require(recent.type == Boolean::class.javaPrimitiveType)
        val modelField = ModernReflect.findField(callbackType, "b")
        require(modelField.type == modelType)
        val responseField = ModernReflect.findField(callbackType, "a")
        val unwrap = ModernReflect.findClass("axcc", loader).declaredMethods.single {
            it.name == "a" && it.parameterTypes.map(Class<*>::getName) == listOf("axch")
        }.apply { isAccessible = true }
        val unit = ModernReflect.findClass("brat", loader).getDeclaredField("a").apply { isAccessible = true }.get(null)
        val constructor = modelType.declaredConstructors.single()
        require(Context::class.java.isAssignableFrom(constructor.parameterTypes.first()))
        ModernHookBridge.atomic {
            ModernHookBridge.hookMethod(constructor, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val stack = initializing.get() ?: ArrayDeque<Initializing>().also(initializing::set)
                    val preview = runCatching {
                        FeatureConfig.enabled(FeaturePolicy.WALLET) && isBarePreview(param.args[0] as Context)
                    }.getOrDefault(false)
                    stack.addLast(Initializing(param.thisObject, preview))
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    initializing.get()?.let { stack ->
                        stack.removeLast()
                        if (stack.isEmpty()) initializing.remove()
                    }
                }
            })
            ModernReflect.findAndHookMethod(callbackType, "b", Any::class.java, object : ModernMethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val window = initializing.get()?.peekLast() ?: return
                    if (!window.preview || param.args[0] !== unit || !FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                    val keepInitialPreview = runCatching {
                        val receiver = param.thisObject ?: return@runCatching false
                        val model = modelField.get(receiver) ?: return@runCatching false
                        if (model !== window.model) return@runCatching false
                        val response = unwrap.invoke(null, responseField.get(receiver))
                        if (!resultType.isInstance(response) || !recent.getBoolean(response)) return@runCatching false
                        val context = ModernReflect.getObjectField(model, "b") as Context
                        if (!context.getSystemService(KeyguardManager::class.java).isDeviceLocked) return@runCatching false
                        val ui = ModernReflect.callMethod(model, "a") ?: return@runCatching false
                        if (ModernReflect.getObjectField(ui, "g") != null ||
                            (ModernReflect.getObjectField(ui, "h") as List<*>).isNotEmpty()) return@runCatching false
                        ModernReflect.callMethod(ModernReflect.getObjectField(model, "q"), "e") == false
                    }.getOrDefault(false)
                    if (keepInitialPreview) {
                        // xht.b only sets the preview request here, then returns coroutine Unit.
                        // Neither authentication data nor an already requested CVM is cleared.
                        param.result = unit
                        HookLog.i("WalletPreview", "Native recent-auth initial preview retained")
                    }
                }
            })
        }
        HookLog.i("WalletPreview", "Verified initial-preview gate installed")
    }

    private fun isBarePreview(context: Context): Boolean {
        val task = context.getSystemService(ActivityManager::class.java).getRunningTasks(1).firstOrNull() ?: return false
        val top = task.topActivity ?: return false
        if (top.packageName != WALLET || top.className != PRESENTATION) return false
        val intent = task.baseIntent ?: return false
        return intent.action == QUICKDRAW && paymentExtras.none(intent::hasExtra)
    }
}
