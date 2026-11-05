package io.github.zopulus.coogle;

import io.github.zopulus.coogle.migration.AssistantRuntime;
import io.github.zopulus.coogle.migration.AssistantVoiceHooks;
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime;
import java.util.List;

/** Retry real installation after hook failures or initially unavailable system classes. */
public final class AssistantVoiceInstallTest {
    private static final String SERVICE = "com.android.server.voiceinteraction.VoiceInteractionManagerService";
    private static final List<String> TARGETS = List.of("onRoleHoldersChanged", "onUserUnlocking", "systemRunning");

    public static void main(String[] args) {
        verifyRetry(0, null);
        for (int failure = 1; failure <= 3; failure++) verifyRetry(failure, null);
        for (String hidden : List.of(SERVICE, SERVICE + "$VoiceInteractionManagerServiceStub",
                SERVICE + "$VoiceInteractionManagerServiceStub$RoleObserver")) verifyRetry(0, hidden);
        System.out.println("PASS: independent voice hooks, installation failures, late classes and duplicate-free retries");
    }

    private static void verifyRetry(int failAt, String hidden) {
        var module = ModernHookRuntime.INSTANCE.module;
        List.copyOf(module.active).forEach(handle -> handle.unhook());
        module.callbacks.clear();
        module.attempts = 0;
        module.failAt = failAt;
        AssistantRuntime.INSTANCE.getInstalledHooks().clear();
        ClassLoader parent = AssistantVoiceInstallTest.class.getClassLoader();
        ClassLoader first = new ClassLoader(parent) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(hidden)) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        };
        AssistantVoiceHooks.INSTANCE.hookVoiceInteractionServiceRepair("android", first);
        int expectedFirst = failAt == 0 && hidden == null ? 3 : 2;
        check(module.active.size() == expectedFirst, "One missing group blocked another: " + hidden);
        var successful = List.copyOf(module.active);
        module.failAt = -1;
        AssistantVoiceHooks.INSTANCE.hookVoiceInteractionServiceRepair("android", parent);
        check(module.active.stream().map(handle -> handle.getExecutable().getName()).sorted().toList().equals(TARGETS),
                "Missing or duplicate hook after retry: failure=" + failAt + ", hidden=" + hidden);
        check(module.active.containsAll(successful), "Retry replaced already successful hooks");
        int installedAttempts = module.attempts;
        AssistantVoiceHooks.INSTANCE.hookVoiceInteractionServiceRepair("android", parent);
        check(module.attempts == installedAttempts && module.active.size() == 3, "Repeated entry reinstalled hooks");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
