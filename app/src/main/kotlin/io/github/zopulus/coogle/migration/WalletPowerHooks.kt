package io.github.zopulus.coogle.migration

import android.content.Context
import io.github.zopulus.coogle.FeatureConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernHookBridge
import io.github.zopulus.coogle.migration.compat.ModernReflect
import java.lang.reflect.Modifier

/** Allow the selected native gesture through the CN region gate and prevent duplicate dispatch. */
internal object WalletPowerHooks {
    fun hook(loader: ClassLoader) {
        runCatching {
            val target = ModernReflect.findClass("com.android.server.policy.OplusWalletController", loader)
            val contextField = ModernReflect.findField(target, "mContext")
            val userField = ModernReflect.findField(target, "mCurrentUserId")
            require(Context::class.java.isAssignableFrom(contextField.type))
            require(userField.type == Int::class.javaPrimitiveType)
            val gestures = ModernReflect.findClass("com.android.server.GestureLauncherService", loader)
            val nativeSelected = ModernReflect.findMethodExact(gestures, "isWalletDoubleTapPowerSettingEnabled",
                arrayOf(Context::class.java, Int::class.javaPrimitiveType!!))
            require(nativeSelected.returnType == Boolean::class.javaPrimitiveType)
            require(Modifier.isStatic(nativeSelected.modifiers))
            require(ModernReflect.findMethodExact(target, "isQuickLaunchSupport", emptyArray()).returnType == Boolean::class.javaPrimitiveType)
            val policy = ModernReflect.findClass("com.android.server.policy.PhoneWindowManagerExtImpl", loader)
            val policyContext = ModernReflect.findField(policy, "mContext")
            val policyUser = ModernReflect.findField(policy, "mCurrentUserId")
            val shutdownChord = ModernReflect.findField(policy, "mShutdownChordEnabled")
            val sosCount = ModernReflect.findField(policy, "sSosPowerKeyDownCount")
            require(Context::class.java.isAssignableFrom(policyContext.type))
            require(policyUser.type == Int::class.javaPrimitiveType)
            require(shutdownChord.type == Boolean::class.javaPrimitiveType && !Modifier.isStatic(shutdownChord.modifiers))
            require(sosCount.type == Int::class.javaPrimitiveType && Modifier.isStatic(sosCount.modifiers))
            require(ModernReflect.findMethodExact(policy, "isCameraGestureEnabled", emptyArray()).returnType == Boolean::class.javaPrimitiveType)
            ModernHookBridge.atomic {
                ModernReflect.findAndHookMethod(policy, "isCameraGestureEnabled", emptyArray(),
                    object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.result != false || !FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                            val windowPolicy = param.thisObject ?: return
                            // Fill only the missing regional capability; keep the original three-press SOS exclusion.
                            if (shutdownChord.getBoolean(windowPolicy) || sosCount.getInt(null) == 3) return
                            val context = policyContext.get(windowPolicy) as? Context ?: return
                            if (nativeSelected.invoke(null, context, policyUser.getInt(windowPolicy)) == true) param.result = true
                        }
                    },
                )
                ModernReflect.findAndHookMethod(target, "isQuickLaunchSupport", emptyArray(),
                    object : ModernMethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (param.result != true || !FeatureConfig.enabled(FeaturePolicy.WALLET)) return
                            val wallet = param.thisObject ?: return
                            val context = contextField.get(wallet) as? Context ?: return
                            val userId = userField.getInt(wallet)
                            // Preserve the platform flag, resource mode, selected action and current-user checks.
                            if (nativeSelected.invoke(null, context, userId) == true) param.result = false
                        }
                    },
                )
            }
            HookLog.i("WalletPowerHooks", "Installed native Wallet region gate and duplicate-route guard")
        }.onFailure { HookLog.w("WalletPowerHooks", "Native wallet binding unavailable; original behavior retained", it) }
    }
}
