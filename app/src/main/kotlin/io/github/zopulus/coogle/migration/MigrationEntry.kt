package io.github.zopulus.coogle.migration

import io.github.zopulus.coogle.FeatureConfig
import io.github.zopulus.coogle.FeaturePolicy
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime
import io.github.libxposed.api.XposedModule

/** Entry point for the Google-related features migrated from ONextBox. */
object MigrationEntry {
    @JvmStatic fun attach(module: XposedModule) = ModernHookRuntime.attach(module)
    @JvmStatic fun hookWalletPreview(loader: ClassLoader, apk: String) = WalletPreviewHooks.install(loader, apk)
    @JvmStatic fun hookPackage(packageName: String, loader: ClassLoader) {
        if (packageName == "com.android.systemui") WalletKeyguardHooks.hook(loader)
        if (packageName == "com.oplus.wallpapers") {
            if (FeatureConfig.enabled(FeaturePolicy.QUICK_ENTRIES)) WallpaperQuickEntryHooks.install(loader)
            return
        }
        if (packageName == "com.android.systemui" && FeatureConfig.enabled(FeaturePolicy.QUICK_ENTRIES))
            NativeQuickEntryHooks.install(loader)
        if (packageName == "com.google.android.googlequicksearchbox") {
            OkGoogleHotwordCompatibilityHooker.hookGoogleApp(loader)
        } else {
            AssistantHooker.hook(packageName, loader)
        }
    }
    @JvmStatic fun hookSystem(loader: ClassLoader) {
        WalletPowerHooks.hook(loader)
        GmsRegionRestrictionHooker.hook(loader)
        AssistantHooker.hook("system", loader)
        OkGoogleHotwordCompatibilityHooker.hookSystemServer(loader)
    }
}
