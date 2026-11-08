package io.github.zopulus.coogle;
final class CompanionSettings {
    static final CompanionSettings INSTANCE = new CompanionSettings();
    static int checks;
    static boolean fail = true;
    static boolean shouldCheck(android.content.SharedPreferences settings) { return true; }
    String refresh(android.content.SharedPreferences settings) throws java.io.IOException {
        checks++;
        if (fail) throw new java.io.IOException("模拟暂时检测失败");
        return "READY";
    }
}
