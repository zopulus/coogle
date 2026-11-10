// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.zopulus.coogle.migration

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.lang.reflect.Method
import io.github.zopulus.coogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.zopulus.coogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect as XposedHelpers
import io.github.zopulus.coogle.migration.AssistantFeatureHooks.hookContextualSearchSystemFeature
import io.github.zopulus.coogle.migration.AssistantRuntime.LAUNCHER_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.findContext
import io.github.zopulus.coogle.migration.AssistantRuntime.resolveDefaultAssistant
import io.github.zopulus.coogle.migration.AssistantRuntime.isGestureCornerAssistantEnabled
import io.github.zopulus.coogle.migration.AssistantRuntime.log
import io.github.zopulus.coogle.migration.AssistantRuntime.installedHooks

internal object AssistantLauncherHooks {
    /** Launcher gesture-bar assistant routing. */
    fun hookLauncher(classLoader: ClassLoader) {
        if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
        hookContextualSearchSystemFeature(LAUNCHER_PACKAGE, classLoader)
        hookLauncherAssistantAvailability(classLoader)

        val featureClass = XposedHelpers.findClassIfExists(
            "com.android.common.config.FeatureOption",
            classLoader,
        ) ?: return
        val key = "launcher:native-circle-feature-option"
        if (!installedHooks.add(key)) return
        runCatching {
            // NavigationHooks owns the Circle to Search gates and their caller deoptimization.
            XposedBridge.hookAllMethods(
                featureClass,
                "isSupportGemini",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
                            param.result = true
                        }
                    }
                },
            )
            log("Enabled the ColorOS 17 launcher Gemini feature gate")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to enable the ColorOS 17 launcher Circle to Search route", it)
        }
        // Standalone CN17 build retains native e(93, Context) and its recents-animation wait.
        // The upstream override uses firmware-specific proxy names and is unnecessary here.
    }
    /** Assistant availability reporting. */
    @Volatile private var availabilityCallback: (() -> Unit)? = null
    @Volatile private var availabilityContext: Context? = null
    private var availabilityObserver: ContentObserver? = null

    @Synchronized
    private fun observeCornerAvailability(context: Context, receiver: Any, method: Method, longPressAvailable: Boolean) {
        val appContext = context.applicationContext ?: context
        availabilityCallback = {
            // Reuse the native binder callback; it schedules the device-state update on the launcher thread.
            method.invoke(receiver, false, longPressAvailable)
        }
        if (availabilityObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                runCatching { availabilityCallback?.invoke() }.onFailure {
                    log("Failed to refresh launcher corner-assistant availability", it)
                }
            }
        }
        appContext.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("circle_to_search_corner_assist_enable_navi"), false, observer)
        availabilityObserver = observer
    }

    private fun hookLauncherAssistantAvailability(classLoader: ClassLoader) {
        val serviceClass = XposedHelpers.findClassIfExists(
            "com.android.quickstep.TouchInteractionService",
            classLoader,
        ) ?: return
        LauncherAssistantBinding.findCallbacks(serviceClass)
            .forEach { method ->
                val key = "launcher:assistant-available:${method.toGenericString()}"
                if (!installedHooks.add(key)) return@forEach
                runCatching {
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!LspConfig.isAssistantNativeCircleEnabledXposed()) return
                                val context = availabilityContext ?: findContext(param.thisObject) ?: return
                                availabilityContext = context.applicationContext ?: context
                                param.args[0] = isGestureCornerAssistantEnabled(context) &&
                                    resolveDefaultAssistant(context) != null
                                observeCornerAvailability(context, param.thisObject ?: return, method,
                                    param.args[1] as Boolean)
                            }
                        },
                    )
                    log("Restored the C17 launcher corner-assistant availability signal")
                }.onFailure {
                    installedHooks.remove(key)
                    log("Failed to restore launcher assistant availability", it)
                }
            }
    }
}
