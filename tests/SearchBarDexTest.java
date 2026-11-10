package io.github.zopulus.coogle;

import dalvik.system.PathClassLoader;
import java.util.List;
import org.luckypray.dexkit.DexKitBridge;

/** Checks the production locator against renamed, ambiguous and actual Launcher DEX. */
public final class SearchBarDexTest {
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        System.loadLibrary("dexkit");
        for (String name : List.of("valid", "duplicate-init", "ambiguous-export", "missing-query", "wrong-field")) {
            String path = args[0] + "/" + name + ".zip";
            var loader = new PathClassLoader(path, SearchBarDexTest.class.getClassLoader());
            try (var bridge = DexKitBridge.create(path)) {
                SearchBarBinding found = SearchBarBinding.find(bridge, loader);
                check((found != null) == name.equals("valid"), name + ": wrong binding decision");
                if (found != null) {
                    check(found.query.getName().equals("query"), "Wrong QSB getter");
                    check(found.exportFlag.getName().equals("export"), "Wrong export flag");
                    check(found.breenoFlag.getName().equals("breeno"), "Wrong Breeno flag");
                    check(found.searchFlag.getName().equals("search"), "Wrong search flag");
                    check(found.callers.size() == 1, "Missing QSB caller");
                }
                System.out.println("PASS: " + name);
            }
        }
        for (int i = 1; i < args.length; i++) {
            String path = args[i];
            var loader = new PathClassLoader(path, SearchBarDexTest.class.getClassLoader());
            String saved;
            try (var targets = new AppDexTargets(loader, path, args[0])) {
                SearchBarBinding found = targets.resolveSearchBar();
                check(found != null, "Actual Launcher features missing");
                saved = found.serialize();
                System.out.println("PASS: actual Launcher\n" + saved);
            }
            try (var targets = new AppDexTargets(loader, path, args[0])) {
                check(saved.equals(targets.resolveSearchBar().serialize()), "Cached Launcher targets changed");
            }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
