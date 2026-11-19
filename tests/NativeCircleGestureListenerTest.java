package io.github.zopulus.coogle;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;

public final class NativeCircleGestureListenerTest {
    public interface TouchEvent {
        void onLongPressed();
        default void onShowPress() {}
    }
    public static final class Controller {
        final LinkedHashSet<TouchEvent> listeners = new LinkedHashSet<>();
        int adds, removes;
        boolean fail;
        public void addHomeHandlerLongClickListener(TouchEvent listener) {
            if (fail) throw new IllegalStateException("registration failed");
            adds++; listeners.add(listener);
        }
        public void removeHomeHandlerLongClickListener(TouchEvent listener) {
            removes++; listeners.remove(listener);
        }
    }
    public static void main(String[] args) throws Exception {
        if (!NativeCircleGestureListener.canStart(0L, false, false, false)) throw new AssertionError("Ordinary gesture blocked");
        for (long restricted : new long[]{8, 64, 512, 4, 2048}) {
            if (NativeCircleGestureListener.canStart(restricted, false, false, false))
                throw new AssertionError("International SystemUI restriction omitted: " + restricted);
        }
        if (!NativeCircleGestureListener.canStart(1L << 41, false, false, false))
            throw new AssertionError("Unrelated SystemUI flag blocked navigation");
        if (NativeCircleGestureListener.canStart(0L, true, false, false)) throw new AssertionError("Minimized split screen launched");
        if (NativeCircleGestureListener.canStart(0L, false, true, false)) throw new AssertionError("Restricted outer display launched");
        if (!NativeCircleGestureListener.canStart(0L, false, true, true)) throw new AssertionError("Inner display incorrectly blocked");
        Controller controller = new Controller();
        AtomicInteger calls = new AtomicInteger();
        NativeCircleGestureListener bridge = new NativeCircleGestureListener(controller, TouchEvent.class, calls::incrementAndGet);
        bridge.update(false);
        if (!controller.listeners.isEmpty() || bridge.isRegistered()) throw new AssertionError("disabled listener registered");
        controller.fail = true;
        try { bridge.update(true); throw new AssertionError("failure hidden"); }
        catch (ReflectiveOperationException expected) {}
        if (bridge.isRegistered()) throw new AssertionError("failed registration became active");
        controller.fail = false;
        bridge.update(true); bridge.update(true);
        if (controller.adds != 1 || controller.listeners.size() != 1) throw new AssertionError("duplicate registration");
        TouchEvent listener = controller.listeners.iterator().next();
        listener.onShowPress(); listener.onLongPressed();
        if (calls.get() != 1) throw new AssertionError("non-long-press dispatched");
        bridge.update(false); bridge.update(false);
        listener.onLongPressed();
        if (controller.removes != 1 || !controller.listeners.isEmpty() || calls.get() != 1) throw new AssertionError("disabled listener still dispatches");
        bridge.update(true); listener.onLongPressed();
        if (calls.get() != 2) throw new AssertionError("re-enable failed");
        System.out.println("PASS: native circle listener registration, rollback, single dispatch and disable");
    }
}
