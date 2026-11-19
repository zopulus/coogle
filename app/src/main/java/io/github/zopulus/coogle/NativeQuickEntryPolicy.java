package io.github.zopulus.coogle;

import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public final class NativeQuickEntryPolicy {
    private static final List<String> INTERNATIONAL_ORDER =
            List.of("assistant", "wallet", "home", "flashlight", "pictorial", "snapchat", "qr_code_scanner");
    private NativeQuickEntryPolicy() {}

    public static String[] completeLockscreenEntries(String[] existing) {
        return completeLockscreenEntries(existing, false);
    }

    public static String[] completeLockscreenEntries(String[] existing, boolean nativeSnapchatAvailable) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(Arrays.asList(existing));
        boolean hasNone = keys.remove("none");
        keys.add("assistant");
        keys.add("wallet");
        keys.add("qr_code_scanner");
        keys.add("home");
        if (nativeSnapchatAvailable) keys.add("snapchat");
        List<String> ordered = new ArrayList<>(keys.size() + (hasNone ? 1 : 0));
        for (String key : INTERNATIONAL_ORDER) if (keys.remove(key)) ordered.add(key);
        ordered.addAll(keys);
        if (hasNone) ordered.add("none");
        return ordered.toArray(new String[0]);
    }
}
