package io.github.zopulus.coogle;

import java.lang.reflect.Method;
import static io.github.zopulus.coogle.HookInstaller.target;
import static io.github.zopulus.coogle.HookInstaller.booleanMethod;

final class SettingsHooks {
    static void installGoogleSettingsEntry(HookInstaller hooks, ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.GOOGLE_ENTRY)) return;
        try {
            Class<?> controller=target(loader,"com.oplus.settings.feature.homepage.controller.GooglePreferenceController");
            for (Method method:controller.getDeclaredMethods()) {
                if (method.getName().equals("getAvailabilityStatus") && method.getReturnType()==int.class)
                    hooks.install(method,chain -> FeatureConfig.enabled(FeaturePolicy.GOOGLE_ENTRY) ? 0 : chain.proceed());
            }
        } catch (Throwable failure) { hooks.error("Google Settings entry hook unavailable",failure); }
    }

    static void installSettingsHiddenSupport(HookInstaller hooks, ClassLoader loader) {
        try {
            Class<?> feature = target(loader, "com.oplus.settings.utils.CustomizeFeatureUtils");
            Method hidden = booleanMethod(feature, "isSupportCircleToSearchNavbarHidden");
            Class<?> fragment = target(loader, "com.oplus.settings.feature.navbar.NavigationBarSettingsFragment");
            hooks.install(hidden, chain -> FeatureConfig.enabled(FeaturePolicy.NAVIGATION) ? true : chain.proceed());
            for (Method method : fragment.getDeclaredMethods()) {
                String name = method.getName();
                if (name.startsWith("needShow") || name.startsWith("lambda$")) hooks.deoptimize(method);
            }
            hooks.info("SETTINGS hidden circle supported; mutual-exclusion reset path disabled");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            hooks.error("Settings hidden support unavailable", error);
        }
    }
}
