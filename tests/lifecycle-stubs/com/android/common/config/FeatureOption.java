package com.android.common.config;

public final class FeatureOption {
    public static boolean isExp;
    public static boolean getHasQsbAtBottomFeature() { return false; }
    public static boolean getSIsSupportBreenoEntry() { return false; }
    public static boolean getSIsSupportSearchEntry() { return true; }
    public static void initFeature(android.content.Context context) { isExp = false; }
}
