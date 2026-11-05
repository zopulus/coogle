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
        SearchBarHooks.install(disabled, loader);
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
                SearchBarHooks.install(hooks, loader);
                SettingsHooks.installGoogleSettingsEntry(hooks, loader);
                check(hooks.installed == (searchEnabled ? 4 : 0) + (entryEnabled ? 1 : 0), "Feature switches are coupled");
            }
        }
        HookInstaller hooks = new HookInstaller();
        SearchBarHooks.install(hooks, loader);
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
        SearchBarHooks.install(partial, loader);
        check(partial.installed == 0, "Partial search hook installation was not removed");
        System.out.println("PASS: optional app startup guards, independent switches, native fallback and installation rollback");
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
