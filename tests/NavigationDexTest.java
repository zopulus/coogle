package io.github.zopulus.coogle;

import dalvik.system.PathClassLoader;
import java.lang.reflect.Constructor;
import java.util.List;
import org.luckypray.dexkit.DexKitBridge;

public final class NavigationDexTest {
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        System.loadLibrary("dexkit");
        for (String name : List.of("valid", "renamed", "missing-dispatch", "ambiguous-circle",
                "static-predicate", "duplicate-corner", "static-accessor")) {
            String path = args[0] + "/" + name + ".zip";
            var loader = new PathClassLoader(path, NavigationDexTest.class.getClassLoader());
            try (var bridge = DexKitBridge.create(path)) {
                NavigationBinding binding;
                try { binding = NavigationBinding.find(bridge, loader); }
                catch (ReflectiveOperationException mismatch) { binding = null; }
                check((binding != null) == (name.equals("valid") || name.equals("renamed")), name + ": wrong decision");
                if (binding != null) {
                    check(binding.navigation.eligible(), "Live predicates should initially pass");
                    var nav = loader.loadClass("fixture.Navigation");
                    for (String field : List.of("gestureEnabled", "hiddenEnabled", "circleEnabled")) {
                        nav.getField(field).setBoolean(null, false);
                        check(!binding.navigation.eligible(), "Ignored live setting: " + field);
                        nav.getField(field).setBoolean(null, true);
                    }
                    check(binding.callers.stream().anyMatch(c -> c instanceof Constructor<?>), "Constructor caller omitted");
                    check(binding.serialize().equals(NavigationBinding.parse(loader, binding.serialize()).serialize()), "Round trip changed");
                }
                System.out.println("PASS: " + name);
            }
        }
        for (int i = 1; i < args.length; i++) {
            var loader = new PathClassLoader(args[i], NavigationDexTest.class.getClassLoader());
            String saved;
            try (var targets = new AppDexTargets(loader, args[i], args[0])) {
                NavigationBinding found = targets.resolveNavigation();
                check(found != null, "Actual Launcher navigation missing");
                check(found.callers.stream().anyMatch(c -> c instanceof Constructor<?>), "Actual constructors missing");
                saved = found.serialize();
                System.out.println("PASS: actual Launcher navigation\n" + saved);
            }
            try (var targets = new AppDexTargets(loader, args[i], args[0])) {
                check(saved.equals(targets.resolveNavigation().serialize()), "Cached navigation changed");
            }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
