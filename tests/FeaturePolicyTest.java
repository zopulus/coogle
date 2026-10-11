package io.github.zopulus.coogle;
public final class FeaturePolicyTest {
    public static void main(String[] args) {
        if (FeaturePolicy.effective(FeaturePolicy.HEY_GOOGLE,true,false)) throw new AssertionError("Missing companion accepted");
        if (!FeaturePolicy.effective(FeaturePolicy.HEY_GOOGLE,true,true)) throw new AssertionError("Ready companion rejected");
        if (FeaturePolicy.effective(FeaturePolicy.HEY_GOOGLE,false,true)) throw new AssertionError("Disabled feature active");
        if (FeaturePolicy.defaultValue(FeaturePolicy.GMS) || FeaturePolicy.defaultValue(FeaturePolicy.SEARCH_BAR)) throw new AssertionError("New feature implicitly enabled");
        if (!FeaturePolicy.defaultValue(FeaturePolicy.LENS)) throw new AssertionError("Existing Lens default lost");
        for (String key : new String[]{FeaturePolicy.WALLET, FeaturePolicy.QUICK_ENTRIES}) {
            if (FeaturePolicy.defaultValue(key)) throw new AssertionError("New integration must be opt-in");
            if (!FeaturePolicy.effective(key, true, false)) throw new AssertionError("Unrelated companion requirement");
            if (FeaturePolicy.effective(key, false, true)) throw new AssertionError("Disabled integration active");
        }
        if (FeaturePolicy.defaultValue(FeaturePolicy.NATIVE_POWER_ASSISTANT)) throw new AssertionError("Native power-key override must be opt-in");
        if (!FeaturePolicy.effective(FeaturePolicy.NATIVE_POWER_ASSISTANT, true, false)) throw new AssertionError("Unrelated companion requirement");
        if (FeaturePolicy.effective(FeaturePolicy.NATIVE_POWER_ASSISTANT, false, true)) throw new AssertionError("Disabled integration active");
        System.out.println("PASS: feature defaults and companion requirement");
    }
}
