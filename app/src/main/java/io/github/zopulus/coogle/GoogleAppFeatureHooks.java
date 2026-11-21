package io.github.zopulus.coogle;

import java.lang.reflect.Method;

/** Supplies the native feature declarations used by the Google App and its Lens callers. */
final class GoogleAppFeatureHooks {
    static final String GALLERY = "com.coloros.gallery3d";
    private static final String CAMERA_INTEGRATION = "com.google.lens.feature.CAMERA_INTEGRATION";
    private static final String IMAGE_INTEGRATION = "com.google.lens.feature.IMAGE_INTEGRATION";
    private static final String GEMINI_POWER_MENU = "com.google.android.feature.GEMINI_POWER_MENU";
    private static final String CAMERA_PROPERTY = "ro.com.google.lens.oem_camera_package";
    private static final String IMAGE_PROPERTY = "ro.com.google.lens.oem_image_package";
    private static final String CAMERA = "com.oplus.camera";

    static void install(HookInstaller hooks, ClassLoader loader) {
        if (!cameraIntegrationEnabled() && !FeatureConfig.enabled(FeaturePolicy.NAVIGATION)) return;
        int mark = hooks.mark();
        try {
            Class<?> pm = HookInstaller.target(loader, "android.app.ApplicationPackageManager");
            boolean installed = false;
            for (Method method : pm.getDeclaredMethods()) {
                if (!method.getName().equals("hasSystemFeature") || method.getReturnType() != boolean.class
                        || method.getParameterCount() < 1 || method.getParameterCount() > 2
                        || method.getParameterTypes()[0] != String.class) continue;
                boolean versioned = method.getParameterCount() == 2;
                if (versioned && method.getParameterTypes()[1] != int.class) continue;
                hooks.install(method, chain -> {
                    String name = (String) chain.getArg(0);
                    boolean enabled = name != null && switch (name) {
                        case CAMERA_INTEGRATION -> cameraIntegrationEnabled();
                        case IMAGE_INTEGRATION -> FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS);
                        case GEMINI_POWER_MENU -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION);
                        default -> false;
                    };
                    if (enabled && (!versioned || (int) chain.getArg(1) <= 0)) return true;
                    return chain.proceed();
                });
                installed = true;
            }
            if (!installed) throw new NoSuchMethodException("Google feature query missing");
            Method property = HookInstaller.target(loader, "android.os.SystemProperties").getDeclaredMethod("get", String.class, String.class);
            hooks.install(property, chain -> {
                Object original = chain.proceed();
                Object name = chain.getArg(0);
                if (IMAGE_PROPERTY.equals(name)) return galleryPackages((String) original);
                if (!CAMERA_PROPERTY.equals(name)) return original;
                boolean cameraEnabled = FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS);
                boolean shortcutEnabled = FeatureConfig.enabled(FeaturePolicy.ACTION_BUTTON_LENS);
                if (!cameraEnabled && !shortcutEnabled) return original;
                String value = (String) original;
                if (cameraEnabled) value = append(value, CAMERA);
                if (shortcutEnabled) value = append(value, ActionButtonLensBinding.PACKAGE);
                return value;
            });
            hooks.info("Google App feature declarations installed");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("Google App feature declarations unavailable", failure);
        }
    }

    static void installGallery(HookInstaller hooks, ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS)) return;
        int mark = hooks.mark();
        try {
            Class<?> properties = HookInstaller.target(loader, "android.os.SystemProperties");
            for (Method getter : new Method[] {
                    properties.getDeclaredMethod("get", String.class),
                    properties.getDeclaredMethod("get", String.class, String.class) }) {
                hooks.install(getter, chain -> {
                    Object original = chain.proceed();
                    return IMAGE_PROPERTY.equals(chain.getArg(0)) ? galleryPackages((String) original) : original;
                });
            }
            hooks.info("Gallery Lens native image integration declared");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("Gallery Lens native image integration unavailable", failure);
        }
    }

    private static String galleryPackages(String original) {
        return FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS) ? append(original, GALLERY) : original;
    }

    private static boolean cameraIntegrationEnabled() {
        return FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS) || FeatureConfig.enabled(FeaturePolicy.ACTION_BUTTON_LENS);
    }
    private static String append(String value, String caller) {
        if (value == null || value.isEmpty()) return caller;
        for (String entry : value.split(",")) if (caller.equals(entry.trim())) return value;
        return value + "," + caller;
    }
    private GoogleAppFeatureHooks() {}
}
