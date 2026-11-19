package io.github.zopulus.coogle.migration

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.zopulus.coogle.NativeCircleGestureListener
import io.github.zopulus.coogle.migration.compat.ModernHookRegistry
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/** Launch conditions shared by the domestic gesture routes; the international manager runs itself. */
internal object NativeCircleStart {
    private const val RECENTS_METADATA = "com.android.launcher.circle_to_search.recent_animation_finish"
    private const val SPLIT_OBSERVER_HOOK = "circle:split-screen-state"
    @Volatile private var binding: Binding? = null
    @Volatile var usesDomesticBridge = false
        private set

    @Synchronized fun install(loader: ClassLoader) {
        if (binding != null) return
        runCatching {
            val manager = ModernReflect.findClass(
                "com.oplus.systemui.navigationbar.gesture.otherbusiness.circlesearch.OplusCircleToSearchManagerExImpl",
                loader)
            usesDomesticBridge = manager.declaredMethods.none { it.name == "registerObservers" }
            if (!usesDomesticBridge) return
            val resolved = Binding(loader)
            binding = resolved
            Handler(Looper.getMainLooper()).post {
                if (!resolved.ensureSplitObserver()) {
                    HookLog.w("NativeCircleStart", "Split-screen observer unavailable; will retry on gesture")
                }
            }
        }.onFailure {
            runCatching { ModernHookRegistry.unhook(SPLIT_OBSERVER_HOOK) }
            HookLog.w("NativeCircleStart", "Could not bind native launch conditions", it)
        }
    }

    /** Used after the launcher has already completed its own recents callback. */
    fun permitsNow(context: Context): Boolean = binding?.permitsNow(context) == true

    fun isGestureNavigation(): Boolean = binding?.isGestureNavigation() == true

    /** The visible handle owns the touch but still waits for the launcher's native recents callback. */
    fun afterRecents(context: Context, enabled: () -> Boolean, launch: () -> Unit) {
        binding?.afterRecents(context, enabled, launch)
    }

    private class Binding(loader: ClassLoader) {
        private val gestureNavigation = ModernReflect.findMethodExact(
            ModernReflect.findClass("com.oplus.systemui.navigationbar.utils.NavBarUtils", loader),
            "isGestureSideMode", emptyArray())
        private val proxyService = ModernReflect.findClass("com.android.systemui.LauncherProxyService", loader)
        private val dependencyClass = ModernReflect.findClass("com.android.systemui.Dependency", loader)
        private val dependency = ModernReflect.findField(dependencyClass, "sDependency")
        private val getDependency = ModernReflect.findMethodExact(dependencyClass, "getDependencyInner", arrayOf(Any::class.java))
        private val state = ModernReflect.findField(proxyService, "mDefaultDisplaySysUIState")
        private val getFlags = ModernReflect.findMethodExact(state.type, "getFlags", emptyArray())
        private val getProxy = ModernReflect.findMethodExact(proxyService, "getProxy", emptyArray())
        private val childrenClass = ModernReflect.findClass("com.oplusos.systemui.common.observer.ChildrenModeObserver", loader)
        private val childrenObserver = childrenClass.getMethod("getInstance").invoke(null)
        private val childrenMode = ModernReflect.findField(childrenClass, "mIsChildrenMode")
        private val foldRemapDisabled = ModernReflect.findMethodExact(
            ModernReflect.findClass("com.oplusos.systemui.common.feature.FeatureOption", loader),
            "isFoldRemapDisabled", emptyArray())
        private val splitClass = ModernReflect.findClass("com.oplus.splitscreen.OplusSplitScreenManager", loader)
        private val split = ModernReflect.findMethodExact(splitClass, "getInstance", emptyArray()).invoke(null)
        private val innerDisplay = ModernReflect.findMethodExact(splitClass, "isFolderInnerScreen", emptyArray())
        private val observerInterface = ModernReflect.findClass("com.oplus.app.IOplusSplitScreenObserver", loader)
        private val register = ModernReflect.findMethodExact(splitClass, "registerSplitScreenObserver", arrayOf(observerInterface))
        private val observerType = ModernReflect.findClass("com.oplus.app.OplusSplitScreenObserver", loader)
        private val observer = observerType.getConstructor().newInstance()
        private val remoteCallback = Class.forName("android.os.RemoteCallback")
        private val remoteListener = Class.forName("android.os.RemoteCallback\$OnResultListener")
        private val remoteConstructor = remoteCallback.getConstructor(remoteListener, Handler::class.java)
        private val remoteBinder = ModernReflect.findField(remoteCallback, "mCallback")
        private val awaitRecents = ModernReflect.findMethodExact(
            getProxy.returnType, "startCircleToSearchOnRecentsAnimRealFinish", arrayOf(remoteBinder.type))
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var splitMinimized = false
        private var observingSplit = false
        private var needsRecentsCallback: Boolean? = null

        init {
            require(getFlags.returnType == Long::class.javaPrimitiveType)
            val onChanged = ModernReflect.findMethodExact(observerType, "onStateChanged",
                arrayOf(String::class.java, Bundle::class.java))
            ModernHookRegistry.installCompat(SPLIT_OBSERVER_HOOK, onChanged, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.thisObject !== observer || param.args.getOrNull(0) != "splitScreenMinimizedChange") return
                    splitMinimized = (param.args.getOrNull(1) as? Bundle)?.getBoolean("isMinimized", false) ?: return
                }
            })
        }

        @Synchronized fun ensureSplitObserver(): Boolean {
            if (!observingSplit) {
                observingSplit = runCatching { register.invoke(split, observer) == true }.getOrDefault(false)
            }
            return observingSplit
        }

        private fun launcherService(): Any? =
            dependency.get(null)?.let { getDependency.invoke(it, proxyService) }

        fun isGestureNavigation(): Boolean = runCatching { gestureNavigation.invoke(null) == true }.getOrDefault(false)

        fun permitsNow(context: Context): Boolean = runCatching {
            val activity = context.getSystemService(ActivityManager::class.java) ?: return false
            if (!isGestureNavigation() || childrenMode.getBoolean(childrenObserver) ||
                activity.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE || !ensureSplitObserver()) return false
            val service = launcherService() ?: return false
            val flags = getFlags.invoke(state.get(service)) as Long
            val foldRemap = foldRemapDisabled.invoke(null) == true
            NativeCircleGestureListener.canStart(flags, splitMinimized, foldRemap,
                foldRemap && innerDisplay.invoke(split) == true)
        }.getOrDefault(false)

        fun afterRecents(context: Context, enabled: () -> Boolean, launch: () -> Unit) {
            if (!enabled()) return
            fun finish() {
                if (enabled() && permitsNow(context)) launch()
            }
            runCatching {
                val wait = needsRecentsCallback ?: (context.packageManager
                    .getApplicationInfo("com.android.launcher", PackageManager.GET_META_DATA)
                    .metaData?.getBoolean(RECENTS_METADATA) == true).also { needsRecentsCallback = it }
                if (!wait) {
                    finish()
                    return
                }
                val service = launcherService() ?: return
                val proxy = getProxy.invoke(service) ?: return
                val pending = AtomicBoolean(true)
                val listener = Proxy.newProxyInstance(remoteListener.classLoader, arrayOf(remoteListener)) { _, method, _ ->
                    if (method.name == "onResult" && pending.getAndSet(false)) finish()
                    null
                }
                val callback = remoteConstructor.newInstance(listener, main)
                awaitRecents.invoke(proxy, remoteBinder.get(callback))
            }.onFailure { HookLog.w("NativeCircleStart", "Could not wait for native recents animation", it) }
        }
    }
}
