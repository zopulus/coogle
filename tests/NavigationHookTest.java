package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import com.android.launcher3.circlesearch.OplusHideNavHandleController;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import org.luckypray.dexkit.wrap.DexField;
import org.luckypray.dexkit.wrap.DexMethod;

public final class NavigationHookTest {
    public static class Features {
        public static boolean circle() { return false; }
        public static boolean hidden() { return false; }
        public static boolean gemini() { return false; }
    }
    public static class Observers {
        public static final Holder state = new Holder();
        boolean gesture = true, hidden = true, circle = true, broken;
        public boolean a() { return gesture; }
        public boolean b() { return hidden; }
        public boolean c() { if (broken) throw new IllegalStateException("unavailable"); return circle; }
    }
    public static class Holder { public Observers current = new Observers(); public Observers get() { return current; } }
    public static class Caller { public Caller() {} public void unrelated() {} }
    public static void main(String[] args) throws Throwable {
        boolean[] enabled = {false};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, method, a) -> method.getName().equals("getBoolean") ? enabled[0] : null));
        String saved = String.join("\n", descriptor(Features.class, "circle"), descriptor(Features.class, "hidden"),
                descriptor(OplusHideNavHandleController.INSTANCE.class, "isSupportCircleToSearchHandle"),
                descriptor(Features.class, "gemini"), new DexField(Observers.class.getDeclaredField("state")).serialize(),
                descriptor(Observers.class, "a"), descriptor(Observers.class, "b"), descriptor(Observers.class, "c"),
                new DexMethod(Caller.class.getConstructor()).serialize());
        ClassLoader loader = new ClassLoader(NavigationHookTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("com.android.common.config.FeatureOption")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        var directory = Files.createTempDirectory("coogle-navigation-cache");
        var apk = directory.resolve("launcher.apk");
        var cache = directory.resolve("coogle_app_targets_v1.cache");
        try {
            Files.write(apk, new byte[]{1});
            Properties stored = new Properties();
            stored.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":1");
            stored.setProperty("navigation.gates-v1", saved);
            try (var output = Files.newOutputStream(cache)) { stored.store(output, "fixture"); }
            HookInstaller disabled = new HookInstaller();
            new NavigationHooks(disabled).install(loader, apk.toString(), directory.toString());
            check(disabled.installed == 0, "Disabled navigation installed hooks");
            enabled[0] = true;
            HookInstaller hooks = new HookInstaller();
            new NavigationHooks(hooks).install(loader, apk.toString(), directory.toString());
            check(hooks.installed == 4 && hooks.constructorsDeoptimized == 1, "Missing gates or constructor deoptimization");
            var binding = NavigationBinding.parse(loader, saved);
            for (Method gate : List.of(binding.circle, binding.hidden, binding.gemini)) expect(hooks, gate, false, true, 0);
            expect(hooks, binding.supported, false, true, 0);
            Observers state = Observers.state.current;
            state.gesture = false; expect(hooks, binding.supported, true, false, 0);
            state.gesture = true; state.hidden = false; expect(hooks, binding.supported, true, false, 0);
            state.hidden = true; state.circle = false; expect(hooks, binding.supported, true, false, 0);
            state.circle = true; state.broken = true; expect(hooks, binding.supported, true, false, 0);
            state.broken = false; Observers.state.current = null; expect(hooks, binding.supported, true, false, 0);
            Observers.state.current = state;
            enabled[0] = false;
            for (Method gate : List.of(binding.circle, binding.hidden, binding.gemini, binding.supported))
                expect(hooks, gate, false, false, 1);
            enabled[0] = true;
            for (int fail = 1; fail <= 4; fail++) {
                HookInstaller failed = new HookInstaller();
                failed.install(Caller.class.getDeclaredMethod("unrelated"), chain -> chain.proceed());
                failed.failAt = fail + 1;
                new NavigationHooks(failed).install(loader, apk.toString(), directory.toString());
                check(failed.installed == 1 && failed.callbacks.containsKey(Caller.class.getDeclaredMethod("unrelated")),
                        "Navigation failure retained partial hooks or removed an unrelated hook");
            }
            Files.write(apk, new byte[]{1, 2});
            try (var targets = new AppDexTargets(loader, apk.toString(), directory.toString())) {
                try { targets.resolveNavigation(); throw new AssertionError("APK update reused stale cache"); }
                catch (UnsatisfiedLinkError expected) { }
            }
        } finally {
            Files.deleteIfExists(cache); Files.deleteIfExists(apk); Files.deleteIfExists(directory);
        }
        System.out.println("PASS: native observer updates, independent gates, disabled fallback, constructor callers, rollback and APK cache invalidation");
    }
    private static String descriptor(Class<?> type, String name) throws Exception {
        return new DexMethod(type.getDeclaredMethod(name)).serialize();
    }
    private static void expect(HookInstaller hooks, Method method, boolean nativeValue, boolean expected, int nativeCalls) throws Throwable {
        int[] calls = {0};
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p, invoked, args) -> {
                    if (!invoked.getName().equals("proceed")) throw new AssertionError(invoked.getName());
                    calls[0]++; return nativeValue;
                });
        check((boolean) hooks.callbacks.get(method).intercept(chain) == expected && calls[0] == nativeCalls,
                "Wrong native behavior: " + method);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
