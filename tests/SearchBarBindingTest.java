package io.github.zopulus.coogle;

import android.content.Context;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import org.luckypray.dexkit.wrap.DexField;
import org.luckypray.dexkit.wrap.DexMethod;

/** Renamed feature fields must retain the inspected signatures and cache identity. */
public final class SearchBarBindingTest {
    public static final class Renamed {
        public static boolean export, breeno, search;
        public static final boolean immutable = false;
        public static int wrong;
        public static void init(Context context) {}
        public static void breeno(Context context) {}
        public static void search(Context context) {}
        public static boolean query() { return false; }
        public static boolean caller(Context context) { return query(); }
        public boolean instance() { return false; }
    }
    static String fixture() throws Exception {
        return String.join("\n", new DexMethod(method("init", Context.class)).serialize(),
                new DexMethod(method("query")).serialize(),
                new DexMethod(method("breeno", Context.class)).serialize(),
                new DexMethod(method("search", Context.class)).serialize(),
                field("export"), field("breeno"), field("search"),
                new DexMethod(method("caller", Context.class)).serialize());
    }
    public static void main(String[] args) throws Exception {
        ClassLoader loader = SearchBarBindingTest.class.getClassLoader();
        String saved = fixture();
        SearchBarBinding binding = SearchBarBinding.parse(loader, saved);
        check(binding != null && saved.equals(binding.serialize()), "Renamed features did not round-trip");
        for (String invalid : List.of(saved.replace(field("export"), field("immutable")),
                saved.replace(field("search"), field("wrong")),
                saved.replace(field("breeno"), field("export")),
                saved.replace(new DexMethod(method("query")).serialize(), new DexMethod(method("instance")).serialize()),
                saved.substring(0, saved.lastIndexOf('\n')))) {
            check(SearchBarBinding.parse(loader, invalid) == null, "Incompatible cached features accepted");
        }
        var directory = Files.createTempDirectory("coogle-search-cache");
        var apk = directory.resolve("launcher.apk");
        var cache = directory.resolve("coogle_app_targets_v1.cache");
        try {
            Files.write(apk, new byte[]{1});
            Properties stored = new Properties();
            stored.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":1");
            stored.setProperty("search_bar.features-v1", saved);
            try (var output = Files.newOutputStream(cache)) { stored.store(output, "fixture"); }
            try (var targets = new AppDexTargets(loader, apk.toString(), directory.toString())) {
                check(saved.equals(targets.resolveSearchBar().serialize()), "Valid cache started native scanning");
            }
            Files.write(apk, new byte[]{1, 2});
            try (var targets = new AppDexTargets(loader, apk.toString(), directory.toString())) {
                try { targets.resolveSearchBar(); throw new AssertionError("APK update reused stale fields"); }
                catch (UnsatisfiedLinkError expected) { }
            }
        } finally {
            Files.deleteIfExists(cache); Files.deleteIfExists(apk); Files.deleteIfExists(directory);
        }
        System.out.println("PASS: launcher signatures, distinct mutable fields, cache reuse and APK invalidation");
    }
    private static Method method(String name, Class<?>... params) throws Exception {
        return Renamed.class.getDeclaredMethod(name, params);
    }
    private static String field(String name) throws Exception { return new DexField(Renamed.class.getDeclaredField(name)).serialize(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
