package io.github.zopulus.coogle;

/** Defaults retain verified features; newly added integrations are opt-in. */
public final class FeaturePolicy {
    public static final String NAVIGATION="navigation", GMS="gms",
            GOOGLE_ENTRY="google_entry", SEARCH_BAR="search_bar", HEY_GOOGLE="hey_google", LENS="lens",
            WALLET="wallet", QUICK_ENTRIES="quick_entries", NATIVE_POWER_ASSISTANT="native_power_assistant";
    public static boolean defaultValue(String key) {
        return NAVIGATION.equals(key) || LENS.equals(key);
    }
    public static boolean effective(String key, boolean requested, boolean companionReady) {
        return requested && (!HEY_GOOGLE.equals(key) || companionReady);
    }
    private FeaturePolicy() {}
}
