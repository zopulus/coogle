// Derived from MiToverG422/ONextBox, commit 1ce6ad3559c9c6ba33f8d8179b1ede55198961e2. GPL-3.0. See LICENSE and NOTICE.txt.
package io.github.zopulus.coogle.migration

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.util.concurrent.ConcurrentHashMap
import io.github.zopulus.coogle.migration.compat.ModernHookBridge as XposedBridge
import io.github.zopulus.coogle.migration.compat.ModernMethodHook as XC_MethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect as XposedHelpers
import io.github.zopulus.coogle.migration.AssistantRuntime.SECURE_ASSISTANT
import io.github.zopulus.coogle.migration.AssistantRuntime.SECURE_VOICE_INTERACTION_SERVICE
import io.github.zopulus.coogle.migration.AssistantRuntime.ASSISTANT_ROLE
import io.github.zopulus.coogle.migration.AssistantRuntime.VOICE_INTERACTION_SERVICE_ACTION
import io.github.zopulus.coogle.migration.AssistantRuntime.BIND_VOICE_INTERACTION_PERMISSION
import io.github.zopulus.coogle.migration.AssistantRuntime.findContext
import io.github.zopulus.coogle.migration.AssistantRuntime.resolveCurrentUserId
import io.github.zopulus.coogle.migration.AssistantRuntime.extractUserId
import io.github.zopulus.coogle.migration.AssistantRuntime.log
import io.github.zopulus.coogle.migration.AssistantRuntime.installedHooks
import io.github.zopulus.coogle.migration.AssistantRuntime.systemContext

internal object AssistantVoiceHooks {
    private val roleHandler by lazy { Handler(Looper.getMainLooper()) }
    private val roleReconciliationTokens = ConcurrentHashMap<Int, Any>()

    /** Assistant-role and voice-service synchronization. */
    fun hookVoiceInteractionServiceRepair(
        packageName: String,
        classLoader: ClassLoader,
    ) {
        val cls = XposedHelpers.findClassIfExists(
            "com.android.server.voiceinteraction.VoiceInteractionManagerService\$" +
                "VoiceInteractionManagerServiceStub",
            classLoader,
        )
        if (cls != null) {
            hookNativeAssistantSessionRecovery(packageName, cls, classLoader)
            hookVoiceServiceStart(packageName, cls)
        }
        hookVoiceServiceUnlock(packageName, classLoader)
        hookAssistantRoleChanges(packageName, classLoader)
    }
    private fun hookVoiceServiceStart(packageName: String, cls: Class<*>) {
        val key = "$packageName:voice-interaction-service-repair:${cls.name}"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.hookAllMethods(
                cls,
                "systemRunning",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        voiceInteractionManagerStub = param.thisObject
                        val context = systemContext ?: findContext(param.thisObject) ?: return
                        val userId = resolveCurrentUserId()
                        reconcileAssistantRoleState(context, userId)
                        scheduleAssistantRoleReconciliation(context, userId)
                    }
                },
            )
            log("Hooked C17 voice-interaction service restoration")
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook C17 voice-interaction service restoration", it)
        }

    }
    private fun hookVoiceServiceUnlock(packageName: String, classLoader: ClassLoader) {
        val serviceClass = XposedHelpers.findClassIfExists(
            "com.android.server.voiceinteraction.VoiceInteractionManagerService",
            classLoader,
        ) ?: return
        val unlockKey = "$packageName:voice-interaction-service-user-unlock"
        if (!installedHooks.add(unlockKey)) return
        runCatching {
            XposedBridge.hookAllMethods(
                serviceClass,
                "onUserUnlocking",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = systemContext ?: findContext(param.thisObject) ?: return
                        val userId = extractUserId(param.args.firstOrNull())
                            ?: resolveCurrentUserId()
                        reconcileAssistantRoleState(context, userId)
                        scheduleAssistantRoleReconciliation(context, userId)
                    }
                },
            )
        }.onFailure {
            installedHooks.remove(unlockKey)
            log("Failed to restore the voice-interaction service after user unlock", it)
        }

    }
    private fun hookAssistantRoleChanges(packageName: String, classLoader: ClassLoader) {
        val roleObserverClass = XposedHelpers.findClassIfExists(
            "com.android.server.voiceinteraction.VoiceInteractionManagerService\$" +
                "VoiceInteractionManagerServiceStub\$RoleObserver",
            classLoader,
        ) ?: return
        val roleKey = "$packageName:assistant-role-reconciliation"
        if (!installedHooks.add(roleKey)) return
        runCatching {
            XposedBridge.hookAllMethods(
                roleObserverClass,
                "onRoleHoldersChanged",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args.firstOrNull() != ASSISTANT_ROLE) return
                        val context = systemContext ?: findContext(param.thisObject) ?: return
                        val userId = extractUserId(param.args.getOrNull(1))
                            ?: resolveCurrentUserId()
                        reconcileAssistantRoleState(context, userId)
                        scheduleAssistantRoleReconciliation(context, userId)
                    }
                },
            )
            log("Hooked the C17 Assistant Role state reconciliation")
        }.onFailure {
            installedHooks.remove(roleKey)
            log("Failed to hook the C17 Assistant Role state reconciliation", it)
        }
    }
    private fun hookNativeAssistantSessionRecovery(
        packageName: String,
        cls: Class<*>,
        classLoader: ClassLoader,
    ) {
        val key = "$packageName:native-assistant-session-recovery"
        if (!installedHooks.add(key)) return
        runCatching {
            XposedBridge.atomic {
                val showMethods = cls.declaredMethods.filter {
                    it.name == "showSessionForActiveService" && it.parameterTypes.size == 5
                }
                check(showMethods.isNotEmpty()) { "Native assistant show method not found" }
                showMethods.forEach { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val stub = param.thisObject ?: return
                            NativeAssistantSessionRecovery.prepare(stub, method, param.args)
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            NativeAssistantSessionRecovery.finishRequest(
                                param.args.getOrNull(3),
                                param.result == true,
                                param.throwable,
                            )
                        }
                    })
                }
                val implClass = XposedHelpers.findClass(
                    "com.android.server.voiceinteraction.VoiceInteractionManagerServiceImpl",
                    classLoader,
                )
                XposedBridge.hookAllMethods(implClass, "showSessionLocked", object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val impl = param.thisObject ?: return
                        NativeAssistantSessionRecovery.prepareCornerSession(
                            impl,
                            param.args.firstOrNull() as? Bundle,
                        )
                    }
                })
                listOf("hideCurrentSession", "hideSessionFromSession", "finish").forEach { name ->
                    XposedBridge.hookAllMethods(cls, name, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (name == "hideCurrentSession") {
                                NativeAssistantSessionRecovery.cancel()
                            } else {
                                val stub = param.thisObject ?: return
                                NativeAssistantSessionRecovery.cancelSession(stub, param.args.firstOrNull())
                            }
                        }
                    })
                }
                log("Hooked native Google assistant session timeout recovery")
            }
        }.onFailure {
            installedHooks.remove(key)
            log("Failed to hook native assistant session recovery", it)
        }
    }
    private fun reconcileAssistantRoleState(context: Context, userId: Int): Boolean {
        val token = Binder.clearCallingIdentity()
        return runCatching {
            val userHandleClass = Class.forName("android.os.UserHandle")
            val user = userHandleClass.getMethod("of", Integer.TYPE).invoke(null, userId)
            val userContext = Context::class.java
                .getMethod("createContextAsUser", userHandleClass, Integer.TYPE)
                .invoke(context, user, 0) as Context
            val roleManager = context.getSystemService(RoleManager::class.java)
                ?: return@runCatching false
            if (!roleManager.isRoleAvailable(ASSISTANT_ROLE)) return@runCatching false

            @Suppress("UNCHECKED_CAST")
            val holders = roleManager.javaClass
                .getMethod("getRoleHoldersAsUser", String::class.java, userHandleClass)
                .invoke(roleManager, ASSISTANT_ROLE, user) as? List<String>
                ?: emptyList()
            val target = if (holders.isEmpty()) {
                AssistantSecureState("", "")
            } else {
                resolveRoleAssistantState(userContext, holders.first())
                    ?: return@runCatching false
            }

            val resolver = userContext.contentResolver
            val oldAssistant = Settings.Secure.getString(resolver, SECURE_ASSISTANT).orEmpty()
            val oldVoiceService = Settings.Secure.getString(
                resolver,
                SECURE_VOICE_INTERACTION_SERVICE,
            ).orEmpty()
            val assistantWritten = oldAssistant == target.assistant || Settings.Secure.putString(
                resolver,
                SECURE_ASSISTANT,
                target.assistant,
            )
            val voiceWritten = oldVoiceService == target.voiceInteractionService ||
                Settings.Secure.putString(
                    resolver,
                    SECURE_VOICE_INTERACTION_SERVICE,
                    target.voiceInteractionService,
                )
            if (oldAssistant != target.assistant || oldVoiceService != target.voiceInteractionService) {
                log(
                    "Reconciled Assistant Role for user $userId: assistant=${target.assistant}, " +
                        "voiceInteractionService=${target.voiceInteractionService}",
                )
            }
            assistantWritten && voiceWritten
        }.onFailure {
            log("Failed to reconcile the C17 Assistant Role state for user $userId", it)
        }.also {
            Binder.restoreCallingIdentity(token)
        }.getOrDefault(false)
    }
    private fun scheduleAssistantRoleReconciliation(context: Context, userId: Int) {
        val token = roleReconciliationTokens.getOrPut(userId) { Any() }
        synchronized(token) {
            roleHandler.removeCallbacksAndMessages(token)
            listOf(1_000L, 5_000L, 15_000L, 30_000L).forEach { delayMs ->
                roleHandler.postDelayed(
                    { reconcileAssistantRoleState(context, userId) },
                    token,
                    delayMs,
                )
            }
        }
    }
    private fun resolveRoleAssistantState(
        userContext: Context,
        packageName: String,
    ): AssistantSecureState? {
        val packageManager = userContext.packageManager
        val serviceQueryFlags = 0x000C0080 // GET_META_DATA + both direct-boot modes.
        val services = packageManager.queryIntentServices(
            Intent(VOICE_INTERACTION_SERVICE_ACTION).setPackage(packageName),
            serviceQueryFlags,
        )
        services.forEach { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@forEach
            if (serviceInfo.permission != BIND_VOICE_INTERACTION_PERMISSION) return@forEach
            val capability = inspectVoiceInteractionService(packageManager, serviceInfo)
                ?: return@forEach
            if (!capability.supportsAssist) return@forEach

            // Match C17/AOSP RoleObserver exactly: a supports-assist service without a declared
            // RecognitionService is cleared to avoid a boot loop on older service implementations.
            val component = if (capability.hasRecognitionService) {
                ComponentName(serviceInfo.packageName, serviceInfo.name).flattenToShortString()
            } else {
                ""
            }
            return AssistantSecureState(component, component)
        }

        val activity = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_ASSIST).setPackage(packageName),
            0x000D0000, // MATCH_DEFAULT_ONLY + both direct-boot modes.
        ).firstOrNull()?.activityInfo ?: return null
        return AssistantSecureState(
            assistant = ComponentName(activity.packageName, activity.name).flattenToShortString(),
            voiceInteractionService = "",
        )
    }
    private fun inspectVoiceInteractionService(
        packageManager: android.content.pm.PackageManager,
        serviceInfo: android.content.pm.ServiceInfo,
    ): VoiceInteractionCapability? {
        return runCatching {
            val infoClass = Class.forName("android.service.voice.VoiceInteractionServiceInfo")
            val info = infoClass
                .getConstructor(
                    android.content.pm.PackageManager::class.java,
                    android.content.pm.ServiceInfo::class.java,
                )
                .newInstance(packageManager, serviceInfo)
            VoiceInteractionCapability(
                supportsAssist = infoClass.getMethod("getSupportsAssist").invoke(info) == true,
                hasRecognitionService =
                    infoClass.getMethod("getRecognitionService").invoke(info) != null,
            )
        }.onFailure {
            log("Failed to inspect ${serviceInfo.packageName}/${serviceInfo.name}", it)
        }.getOrNull()
    }
    /** Refresh the active assistant binding. */
    fun refreshLiveAssistantBinding(context: Context, userId: Int) {
        roleHandler.post {
            reconcileAssistantRoleState(context, userId)
            val stub = voiceInteractionManagerStub
            if (stub == null) {
                log("Skipped live Assistant Role refresh because VIMS is not ready")
                return@post
            }
            runCatching {
                XposedHelpers.callMethod(stub, "switchImplementationIfNeeded", true)
                log("Refreshed the live C17 Assistant Role binding for user $userId")
            }.onFailure {
                log("Failed to refresh the live C17 Assistant Role binding for user $userId", it)
            }
        }
    }
    @Volatile
    private var voiceInteractionManagerStub: Any? = null
    private data class AssistantSecureState(
        val assistant: String,
        val voiceInteractionService: String,
    )
    private data class VoiceInteractionCapability(
        val supportsAssist: Boolean,
        val hasRecognitionService: Boolean,
    )
}
