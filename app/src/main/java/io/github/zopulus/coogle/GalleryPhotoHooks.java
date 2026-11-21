package io.github.zopulus.coogle;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Enables the gallery's native Lens action in the photo recommendations. */
final class GalleryPhotoHooks {
    private static final String CLASSES = "com.oplus.aiunit.vision.";
    private static final String PHOTO_PAGE = "com.oplus.gallery.photo_page.";
    private static final String LENS_FLAG = "feature_is_support_google_lens";
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";

    static void install(HookInstaller hooks, ClassLoader loader) {
        if (!FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS)) return;
        int mark = hooks.mark();
        try {
            Class<?> features = HookInstaller.target(loader, CLASSES + "exb");
            Method query = features.getDeclaredMethod("c", int.class, String.class, boolean.class);
            if (query.getReturnType() != boolean.class) throw new NoSuchMethodException(query.toString());

            Class<?> inputs = HookInstaller.target(loader, PHOTO_PAGE + "viewmodel.inputarguments.PhotoInputArgumentsViewModel");
            Class<?> fragmentActivity = HookInstaller.target(loader, "androidx.fragment.app.FragmentActivity");
            Method readInputs = inputs.getDeclaredMethod("m", fragmentActivity, Intent.class, Bundle.class);
            Class<?> wrapper = HookInstaller.target(loader, CLASSES + "spp");
            Class<?> lifecycle = HookInstaller.target(loader, "androidx.lifecycle.Lifecycle");
            Class<?> callback = HookInstaller.target(loader, CLASSES + "me00$a");
            hooks.deoptimize(wrapper.getDeclaredMethod("Q", lifecycle, callback));
            ThreadLocal<Boolean> readingPhotoInputs = new ThreadLocal<>();
            AtomicBoolean lensAppAvailable = new AtomicBoolean();

            var buildFactories = HookInstaller.target(loader, CLASSES + "t9z").getDeclaredMethod("invoke");
            var skipGoogleFactories = HookInstaller.target(loader, CLASSES + "g9z").getDeclaredMethod("invoke");
            Class<?> passScan = HookInstaller.target(loader, PHOTO_PAGE
                    + "viewmodel.funcRecommend.factory.GooglePassScanFuncFactory");
            ThreadLocal<boolean[]> buildingFactories = new ThreadLocal<>();

            Class<?> observer = HookInstaller.target(loader, PHOTO_PAGE
                    + "ui.section.intelliRecommend.PhotoIntelliFuncSection$subscribeLiveDataFromViewModel$1");
            Class<?> result = HookInstaller.target(loader, CLASSES + "j3k");
            var render = observer.getDeclaredMethod("invoke", result);
            Field status = result.getField("a");
            Field imageKey = result.getField("b");
            Field items = result.getField("c");
            var copy = result.getConstructor(status.getType(), String.class, List.class);
            Class<?> translate = HookInstaller.target(loader, PHOTO_PAGE + "viewmodel.funcRecommend.FuncItem$o");

            hooks.install(query, chain -> {
                if (FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS)) {
                    Object key = chain.getArg(1);
                    if (LENS_FLAG.equals(key)) return true;
                    if ("is_region_cn".equals(key) && Boolean.TRUE.equals(readingPhotoInputs.get())) return false;
                }
                return chain.proceed();
            });
            // This method reads is_region_cn to set the photo page's Lens eligibility.
            hooks.deoptimize(readInputs);
            hooks.install(readInputs, chain -> {
                if (!FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS) || Boolean.TRUE.equals(readingPhotoInputs.get())
                        || !(chain.getArg(0) instanceof Context context)) return chain.proceed();
                boolean available = hasLens(context);
                lensAppAvailable.set(available);
                if (!available) return chain.proceed();
                readingPhotoInputs.set(true);
                try {
                    return chain.proceed();
                } finally {
                    readingPhotoInputs.remove();
                }
            });
            hooks.install(skipGoogleFactories, chain -> {
                Object original = chain.proceed();
                boolean[] added = buildingFactories.get();
                if (added == null || !Boolean.TRUE.equals(original)) return original;
                added[0] = true;
                return false;
            });
            hooks.install(buildFactories, chain -> {
                if (!FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS) || buildingFactories.get() != null) return chain.proceed();
                boolean[] added = {false};
                buildingFactories.set(added);
                try {
                    Object original = chain.proceed();
                    // The native branch builds both Lens and pass scanning. Keep only the added Lens factory.
                    if (added[0] && original instanceof List<?> factories) factories.removeIf(passScan::isInstance);
                    return original;
                } finally {
                    buildingFactories.remove();
                }
            });
            hooks.install(render, chain -> {
                Object original = chain.getArg(0);
                if (!FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS) || !lensAppAvailable.get() || original == null
                        || !(items.get(original) instanceof List<?> actions)) return chain.proceed();

                // Translate can be returned in a photo's first batch, before the native Lens action.
                List<?> updated = actions.stream().filter(action -> !translate.isInstance(action)).toList();
                if (updated.size() == actions.size()) return chain.proceed();
                return chain.proceed(new Object[] {copy.newInstance(status.get(original), imageKey.get(original), updated)});
            });
            hooks.info("Gallery photo Lens recommendations installed");
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("Gallery photo Lens recommendations unavailable", failure);
        }
    }

    private static boolean hasLens(Context context) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("google://lens")).setPackage(GOOGLE);
        return view.resolveActivity(context.getPackageManager()) != null;
    }

    private GalleryPhotoHooks() {}
}
