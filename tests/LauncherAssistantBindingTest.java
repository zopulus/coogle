package io.github.zopulus.coogle;

import io.github.zopulus.coogle.migration.LauncherAssistantBinding;
import java.lang.reflect.Method;
import java.util.List;

/** Exercise the actual discovery used by the launcher availability hook. */
public final class LauncherAssistantBindingTest {
    public static final class DirectService {
        public void onAssistantAvailable(boolean available, boolean longPress) {}
    }
    public static final class LegacyService {
        public final class Proxy {
            public void onAssistantAvailable(boolean available, boolean longPress) {}
        }
    }
    public static final class ExternalService {
        private RenamedProxy h0;
        private RenamedProxy anotherReference;
    }
    public static final class RenamedProxy {
        public void onAssistantAvailable(boolean available, boolean longPress) {}
    }
    public static final class StaticFieldService {
        private static RenamedProxy unrelated;
    }
    public static final class InvalidService {
        private WrongResult result;
        private WrongParameters parameters;
        private StaticCallback staticCallback;
        private AbstractCallback abstractCallback;
        private NativeCallback nativeCallback;
    }
    public static final class WrongResult {
        public boolean onAssistantAvailable(boolean available, boolean longPress) { return available; }
    }
    public static final class WrongParameters {
        public void onAssistantAvailable(boolean available) {}
        public void onAssistantAvailable(Boolean available, boolean longPress) {}
    }
    public static final class StaticCallback {
        public static void onAssistantAvailable(boolean available, boolean longPress) {}
    }
    public abstract static class AbstractCallback {
        public abstract void onAssistantAvailable(boolean available, boolean longPress);
    }
    public static final class NativeCallback {
        public native void onAssistantAvailable(boolean available, boolean longPress);
    }

    public static void main(String[] args) throws Exception {
        expect(DirectService.class, DirectService.class);
        expect(LegacyService.class, LegacyService.Proxy.class);
        expect(ExternalService.class, RenamedProxy.class);
        check(LauncherAssistantBinding.findCallbacks(StaticFieldService.class).isEmpty(),
                "Static fields exposed unrelated callbacks");
        check(LauncherAssistantBinding.findCallbacks(InvalidService.class).isEmpty(),
                "Incompatible or non-instance callbacks accepted");
        System.out.println("PASS: direct, nested and renamed field-held assistant callbacks; invalid targets rejected");
    }

    private static void expect(Class<?> service, Class<?> owner) throws Exception {
        Method expected = owner.getDeclaredMethod("onAssistantAvailable", boolean.class, boolean.class);
        check(LauncherAssistantBinding.findCallbacks(service).equals(List.of(expected)),
                "Missing or duplicate assistant callback for " + service.getSimpleName());
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
