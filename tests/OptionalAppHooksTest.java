package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import com.android.common.config.FeatureOption;
import com.oplus.settings.feature.homepage.controller.GooglePreferenceController;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Verify optional app hooks at startup and while their switches change. */
public final class OptionalAppHooksTest {
    public static void main(String[] args) throws Throwable {
        Map<String, Boolean> enabled = new HashMap<>();
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, method, a) -> {
                    if (!method.getName().equals("getBoolean")) throw new AssertionError(method.getName());
                    return enabled.getOrDefault((String) a[0], false);
                }));
        int[] lookups = {0};
        ClassLoader loader = new ClassLoader(OptionalAppHooksTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(FeatureOption.class.getName()) || name.equals(GooglePreferenceController.class.getName()))
                    lookups[0]++;
                return super.loadClass(name, resolve);
            }
        };
        HookInstaller disabled = new HookInstaller();
        SearchBarHooks.install(disabled, loader, null, null);
        SettingsHooks.installGoogleSettingsEntry(disabled, loader);
        check(disabled.installed == 0 && lookups[0] == 0, "Disabled features resolved targets or installed hooks");

        Method query = FeatureOption.class.getDeclaredMethod("getHasQsbAtBottomFeature");
        Method breeno = FeatureOption.class.getDeclaredMethod("getSIsSupportBreenoEntry");
        Method search = FeatureOption.class.getDeclaredMethod("getSIsSupportSearchEntry");
        Method init = FeatureOption.class.getDeclaredMethod("initFeature", android.content.Context.class);
        Method availability = GooglePreferenceController.class.getDeclaredMethod("getAvailabilityStatus");
        for (boolean searchEnabled : new boolean[]{false, true}) {
            for (boolean entryEnabled : new boolean[]{false, true}) {
                enabled.put(FeaturePolicy.SEARCH_BAR, searchEnabled);
                enabled.put(FeaturePolicy.GOOGLE_ENTRY, entryEnabled);
                HookInstaller hooks = new HookInstaller();
                SearchBarHooks.install(hooks, loader, null, null);
                SettingsHooks.installGoogleSettingsEntry(hooks, loader);
                check(hooks.installed == (searchEnabled ? 4 : 0) + (entryEnabled ? 1 : 0), "Feature switches are coupled");
            }
        }
        HookInstaller hooks = new HookInstaller();
        SearchBarHooks.install(hooks, loader, null, null);
        SettingsHooks.installGoogleSettingsEntry(hooks, loader);
        expect(hooks, query, false, true, 0);
        expect(hooks, breeno, false, true, 0);
        expect(hooks, search, true, false, 0);
        expect(hooks, availability, 3, 0, 0);
        FeatureOption.isExp = false;
        expect(hooks, init, null, null, 1);
        check(FeatureOption.isExp, "Enabled QSB lost the native export implementation");

        enabled.clear();
        expect(hooks, query, false, false, 1);
        expect(hooks, breeno, false, false, 1);
        expect(hooks, search, true, true, 1);
        expect(hooks, availability, 3, 3, 1);
        FeatureOption.isExp = false;
        expect(hooks, init, null, null, 1);
        check(!FeatureOption.isExp, "Disabled QSB still changed the export flag");
        check(disabled.installed == 0, "Previously disabled process installed hooks without a restart");

        enabled.put(FeaturePolicy.SEARCH_BAR, true);
        HookInstaller partial = new HookInstaller(); partial.failAt = 2;
        SearchBarHooks.install(partial, loader, null, null);
        check(partial.installed == 0, "Partial search hook installation was not removed");
        SearchBarBindingTest.main(args);
        testRenamed(enabled);
        System.out.println("PASS: optional app startup guards, independent switches, native fallback and installation rollback");
    }

    private static void testRenamed(Map<String, Boolean> enabled) throws Throwable {
        var directory = java.nio.file.Files.createTempDirectory("coogle-renamed-launcher");
        var apk = directory.resolve("launcher.apk");
        var cache = directory.resolve("coogle_app_targets_v1.cache");
        try {
            java.nio.file.Files.write(apk, new byte[]{1});
            var stored = new java.util.Properties();
            stored.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":1");
            stored.setProperty("search_bar.features-v1", SearchBarBindingTest.fixture());
            try (var output = java.nio.file.Files.newOutputStream(cache)) { stored.store(output, "renamed launcher"); }
            ClassLoader loader = new ClassLoader(OptionalAppHooksTest.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.equals(FeatureOption.class.getName())) throw new ClassNotFoundException(name);
                    return super.loadClass(name, resolve);
                }
            };
            var binding = SearchBarBinding.parse(loader, SearchBarBindingTest.fixture());
            HookInstaller hooks = new HookInstaller();
            SearchBarHooks.install(hooks, loader, apk.toString(), directory.toString());
            check(hooks.installed == 4, "Obfuscated launcher lost its search hooks");
            SearchBarBindingTest.Renamed.export = false;
            SearchBarBindingTest.Renamed.breeno = false;
            SearchBarBindingTest.Renamed.search = true;
            expect(hooks, binding.init, null, null, 1);
            check(SearchBarBindingTest.Renamed.export && SearchBarBindingTest.Renamed.breeno
                    && !SearchBarBindingTest.Renamed.search, "Wrong renamed launcher flags");
            expect(hooks, binding.query, false, true, 0);
            SearchBarBindingTest.Renamed.breeno = false;
            SearchBarBindingTest.Renamed.search = true;
            expect(hooks, binding.breeno, null, null, 1);
            expect(hooks, binding.search, null, null, 1);
            check(SearchBarBindingTest.Renamed.breeno && !SearchBarBindingTest.Renamed.search, "Package refresh reset entry flags");
            enabled.put(FeaturePolicy.SEARCH_BAR, false);
            SearchBarBindingTest.Renamed.export = false;
            SearchBarBindingTest.Renamed.breeno = false;
            SearchBarBindingTest.Renamed.search = true;
            expect(hooks, binding.init, null, null, 1);
            expect(hooks, binding.breeno, null, null, 1);
            expect(hooks, binding.search, null, null, 1);
            expect(hooks, binding.query, false, false, 1);
            check(!SearchBarBindingTest.Renamed.export && !SearchBarBindingTest.Renamed.breeno
                    && SearchBarBindingTest.Renamed.search, "Disabled renamed hooks changed native flags");
            enabled.put(FeaturePolicy.SEARCH_BAR, true);
            HookInstaller failed = new HookInstaller(); failed.failAt = 3;
            SearchBarHooks.install(failed, loader, apk.toString(), directory.toString());
            check(failed.installed == 0, "Renamed launcher partial hooks retained");
        } finally {
            java.nio.file.Files.deleteIfExists(cache); java.nio.file.Files.deleteIfExists(apk);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }
    private static void expect(HookInstaller hooks, Method method, Object nativeResult, Object expected, int nativeCalls) throws Throwable {
        XposedInterface.Hooker hook = hooks.callbacks.get(method);
        check(hook != null, "Missing hook: " + method.getName());
        int[] calls = {0};
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p, invoked, args) -> {
                    if (!invoked.getName().equals("proceed")) throw new AssertionError(invoked.getName());
                    calls[0]++; return nativeResult;
                });
        check(java.util.Objects.equals(hook.intercept(chain), expected) && calls[0] == nativeCalls,
                "Wrong native dispatch: " + method.getName());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
