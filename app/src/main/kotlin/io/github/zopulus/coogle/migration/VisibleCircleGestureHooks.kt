package io.github.zopulus.coogle.migration

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.zopulus.coogle.NativeCircleGestureListener
import io.github.zopulus.coogle.migration.compat.ModernHookRegistry
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime

/** Restore the EXP listener registration in the CN manager's empty lifecycle method. */
internal object VisibleCircleGestureHooks {
    @Volatile private var listener: NativeCircleGestureListener? = null
    private var bound = false

    fun isRegistered() = listener?.isRegistered == true

    @Synchronized fun install(loader: ClassLoader, dispatch: () -> Boolean) {
        if (bound) return
        runCatching {
            val manager = ModernReflect.findClass(
                "com.oplus.systemui.navigationbar.gesture.otherbusiness.circlesearch.OplusCircleToSearchManagerExImpl", loader)
            // A full EXP implementation already owns its independent listener.
            if (manager.declaredMethods.any { it.name == "registerObservers" }) return
            val register = manager.getMethod("registerObservers", Runnable::class.java)
            // CN's empty registerObservers can be inlined into this native caller.
            ModernReflect.findClass("com.android.systemui.navigationbar.NavBarHelper", loader)
                .declaredConstructors.forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            val handle = ModernReflect.findClass(
                "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle", loader)
            val keyguard = ModernReflect.findField(handle, "isKeyguardShowing")
            val inRange = ModernReflect.findField(handle, "inGestureXRange")
            val controllerClass = ModernReflect.findClass(
                "com.oplus.systemui.navigationbar.gesture.otherbusiness.GestureHomeHandleEventController", loader)
            val touchEvent = ModernReflect.findClass("${controllerClass.name}\$HomeHandleTouchEvent", loader)
            val controller = ModernReflect.findField(controllerClass, "INSTANCE").get(null)
            // The native detector callbacks may inline shouldHandlePress from the boot oat file.
            val pressGateCallers = mapOf(
                "1" to setOf("onDown", "onShowPress", "onPreLongPress", "onLongPress", "onScroll"),
                "2" to setOf("onShowPress", "onLongPress"),
            )
            for ((suffix, names) in pressGateCallers) {
                ModernReflect.findClass("${handle.name}\$$suffix", loader).declaredMethods
                    .filter { it.name in names }
                    .forEach { ModernHookRuntime.requireModule().deoptimize(it) }
            }
            val childrenClass = ModernReflect.findClass("com.oplusos.systemui.common.observer.ChildrenModeObserver", loader)
            val children = childrenClass.getMethod("getInstance").invoke(null)
            val childrenEnabled = ModernReflect.findField(childrenClass, "mIsChildrenMode")
            @Suppress("UNCHECKED_CAST")
            val childrenUri = (childrenClass.getMethod("getUri").invoke(children) as java.util.function.Function<String, Uri>)
                .apply(childrenClass.getMethod("getKey").invoke(children) as String)
            ModernHookRegistry.installInterceptor("circle:visible-press-gate", handle.getDeclaredMethod("shouldHandlePress")) { chain ->
                // Same EXP conditions; bypass the before/after adapter in this frequent callback.
                if (isRegistered()) !keyguard.getBoolean(chain.thisObject) && inRange.getBoolean(chain.thisObject)
                else chain.proceed()
            }
            ModernHookRegistry.installCompat("circle:visible-registration", register, object : ModernMethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null || listener != null) return
                    val context = AssistantRuntime.systemContext ?: return
                    val resolver = context.contentResolver
                    val handler = Handler(Looper.getMainLooper())
                    fun isAllowed() = LspConfig.isAssistantNativeCircleEnabledXposed() &&
                        AssistantRuntime.isGestureCircleToSearchEnabled(context) &&
                        NativeCircleStart.isGestureNavigation() && !childrenEnabled.getBoolean(children)
                    val bridge = NativeCircleGestureListener(controller, touchEvent) {
                        NativeCircleStart.afterRecents(context, ::isAllowed) { dispatch() }
                    }
                    fun refresh() {
                        bridge.update(isAllowed())
                    }
                    fun refreshSafely() {
                        runCatching { refresh() }.onFailure {
                            HookLog.w("VisibleCircle", "Could not update native listener", it)
                        }
                    }
                    val change = object : ContentObserver(handler) {
                        override fun onChange(selfChange: Boolean) {
                            refreshSafely()
                        }
                    }
                    try {
                        resolver.registerContentObserver(Settings.Secure.getUriFor("circle_to_search_enable_navi"), false, change)
                        resolver.registerContentObserver(Settings.Secure.getUriFor("hide_navigationbar_enable"), false, change)
                        resolver.registerContentObserver(childrenUri, false, change)
                        refresh()
                    } catch (error: Throwable) {
                        resolver.unregisterContentObserver(change)
                        throw error
                    }
                    listener = bridge
                    handler.post { refreshSafely() }
                    HookLog.i("VisibleCircle", "Restored independent native circle gesture listener")
                }
            })
            bound = true
        }.onFailure { error ->
            listOf("circle:visible-registration", "circle:visible-press-gate").forEach { key ->
                runCatching { ModernHookRegistry.unhook(key) }.exceptionOrNull()?.let { failure ->
                    if (failure !== error) error.addSuppressed(failure)
                }
            }
            HookLog.w("VisibleCircle", "Native listener binding unavailable; partial hooks removed", error)
        }
    }
}
