// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.zopulus.coogle.migration

import android.content.Context
import io.github.zopulus.coogle.migration.compat.ModernHookRegistry
import io.github.zopulus.coogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect as XposedHelpers
import io.github.zopulus.coogle.migration.AssistantLauncherHooks.hookLauncher
import io.github.zopulus.coogle.migration.AssistantSettingsHooks.hookSettings
import io.github.zopulus.coogle.migration.AssistantSystemUiHooks.hookSystemUi
import io.github.zopulus.coogle.migration.AssistantFeatureHooks.hookNativeAssistantFeatureRoute
import io.github.zopulus.coogle.migration.AssistantPowerHooks.hookNativePowerLongPress
import io.github.zopulus.coogle.migration.AssistantVoiceHooks.hookVoiceInteractionServiceRepair
import io.github.zopulus.coogle.migration.ContextualSearchHooks.hookContextualSearch
import io.github.zopulus.coogle.migration.AssistantRuntime.SYSTEM_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.ANDROID_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.SYSTEM_UI_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.LAUNCHER_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.SETTINGS_PACKAGE
import io.github.zopulus.coogle.migration.AssistantRuntime.log
import io.github.zopulus.coogle.migration.AssistantRuntime.installedHooks
import io.github.zopulus.coogle.migration.AssistantRuntime.systemContext

object AssistantHooker {
    fun hook(packageName: String, classLoader: ClassLoader) {
        when (packageName) {
            SYSTEM_PACKAGE, ANDROID_PACKAGE -> hookSystem(packageName, classLoader)
            SYSTEM_UI_PACKAGE -> hookSystemUi(classLoader)
            LAUNCHER_PACKAGE -> hookLauncher(classLoader)
            SETTINGS_PACKAGE -> hookSettings(classLoader)
        }
    }
    private fun hookSystem(packageName: String, classLoader: ClassLoader) {
        hookSystemContext(packageName, classLoader)
        retrySystemHooks(packageName, classLoader)
    }
    private fun hookSystemContext(packageName: String, classLoader: ClassLoader) {
        val key = "$packageName:system-context"
        val cls = XposedHelpers.findClassIfExists("com.android.server.SystemService", classLoader) ?: return
        if (!installedHooks.add(key)) return
        runCatching {
            cls.declaredMethods
                .filter { method -> method.name == "getContext" }
                .forEach { method ->
                    val handleKey = "assistant:system-context:${method.toGenericString()}"
                    ModernHookRegistry.installCompat(
                        handleKey,
                        method,
                        object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.result as? Context)?.let {
                            systemContext = it
                            sequenceOf(
                                param.thisObject?.javaClass?.classLoader,
                                it.classLoader,
                                Thread.currentThread().contextClassLoader
                            ).filterNotNull().distinct().forEach { runtimeClassLoader ->
                                retrySystemHooks(packageName, runtimeClassLoader)
                            }
                            // SystemService#getContext is common during boot. It is needed only to
                            // capture the first valid context and retry late OEM targets once.
                            ModernHookRegistry.unhookPrefix("assistant:system-context:")
                        }
                    }
                        },
                    )
                }
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook SystemService.getContext", it)
        }
    }
    private fun retrySystemHooks(packageName: String, classLoader: ClassLoader) {
        if (LspConfig.isAssistantNativeCircleEnabledXposed()) {
            hookContextualSearch(packageName, classLoader)
        }
        hookNativeAssistantFeatureRoute(packageName, classLoader)
        // Keep the stock ColorOS power-key channel (native Breeno launch animation) when the
        // user opts in; corner-gesture and Circle to Search dispatch stay on the AOSP path.
        if (!LspConfig.isNativePowerAssistantPreservedXposed()) {
            hookNativePowerLongPress(packageName, classLoader)
        }
        hookVoiceInteractionServiceRepair(packageName, classLoader)
    }
}
