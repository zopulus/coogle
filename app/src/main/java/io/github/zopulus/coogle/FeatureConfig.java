package io.github.zopulus.coogle;

import android.content.SharedPreferences;

public final class FeatureConfig {
    public static final String GROUP="coogle";
    private static volatile SharedPreferences preferences;
    public static void attach(SharedPreferences value) { preferences=value; }
    public static boolean enabled(String key) {
        SharedPreferences p=preferences;
        boolean requested=requested(p, key);
        return FeaturePolicy.effective(key,requested,requested && FeaturePolicy.HEY_GOOGLE.equals(key)
                && p!=null && p.getBoolean("companion_ready",false));
    }

    public static boolean requested(SharedPreferences settings, String key) {
        if (settings == null) return FeaturePolicy.defaultValue(key);
        if (FeaturePolicy.GALLERY_LENS.equals(key) && !settings.contains(key)) key = FeaturePolicy.LENS;
        return settings.getBoolean(key, FeaturePolicy.defaultValue(key));
    }

    public static boolean save(SharedPreferences settings, String key, boolean enabled) {
        SharedPreferences.Editor editor = settings.edit();
        if (FeaturePolicy.LENS.equals(key) && !settings.contains(FeaturePolicy.GALLERY_LENS)) {
            // Preserve the legacy value for Gallery and Camera when Talk and Create changes first.
            editor.putBoolean(FeaturePolicy.GALLERY_LENS, requested(settings, FeaturePolicy.LENS));
        }
        return editor.putBoolean(key, enabled).commit();
    }

    private FeatureConfig() {}
}
