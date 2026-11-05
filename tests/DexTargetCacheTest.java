package io.github.zopulus.coogle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.luckypray.dexkit.wrap.DexMethod;

/** Runs without a DexKit native library: valid caches must not initialize the scanner. */
public final class DexTargetCacheTest {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("coogle-cache-test");
        Path apk = dir.resolve("base.apk");
        Path cache = dir.resolve("coogle_app_targets_v1.cache");
        try {
            Files.write(apk, new byte[]{1});
            Properties stale = new Properties();
            stale.setProperty("apk", "1:" + apk + ":" + apk.toFile().lastModified() + ":" + apk.toFile().length());
            stale.setProperty(FeaturePolicy.LENS, "");
            try (var output = Files.newOutputStream(cache)) { stale.store(output, "pre-fix negative cache"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                try { targets.resolve(FeaturePolicy.LENS); throw new AssertionError("Old negative cache survived the locator update"); }
                catch (UnsatisfiedLinkError expected) { /* Cache was invalidated and a fresh scan was requested. */ }
            }

            stale.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":" + apk.toFile().length());
            try (var output = Files.newOutputStream(cache)) { stale.store(output, "1.3.4 Lens layout miss"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                try { targets.resolve(FeaturePolicy.LENS); throw new AssertionError("Google 17.68 layout miss survived the locator update"); }
                catch (UnsatisfiedLinkError expected) { /* Same APK must be rescanned by the corrected layout matcher. */ }
            }

            stale.remove(FeaturePolicy.LENS);
            stale.setProperty(FeaturePolicy.LENS + ".controllers-v2", "");
            try (var output = Files.newOutputStream(cache)) { stale.store(output, "1.3.5 Lens layout miss"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                try { targets.resolve(FeaturePolicy.LENS); throw new AssertionError("Google 17.70 layout miss survived the locator update"); }
                catch (UnsatisfiedLinkError expected) { /* The revised Talk layout requires a new cache key. */ }
            }

            stale.remove(FeaturePolicy.LENS + ".controllers-v2");
            stale.setProperty(FeaturePolicy.LENS + ".controllers-v3", "");
            try (var output = Files.newOutputStream(cache)) { stale.store(output, "1.3.6 Lens layout miss"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                try { targets.resolve(FeaturePolicy.LENS); throw new AssertionError("Google 17.72 layout miss survived the locator update"); }
                catch (UnsatisfiedLinkError expected) { /* The additional dependency requires a new cache key. */ }
            }

            var create = LensBindingTest.RenamedCreate.class.getDeclaredMethod("renamed");
            var talk = LensBindingTest.CurrentTalk.class.getDeclaredMethod("renamed");
            Properties stored = new Properties();
            stored.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":" + apk.toFile().length());
            // Obsolete entries may coexist until the APK identity changes; only the current revision is read.
            stored.setProperty(FeaturePolicy.LENS, "");
            stored.setProperty(FeaturePolicy.LENS + ".controllers-v2", "");
            stored.setProperty(FeaturePolicy.LENS + ".controllers-v3", "");
            stored.setProperty(FeaturePolicy.LENS + ".controllers-v4", new DexMethod(create).serialize() + "\n" + new DexMethod(talk).serialize());
            stored.setProperty(FeaturePolicy.WALLET, "");
            stored.setProperty(FeaturePolicy.CAMERA_LENS, "");
            stored.setProperty("wallet.preview-candidates-v2", "");
            try (var output = Files.newOutputStream(cache)) { stored.store(output, "fixture"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                var methods = targets.resolve(FeaturePolicy.LENS);
                if (!methods.equals(java.util.List.of(create, talk))) throw new AssertionError("Cached methods changed");
                try { targets.resolve("wallet.preview-candidates-v2"); throw new AssertionError("Retired discovery accepted cached targets"); }
                catch (IllegalArgumentException expected) { /* Diagnostic discovery is not a runtime feature. */ }
                try { targets.resolve(FeaturePolicy.WALLET); throw new AssertionError("Retired wallet locator accepted cached targets"); }
                catch (IllegalArgumentException expected) { /* Retired features never initialize DexKit. */ }
                try { targets.resolve(FeaturePolicy.CAMERA_LENS); throw new AssertionError("Former camera miss suppressed native lookup"); }
                catch (UnsatisfiedLinkError expected) { /* A new native binding requires its own cache key. */ }
            }
            stored.setProperty(FeaturePolicy.CAMERA_LENS + ".native-v1", "");
            stored.setProperty(FeaturePolicy.LENS + ".controllers-v4", "");
            try (var output = Files.newOutputStream(cache)) { stored.store(output, "native negative cache"); }
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                if (!targets.resolve(FeaturePolicy.CAMERA_LENS).isEmpty()) throw new AssertionError("Native negative cache ignored");
                if (!targets.resolve(FeaturePolicy.LENS).isEmpty()) throw new AssertionError("Current Lens negative cache ignored");
            }
            Files.write(apk, new byte[]{1,2});
            try (var targets = new AppDexTargets(DexTargetCacheTest.class.getClassLoader(), apk.toString(), dir.toString())) {
                try { targets.resolve(FeaturePolicy.WALLET); throw new AssertionError("Retired wallet locator scanned APK"); }
                catch (IllegalArgumentException expected) { /* No cached result must still reject Wallet. */ }
                try { targets.resolve(FeaturePolicy.CAMERA_LENS); throw new AssertionError("APK update retained negative cache"); }
                catch (UnsatisfiedLinkError expected) { /* Scanner was attempted; no native library in this test. */ }
                try { targets.resolve(FeaturePolicy.LENS); throw new AssertionError("APK update retained Lens cache"); }
                catch (UnsatisfiedLinkError expected) { /* Lens discovery rescans after an APK update. */ }
                try { targets.resolve("wallet.preview-candidates-v2"); throw new AssertionError("Retired discovery scanned APK"); }
                catch (IllegalArgumentException expected) { /* APK updates cannot reactivate diagnostic discovery. */ }
            }
            System.out.println("PASS: locator update invalidates old misses; valid caches skip native scanning; APK update invalidates targets");
        } finally {
            Files.deleteIfExists(cache); Files.deleteIfExists(apk); Files.deleteIfExists(dir);
        }
    }
}
