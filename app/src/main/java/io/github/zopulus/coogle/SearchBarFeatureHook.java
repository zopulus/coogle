package io.github.zopulus.coogle;

import android.content.pm.FeatureInfo;
import android.util.ArrayMap;

/** Supply the same unversioned QSB declaration that the international firmware loads from XML. */
final class SearchBarFeatureHook {
    private static final String QSB = "com.google.android.feature.qsb_at_bottom";

    static void install(HookInstaller hooks, ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR)) return;
        try {
            var config = HookInstaller.target(loader, "com.android.server.SystemConfig");
            var getFeatures = config.getDeclaredMethod("getAvailableFeatures");
            if (getFeatures.getReturnType() != ArrayMap.class) {
                throw new NoSuchMethodException("SystemConfig feature map signature changed");
            }
            hooks.install(getFeatures, chain -> {
                Object original = chain.proceed();
                if (!(original instanceof ArrayMap<?, ?> features) || features.containsKey(QSB)) return original;
                @SuppressWarnings("unchecked")
                var result = new ArrayMap<>((ArrayMap<String, FeatureInfo>) features);
                FeatureInfo qsb = new FeatureInfo();
                qsb.name = QSB;
                result.put(QSB, qsb);
                return result;
            });
            hooks.info("SEARCH_BAR system feature installed");
        } catch (Throwable failure) {
            hooks.error("Google search bar system feature unavailable", failure);
        }
    }

    private SearchBarFeatureHook() {}
}
