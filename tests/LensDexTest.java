package io.github.zopulus.coogle;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;
import java.lang.reflect.Method;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

/** Runs the production scanner against synthetic DEX files on Android. */
public final class LensDexTest {
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable failure) { failure.printStackTrace(System.err); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        String directory = args[0];
        System.out.println("Lens DEX scanner test started");
        for (String name : new String[]{"old-event", "new-event", "new-layout", "latest-layout", "ambiguous", "ambiguous-talk",
                "wrong-shape", "wrong-talk-shape", "latest-wrong-talk-shape", "unknown-talk-layout", "missing-talk"}) {
            String path = directory + "/" + name + ".zip";
            ClassLoader loader = new DexClassLoader(path, directory, directory, LensDexTest.class.getClassLoader());
            try (AppDexTargets targets = new AppDexTargets(loader, path, null)) {
                System.out.println("Scanning: " + name);
                List<Method> methods = targets.resolve(FeaturePolicy.LENS);
                if (name.endsWith("event") || name.equals("new-layout") || name.equals("latest-layout")) {
                    check(methods.size() >= 3, name + ": eligibility pair/callers missing");
                    check(methods.get(0).getDeclaringClass().getName().equals("fixture.RenamedCreate"), "Wrong create target");
                    check(methods.get(1).getDeclaringClass().getName().equals("fixture.RenamedTalk"), "Wrong talk target");
                    check(methods.stream().skip(2).anyMatch(m -> m.getDeclaringClass().getName().equals("fixture.Caller")), "Caller graph missing");
                } else check(methods.isEmpty(), name + ": incompatible/ambiguous DEX accepted");
                System.out.println("PASS: " + name);
            }
        }
        for (int i = 1; i < args.length; i++) verifyApk(args[i], directory);
    }
    private static void verifyApk(String path, String directory) throws Exception {
        File apk = new File(path), cache = new File(directory, "coogle_app_targets_v1.cache");
        Properties stale = new Properties();
        stale.setProperty("apk", "2:" + path + ":" + apk.lastModified() + ":" + apk.length());
        stale.setProperty(FeaturePolicy.LENS, "");
        stale.setProperty(FeaturePolicy.LENS + ".controllers-v2", "");
        try (var output = Files.newOutputStream(cache.toPath())) { stale.store(output, "pre-fix layout miss"); }
        ClassLoader loader = new PathClassLoader(path + ":/system/framework/org.apache.http.legacy.jar", LensDexTest.class.getClassLoader());
        List<Method> found;
        try (var targets = new AppDexTargets(loader, path, directory)) {
            found = targets.resolve(FeaturePolicy.LENS);
            check(found.size() >= 2 && LensBinding.isPair(found.get(0), found.get(1)), path + ": actual eligibility pair missing");
        }
        try (var targets = new AppDexTargets(loader, path, directory)) {
            check(found.equals(targets.resolve(FeaturePolicy.LENS)), path + ": cached targets changed");
        }
        Properties updated = new Properties();
        try (var input = Files.newInputStream(cache.toPath())) { updated.load(input); }
        check(!updated.containsKey(FeaturePolicy.LENS), "Obsolete Lens miss retained");
        check(!updated.containsKey(FeaturePolicy.LENS + ".controllers-v2"), "Obsolete 17.68 Lens miss retained");
        System.out.println("PASS: " + apk.getName() + ": " + found.get(0) + ", " + found.get(1)
                + "; targets=" + found.size() + "; old miss replaced and cached binding validated");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
