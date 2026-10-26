package io.github.zopulus.coogle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.wrap.DexMethod;

/** One lazy bridge per package initialization; persistent descriptors avoid repeated scans.
 * API reference: https://luckypray.org/DexKit/en/guide/example.html
 */
final class AppDexTargets implements AutoCloseable {
    private static final int CACHE_SCHEMA = 2;
    private static volatile boolean nativeLoaded;
    private final String apkPath;
    private final ClassLoader loader;
    private final File cache;
    private final Properties stored = new Properties();
    private DexKitBridge bridge;
    private boolean dirty;

    AppDexTargets(ClassLoader loader, String apkPath, String dataDir) throws IOException {
        if (apkPath == null || apkPath.isEmpty()) throw new IOException("Scoped app APK unavailable");
        this.apkPath = apkPath;
        this.loader = loader;
        cache = dataDir == null ? null : new File(dataDir, "coogle_app_targets_v1.cache");
        File apk = new File(apkPath);
        String key = CACHE_SCHEMA + ":" + apkPath + ":" + apk.lastModified() + ":" + apk.length();
        if (cache != null && cache.isFile() && cache.length() < 65536) {
            try (var input = Files.newInputStream(cache.toPath())) { stored.load(input); }
            catch (IOException | IllegalArgumentException invalid) { stored.clear(); }
        }
        if (!key.equals(stored.getProperty("apk"))) stored.clear();
        stored.setProperty("apk", key);
    }

    List<Method> resolve(String feature) throws ReflectiveOperationException {
        if (!FeaturePolicy.LENS.equals(feature) && !FeaturePolicy.CAMERA_LENS.equals(feature)
                && !WalletPreviewCandidates.CACHE_FEATURE.equals(feature)
                && !ActionButtonLensBinding.CACHE_FEATURE.equals(feature))
            throw new IllegalArgumentException("Unsupported dynamic feature");
        // Revised locators must not reuse old descriptors or deterministic misses.
        String cacheKey = switch (feature) {
            case FeaturePolicy.LENS -> feature + ".controllers-v3";
            case FeaturePolicy.CAMERA_LENS -> feature + ".native-v1";
            default -> feature;
        };
        if (!cacheKey.equals(feature) && stored.remove(feature) != null) dirty = true;
        if (FeaturePolicy.LENS.equals(feature) && stored.remove(feature + ".controllers-v2") != null) dirty = true;
        if (WalletPreviewCandidates.CACHE_FEATURE.equals(feature)
                && stored.remove("wallet.preview-candidates-v1") != null) dirty = true;
        String saved = stored.getProperty(cacheKey);
        if (saved != null) {
            if (saved.isEmpty()) return List.of();
            try {
                List<Method> methods = new ArrayList<>();
                for (String descriptor : saved.split("\n"))
                    methods.add(new DexMethod(descriptor).getMethodInstance(loader));
                if (valid(feature, methods)) return methods;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError invalid) {
                // Stale or incompatible cached targets require a fresh scan.
            }
            stored.remove(cacheKey);
            dirty = true;
        }
        if (bridge == null) {
            loadNative();
            bridge = DexKitBridge.create(apkPath);
        }
        List<Method> methods = switch (feature) {
            case FeaturePolicy.LENS -> findLens();
            case FeaturePolicy.CAMERA_LENS -> findCameraLens();
            case WalletPreviewCandidates.CACHE_FEATURE -> findWalletPreviewCandidates();
            case ActionButtonLensBinding.CACHE_FEATURE -> findActionButtonLens();
            default -> throw new IllegalArgumentException("Unsupported dynamic feature");
        };
        if (methods.isEmpty()) {
            // Only deterministic misses are cached; thrown scan/binding errors are retried.
            stored.setProperty(cacheKey, "");
            dirty = true;
            return methods;
        }
        if (!valid(feature, methods)) return List.of();
        List<String> descriptors = new ArrayList<>();
        for (Method method : methods) descriptors.add(new DexMethod(method).serialize());
        stored.setProperty(cacheKey, String.join("\n", descriptors));
        dirty = true;
        return methods;
    }

    private static boolean valid(String feature, List<Method> methods) {
        if (ActionButtonLensBinding.CACHE_FEATURE.equals(feature)) return ActionButtonLensBinding.valid(methods);
        if (WalletPreviewCandidates.CACHE_FEATURE.equals(feature)) return WalletPreviewCandidates.valid(methods);
        if (FeaturePolicy.CAMERA_LENS.equals(feature))
            return CameraLensBinding.valid(methods);
        if (methods.size() < 2 || methods.size() > 514) return false;
        if (FeaturePolicy.LENS.equals(feature)) {
            return LensBinding.isPair(methods.get(0), methods.get(1));
        }
        return false;
    }

    private List<Method> findActionButtonLens() throws ReflectiveOperationException {
        var labels = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass("com.oplus.gesture.actionbuttonkey.ActionButtonSettingsActivity")
                .returnType("java.lang.String").paramCount(0)
                .usingStrings(List.of("getOcrScannerAppLabel: metaKey="))));
        var launches = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("void").paramCount(2)
                .usingStrings(List.of("longPressStartApp currentScene = "))));
        if (labels.size() != 1 || launches.size() != 1) return List.of();
        var dispatch = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(launches.get(0).getClassName()).returnType("void")
                .paramTypes("android.content.Intent", "java.lang.String", "java.lang.String")));
        if (dispatch.size() != 1) return List.of();
        var installed = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("boolean").paramTypes("java.lang.String", "int", "android.content.Context")
                .usingStrings(List.of("isAppInstalled result="))));
        if (installed.size() != 1) return List.of();
        MethodData availability = null;
        for (MethodData caller : installed.get(0).getCallers()) {
            if (!caller.isMethod() || !caller.getClassName().equals(installed.get(0).getClassName())) continue;
            Method method = caller.getMethodInstance(loader);
            if (method.getReturnType() == boolean.class && Modifier.isStatic(method.getModifiers())
                    && List.of(method.getParameterTypes()).equals(List.of(String.class, int.class, android.content.Context.class))) {
                if (availability != null) return List.of();
                availability = caller;
            }
        }
        if (availability == null) return List.of();
        var binders = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(launches.get(0).getClassName()).returnType("void")
                .paramTypes("android.content.Intent", "java.lang.String").usingStrings(List.of("setBinderInfo bindService"))));
        var hints = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("void").paramTypes("int", "java.lang.String", "java.lang.String", "java.lang.Object", "java.lang.String", "boolean")
                .usingStrings(List.of("showSendlingAndSideAnim flag:"))));
        if (binders.size() != 1 || hints.size() != 1) return List.of();
        var factories = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(hints.get(0).getClassName()).returnType(hints.get(0).getClassName()).paramTypes("android.content.Context")));
        if (factories.size() != 1) return List.of();
        List<Method> result = new ArrayList<>(List.of(labels.get(0).getMethodInstance(loader), dispatch.get(0).getMethodInstance(loader),
                availability.getMethodInstance(loader), binders.get(0).getMethodInstance(loader), factories.get(0).getMethodInstance(loader),
                hints.get(0).getMethodInstance(loader)));
        Set<Method> callers = new LinkedHashSet<>();
        for (MethodData target : List.of(labels.get(0), dispatch.get(0), availability, binders.get(0))) {
            for (MethodData caller : target.getCallers()) {
                if (caller.isMethod() && !Modifier.isAbstract(caller.getModifiers()) && !Modifier.isNative(caller.getModifiers()))
                    callers.add(caller.getMethodInstance(loader));
            }
        }
        if (callers.size() > 16) return List.of();
        result.addAll(callers);
        return result;
    }

    private List<Method> findCameraLens() throws ReflectiveOperationException {
        var found = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("boolean").paramTypes("java.lang.String")
                .usingStrings(List.of("key_show_googlelens_icon", "com.oplus.camera.feature.google_lens"))));
        if (found.size() != 1) return List.of();
        var clicks = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("void").paramTypes("java.lang.String", "java.lang.String", "boolean")
                .usingStrings(List.of("googleapp://lens", "com.google.android.googlequicksearchbox"))));
        if (clicks.size() != 1) return List.of();
        MethodData eligibility = null;
        for (MethodData method : clicks.get(0).getDeclaredClass().getMethods()) {
            if (!method.getReturnTypeName().equals("boolean") || method.getParamCount() != 0
                    || !method.getUsingStrings().containsAll(List.of("groupshot", "night", "microscope"))) continue;
            if (eligibility != null) return List.of();
            eligibility = method;
        }
        if (eligibility == null) return List.of();
        MethodData entry = null;
        for (MethodData method : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass("com.oplus.camera.module.BaseMode").returnType("boolean").paramCount(0)))) {
            if (method.getOpCodes().size() != 6 || method.getInvokes().size() != 2) continue;
            for (MethodData invoked : method.getInvokes()) {
                // CameraEntry's inspected normal-entry predicate: one integer == 1.
                // A changed/ambiguous instruction shape retains the original camera.
                if (!invoked.getClassName().startsWith("com.oplus.camera.entry.")
                        || !invoked.getReturnTypeName().equals("boolean") || invoked.getParamCount() != 0
                        || !invoked.getOpNames().equals(List.of("iget", "const/4", "if-ne", "goto", "const/4", "return"))) continue;
                if (entry != null) return List.of();
                entry = method;
            }
        }
        if (entry == null) return List.of();
        MethodData supported = null, children = null, refresh = null;
        for (MethodData method : eligibility.getInvokes()) {
            if (!Modifier.isAbstract(method.getModifiers())) continue;
            if (method.getReturnTypeName().equals("boolean") && method.getParamTypeNames().equals(List.of("android.app.Activity"))) {
                if (children != null) return List.of(); children = method;
            } else if (method.getParamCount() == 0 && method.getReturnTypeName().equals("boolean")
                    && !method.getName().equals(entry.getName())) {
                if (supported != null) return List.of(); supported = method;
            } else if (method.getParamCount() == 0 && method.getReturnTypeName().equals("void")) {
                if (refresh != null) return List.of(); refresh = method;
            }
        }
        var locked = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass("com.oplus.camera.util.Util").returnType("boolean").paramCount(0)
                .invokeMethods(MethodsMatcher.create().add(MethodMatcher.create()
                        .declaredClass("android.app.KeyguardManager").name("isDeviceLocked").paramCount(0)))));
        if (supported == null || children == null || refresh == null || locked.size() != 1) return List.of();
        Class<?> ui = eligibility.getClassInstance(loader).getMethod("getMainUIContainer").getReturnType();
        Method uiState = null;
        for (Method method : ui.getMethods()) {
            if (method.getParameterCount() != 0 || !method.getReturnType().getName().equals(CameraLensBinding.UI_STATE)) continue;
            if (uiState != null && !uiState.equals(method)) return List.of(); uiState = method;
        }
        if (uiState == null) return List.of();
        List<Method> result = new ArrayList<>(List.of(found.get(0).getMethodInstance(loader), eligibility.getMethodInstance(loader),
                supported.getMethodInstance(loader), children.getMethodInstance(loader), entry.getMethodInstance(loader),
                locked.get(0).getMethodInstance(loader), uiState, refresh.getMethodInstance(loader)));
        Set<Method> callers = new LinkedHashSet<>();
        callers.add(result.get(0));
        List<MethodData> nativeCallers = new ArrayList<>(found.get(0).getCallers());
        nativeCallers.addAll(eligibility.getCallers());
        for (MethodData caller : nativeCallers) {
            if (!caller.isMethod() || Modifier.isAbstract(caller.getModifiers()) || Modifier.isNative(caller.getModifiers())) continue;
            callers.add(caller.getMethodInstance(loader));
            if (callers.size() > 64) return List.of();
        }
        result.addAll(callers);
        return result;
    }

    private List<Method> findWalletPreviewCandidates() throws ReflectiveOperationException {
        var constructors = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().name("<init>")
                .usingStrings(List.of("paymentCardInfo", "safePaymentCardInfo", "valuables"))));
        if (constructors.isEmpty() || constructors.size() > 16) return List.of();
        Set<Method> getters = new LinkedHashSet<>();
        for (MethodData constructor : constructors)
            for (Method method : constructor.getClassInstance(loader).getDeclaredMethods())
                if (WalletPreviewCandidates.isModelGetter(method)) getters.add(method);
        if (getters.isEmpty()) return List.of();
        var found = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("java.lang.Object").paramTypes("java.lang.Object")
                .invokeMethods(MethodsMatcher.create().add(MethodMatcher.create()
                        .declaredClass("android.app.KeyguardManager").name("isDeviceLocked").paramCount(0)))));
        if (found.size() > 128) return List.of();
        List<Method> callbacks = new ArrayList<>();
        for (MethodData callback : found) callbacks.add(callback.getMethodInstance(loader));
        return WalletPreviewCandidates.select(getters, callbacks);
    }

    private List<Method> findLens() throws ReflectiveOperationException {
        var anchors = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                // Google changes this diagnostic event number between app builds; the text,
                // coroutine method shape, unique gate, and eligibility graph provide the anchors.
                .returnType("java.lang.Object").paramCount(4)
                .usingStrings(List.of("Checked: %s"))));
        if (anchors.size() != 1) return List.of();
        MethodData gate = null;
        for (MethodData method : anchors.get(0).getDeclaredClass().getMethods()) {
            if (!method.getReturnTypeName().equals("boolean") || !method.getParamTypeNames().equals(List.of("boolean"))) continue;
            if (gate != null) return List.of();
            gate = method;
        }
        if (gate == null) return List.of();
        Map<Method, MethodData> candidates = new LinkedHashMap<>();
        for (MethodData caller : gate.getCallers()) {
            if (caller.getParamCount() == 0 && caller.getReturnTypeName().equals("boolean"))
                candidates.put(caller.getMethodInstance(loader), caller);
        }
        Method[] selected = LensBinding.select(candidates.keySet(), gate.getClassInstance(loader));
        if (selected == null) return List.of();
        List<Method> result = new ArrayList<>(List.of(selected));
        List<MethodData> frontier = new ArrayList<>(List.of(candidates.get(selected[0]), candidates.get(selected[1])));
        Set<String> visited = new LinkedHashSet<>();
        for (MethodData method : frontier) visited.add(method.getDescriptor());
        // Follow the inspected eligibility callers, replacing the old fixed caller-name list.
        for (int depth = 0; depth < 3; depth++) {
            List<MethodData> next = new ArrayList<>();
            for (MethodData method : frontier) {
                for (MethodData caller : method.getCallers()) {
                    if (!caller.isMethod() || Modifier.isAbstract(caller.getModifiers()) || Modifier.isNative(caller.getModifiers())
                            || !visited.add(caller.getDescriptor())) continue;
                    if (visited.size() > 512) return List.of();
                    result.add(caller.getMethodInstance(loader));
                    next.add(caller);
                }
            }
            frontier = next;
        }
        return result;
    }

    private static void loadNative() {
        if (nativeLoaded) return;
        synchronized (AppDexTargets.class) {
            if (!nativeLoaded) { System.loadLibrary("dexkit"); nativeLoaded = true; }
        }
    }

    @Override public void close() {
        try {
            if (bridge != null) bridge.close();
        } finally {
            if (dirty && cache != null) {
                File temporary = null;
                try {
                    temporary = File.createTempFile("coogle_google_targets", ".tmp", cache.getParentFile());
                    try (var output = Files.newOutputStream(temporary.toPath())) { stored.store(output, "Coogle DexKit targets"); }
                    Files.move(temporary.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException | RuntimeException unavailable) {
                    // Unwritable cache must not prevent feature installation.
                } finally {
                    if (temporary != null) temporary.delete();
                }
            }
        }
    }
}
