package io.github.zopulus.coogle;

/** Defaults for user-controlled settings; scope-controlled integrations have no switch. */
public final class FeaturePolicy {
    public static final String NAVIGATION="navigation", GMS="gms",
            GOOGLE_ENTRY="google_entry", SEARCH_BAR="search_bar", HEY_GOOGLE="hey_google", LENS="lens",
            WALLET="wallet", QUICK_ENTRIES="quick_entries", ACTION_BUTTON_LENS="action_button_lens",
            GALLERY_LENS="gallery_lens";
    // Locator/cache identifier for the camera integration.
    public static final String CAMERA_LENS="camera_lens";
    public static boolean defaultValue(String key) {
        return NAVIGATION.equals(key) || LENS.equals(key) || GALLERY_LENS.equals(key);
    }
    public static boolean effective(String key, boolean requested, boolean companionReady) {
        return requested && (!HEY_GOOGLE.equals(key) || companionReady);
    }
    private FeaturePolicy() {}
}
