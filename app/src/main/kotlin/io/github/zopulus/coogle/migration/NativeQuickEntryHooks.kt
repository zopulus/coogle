package io.github.zopulus.coogle.migration

import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import io.github.zopulus.coogle.NativeQuickEntryPolicy
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime
import io.github.zopulus.coogle.migration.compat.ModernMethodHook
import io.github.zopulus.coogle.migration.compat.ModernReflect

/** Expose existing native entries; their actions and setup requirements remain native. */
internal object NativeQuickEntryHooks {
    fun install(loader: ClassLoader) {
        installPart("lockscreen picker") { hookPicker(loader) }
        installPart("wallet tile") { hookWalletTile(loader) }
        installPart("QR scanner tile") { hookScannerTile(loader) }
    }

    private inline fun installPart(name: String, block: () -> Unit) {
        runCatching(block).onSuccess { HookLog.i("NativeQuickEntries", "Restored $name") }
            .onFailure { HookLog.w("NativeQuickEntries", "Could not restore $name; original behavior retained", it) }
    }

    private fun hookPicker(loader: ClassLoader) {
        val cls = ModernReflect.findClass("com.oplus.keyguard.settings.AffordanceActivity\$SettingsFragment", loader)
        val getResources = ModernReflect.findMethodExact(cls, "getResources", emptyArray())
        val create = ModernReflect.findMethodExact(cls, "onCreatePreferences", arrayOf(Bundle::class.java, String::class.java))
        val getArray = Resources::class.java.getDeclaredMethod("getStringArray", Int::class.javaPrimitiveType)
        val module = ModernHookRuntime.requireModule()
        module.hook(create).intercept { chain ->
            val resources = getResources.invoke(chain.thisObject) as Resources
            val id = resources.getIdentifier("config_keyguardQuickAffordanceKey", "array", "com.android.systemui")
            if (id == 0) return@intercept chain.proceed()
            // This resource hook exists only while the native picker is being built.
            val handle = module.hook(getArray).intercept { read ->
                val result = read.proceed()
                if (read.thisObject === resources && read.args[0] == id) {
                    @Suppress("UNCHECKED_CAST")
                    NativeQuickEntryPolicy.completeLockscreenEntries(result as Array<String>)
                } else result
            }
            try { chain.proceed() } finally { handle.unhook() }
        }
    }

    private fun hookWalletTile(loader: ClassLoader) {
        val cls = ModernReflect.findClass("com.android.systemui.qs.tiles.QuickAccessWalletTile", loader)
        val contextField = ModernReflect.findField(cls, "mContext")
        val hostField = ModernReflect.findField(cls, "mHost")
        val getUserId = ModernReflect.findMethodExact(hostField.type, "getUserId", emptyArray())
        ModernReflect.findAndHookMethod(cls, "isAvailable", emptyArray(), object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tile = param.thisObject ?: return
                val context = contextField.get(tile) as Context
                val pm = context.packageManager
                val walletRoleAvailable = getUserId.invoke(hostField.get(tile)) == 0 &&
                    context.getSystemService(RoleManager::class.java).isRoleAvailable("android.app.role.WALLET")
                // Same native conditions, excluding only the export-region test.
                param.result = (walletRoleAvailable || pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) &&
                    !pm.hasSystemFeature("org.chromium.arc")
            }
        })
    }

    private fun hookScannerTile(loader: ClassLoader) {
        val cls = ModernReflect.findClass("com.oplus.systemui.qs.tiles.OplusQRCodeScannerTile", loader)
        val controllerField = ModernReflect.findField(cls, "qrCodeScannerController")
        val cameraAvailable = ModernReflect.findMethodExact(controllerField.type, "isCameraAvailable", emptyArray())
        ModernReflect.findAndHookMethod(cls, "isAvailable", emptyArray(), object : ModernMethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val tile = param.thisObject ?: return
                param.result = cameraAvailable.invoke(controllerField.get(tile))
            }
        })
    }
}
