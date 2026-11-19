package io.github.zopulus.coogle;

import java.util.Arrays;

public final class NativeQuickEntryPolicyTest {
    public static void main(String[] args) {
        String[] cn = {"pictorial", "flashlight", "none"};
        String[] expected = {"assistant", "wallet", "home", "flashlight", "pictorial", "qr_code_scanner", "none"};
        if (!Arrays.equals(expected, NativeQuickEntryPolicy.completeLockscreenEntries(cn))) throw new AssertionError("CN entries");
        if (!Arrays.equals(expected, NativeQuickEntryPolicy.completeLockscreenEntries(expected))) throw new AssertionError("Idempotence");
        if (!Arrays.equals(cn, new String[]{"pictorial", "flashlight", "none"})) throw new AssertionError("Input mutated");
        String[] custom = NativeQuickEntryPolicy.completeLockscreenEntries(new String[]{"camera", "wallet", "domestic_only"});
        if (!Arrays.equals(custom, new String[]{"assistant", "wallet", "home", "qr_code_scanner", "camera", "domestic_only"})) throw new AssertionError("Preserve original entries and their relative order");
        String[] intl = {"assistant", "wallet", "home", "flashlight", "pictorial", "snapchat", "qr_code_scanner", "none"};
        if (!Arrays.equals(intl, NativeQuickEntryPolicy.completeLockscreenEntries(intl))) throw new AssertionError("International order changed");
        if (!Arrays.equals(intl, NativeQuickEntryPolicy.completeLockscreenEntries(cn, true))) throw new AssertionError("Supported native Snapchat entry omitted");
        System.out.println("NativeQuickEntryPolicyTest passed");
    }
}
