package io.github.zopulus.coogle;

import android.content.Context;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import static io.github.zopulus.coogle.HookInstaller.target;
import static io.github.zopulus.coogle.HookInstaller.booleanMethod;

final class SearchBarHooks {
    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR)) return;
        int firstHandle = hooks.mark();
        try {
            Class<?> feature;
            try { feature=target(loader,"com.android.common.config.FeatureOption"); }
            catch (ClassNotFoundException renamed) {
                try (var targets = new AppDexTargets(loader, apkPath, dataDir)) {
                    SearchBarBinding binding = targets.resolveSearchBar();
                    if (binding == null) {
                        hooks.info("SEARCH_BAR skip: launcher features not uniquely found");
                        return;
                    }
                    installRenamed(hooks, binding);
                }
                return;
            }
            Method query=booleanMethod(feature,"getHasQsbAtBottomFeature");
            Method breeno=booleanMethod(feature,"getSIsSupportBreenoEntry");
            Method search=booleanMethod(feature,"getSIsSupportSearchEntry");
            Method init=feature.getDeclaredMethod("initFeature",Context.class);
            var export=feature.getDeclaredField("isExp"); export.setAccessible(true);
            if (export.getType()!=boolean.class || !Modifier.isStatic(export.getModifiers())) {
                throw new NoSuchFieldException("Launcher export feature signature changed");
            }
            hooks.install(query,chain -> FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR) ? true : chain.proceed());
            // QSB needs the export bottom-search implementation, but the independent
            // page-indicator button must retain its native Breeno action and settings.
            hooks.install(breeno,chain ->
                    FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR) ? true : chain.proceed());
            hooks.install(search,chain ->
                    FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR) ? false : chain.proceed());
            hooks.install(init,chain -> {
                Object result=chain.proceed();
                if (FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR)) export.setBoolean(null,true);
                return result;
            });
        } catch (Throwable failure) {
            hooks.unhookSince(firstHandle);
            hooks.error("Google search bar unavailable; partial hooks removed",failure);
        }
    }

    private static void installRenamed(HookInstaller hooks, SearchBarBinding binding) {
        for (Method caller : binding.callers) hooks.deoptimize(caller);
        hooks.install(binding.query, chain -> FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR) ? true : chain.proceed());
        // These getters are now inlined field reads. Keep the native initializers and
        // adjust only their inspected output flags, including later package refreshes.
        hooks.install(binding.breeno, chain -> {
            Object result = chain.proceed();
            if (FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR)) binding.breenoFlag.setBoolean(null, true);
            return result;
        });
        hooks.install(binding.search, chain -> {
            Object result = chain.proceed();
            if (FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR)) binding.searchFlag.setBoolean(null, false);
            return result;
        });
        hooks.install(binding.init, chain -> {
            Object result = chain.proceed();
            if (FeatureConfig.enabled(FeaturePolicy.SEARCH_BAR)) {
                binding.exportFlag.setBoolean(null, true);
                binding.breenoFlag.setBoolean(null, true);
                binding.searchFlag.setBoolean(null, false);
            }
            return result;
        });
        hooks.info("SEARCH_BAR installed: " + binding.init.getDeclaringClass().getName()
                + "; QSB callers=" + binding.callers.size());
    }
}
