package io.github.zopulus.coogle;

import java.io.IOException;

public final class WalletNativeGestureTest {
    public static void main(String[] args) throws Exception {
        rootFailureRollback();
        Fake driver = new Fake();
        var original = driver.current;
        WalletNativeGesture gesture = new WalletNativeGesture(10, driver, driver);
        gesture.apply(true);
        check(driver.current.equals(new WalletNativeGesture.State("1", "1")) && driver.original.equals(original), "Enable lost previous native configuration");
        gesture.apply(true);
        check(driver.original.equals(original), "Repeated enable replaced original backup");
        gesture.apply(false);
        check(driver.current.equals(original) && driver.original == null, "Disable did not restore native settings");
        int calls = driver.calls;
        gesture.apply(false);
        check(driver.calls == calls, "Unmanaged disable requested Root");
        gesture.apply(true);
        driver.current = new WalletNativeGesture.State("1", "0");
        gesture.apply(false);
        check(driver.current.equals(new WalletNativeGesture.State("1", "0")) && driver.original == null, "Disable overwrote user's newer camera selection");
        driver.current = original;
        var undo = gesture.apply(true);
        undo.restore();
        check(driver.current.equals(original) && driver.original == null, "Preference failure cannot restore configuration and backup");
        gesture.apply(true);
        var disableUndo = gesture.apply(false);
        disableUndo.restore();
        check(driver.current.equals(new WalletNativeGesture.State("1", "1")) && driver.original.equals(original),
                "Failed disable preference commit cannot restore managed Wallet choice");
        driver.failBackup = true;
        try { gesture.apply(false); throw new AssertionError("Failed backup removal accepted"); }
        catch (IOException expected) { }
        check(driver.current.equals(new WalletNativeGesture.State("1", "1")) && driver.original.equals(original),
                "Backup removal failure lost managed state");
        gesture.apply(false);
        for (var previous : new WalletNativeGesture.State[]{new WalletNativeGesture.State("1", "0"), new WalletNativeGesture.State("null", "null")}) {
            driver.current = previous;
            gesture.apply(true);
            gesture.apply(false);
            check(driver.current.equals(previous) && driver.original == null, "Camera or absent native configuration not restored");
        }
        driver.current = original;
        driver.ignoreWrite = true;
        try { gesture.apply(true); throw new AssertionError("Unapplied native write accepted"); }
        catch (IOException expected) { }
        check(driver.current.equals(original) && driver.original == null, "Readback failure not rolled back");
        driver.failWrite = true;
        try { gesture.apply(true); throw new AssertionError("Partial native write accepted"); }
        catch (IOException expected) { }
        check(driver.current.equals(original) && driver.original == null, "Partial native write not rolled back");
        driver.failBackup = true;
        try { gesture.apply(true); throw new AssertionError("Backup failure accepted"); }
        catch (IOException expected) { }
        check(driver.current.equals(original) && driver.original == null, "Backup failure changed native settings");
        gesture.apply(true);
        driver.failedWritesRemaining = 2;
        try { gesture.apply(false); throw new AssertionError("Failed disable and rollback accepted"); }
        catch (IOException expected) { check(expected.getSuppressed().length == 1, "Rollback failure lost"); }
        check(driver.original.equals(original), "Failed disable and rollback discarded recovery backup");
        // Retry after the failed operation; the original state must still be recoverable.
        driver.current = new WalletNativeGesture.State("1", "1");
        gesture.apply(false);
        check(driver.current.equals(original) && driver.original == null, "Disable retry lost prior configuration");
        driver.support = false;
        try { gesture.apply(true); throw new AssertionError("Unsupported native route accepted"); }
        catch (IOException expected) { }
        check(driver.current.equals(original) && driver.original == null, "Unsupported route changed settings");
        for (String reason : new String[]{"MODE_UNAVAILABLE", "MODE_UNSUPPORTED", "FLAGS_UNAVAILABLE", "FLAGS_DISABLED"}) {
            int[] writes = {0};
            var unsupported = new WalletNativeGesture(10, command -> "COOGLE_WALLET_" + reason,
                    new WalletNativeGesture.Backup() {
                        public WalletNativeGesture.State read() { return null; }
                        public boolean write(WalletNativeGesture.State value) { writes[0]++; return true; }
                    });
            try { unsupported.apply(true); throw new AssertionError("Unsupported route accepted"); }
            catch (IOException expected) { check(!expected.getMessage().contains("Root"), "Capability failure mislabeled as Root denial"); }
            check(writes[0] == 0, "Capability error changed backup");
        }
        for (String invalid : new String[]{"1; reboot", "-1", "2147483648", "garbage"}) {
            try { new WalletNativeGesture.State("1", invalid); throw new AssertionError("Invalid action accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("PASS: native enable/disable, prior-state backup, external changes, rollback and unsupported route");
    }
    private static void rootFailureRollback() throws Exception {
        for (RootShell.Reason reason : RootShell.Reason.values()) {
            for (boolean enabled : new boolean[]{false, true}) {
                Fake driver = new Fake();
                var gesture = new WalletNativeGesture(10, driver, driver);
                if (!enabled) gesture.apply(true);
                var before = driver.current;
                var saved = driver.original;
                int writes = driver.nativeWrites;
                RootShell.Failure failure = new RootShell.Failure(reason);
                driver.rootFailure = failure;
                try { gesture.apply(enabled); throw new AssertionError("Root failure accepted"); }
                catch (RootShell.Failure expected) { check(expected == failure, "Original failure replaced"); }
                boolean notStarted = reason == RootShell.Reason.AUTHORIZATION || reason == RootShell.Reason.UNAVAILABLE;
                check(driver.nativeWrites - writes == (notStarted ? 1 : 2), "Wrong rollback count: " + reason);
                check(driver.current.equals(before), "Native state changed after failed write: " + reason);
                check(java.util.Objects.equals(driver.original, saved), "Backup not restored: " + reason);
            }
        }
    }
    static final class Fake implements WalletNativeGesture.Shell, WalletNativeGesture.Backup {
        WalletNativeGesture.State current = new WalletNativeGesture.State("0", "null"), original;
        boolean failWrite, failBackup, ignoreWrite, support = true; int calls, failedWritesRemaining, nativeWrites;
        RootShell.Failure rootFailure;
        public WalletNativeGesture.State read() { return original; }
        public boolean write(WalletNativeGesture.State value) {
            original = value;
            if (failBackup) { failBackup = false; return false; }
            return true;
        }
        public String run(String command) throws IOException {
            calls++;
            if (command.startsWith(WalletNativeGesture.supportCommand(10)) && !support) throw new IOException("Unsupported");
            for (var state : new WalletNativeGesture.State[]{new WalletNativeGesture.State("1", "1"), new WalletNativeGesture.State("0", "null"),
                    new WalletNativeGesture.State("1", "0"), new WalletNativeGesture.State("null", "null")}) {
                if (command.equals(WalletNativeGesture.writeCommand(10, state))) {
                    nativeWrites++;
                    if (rootFailure != null) {
                        var failure = rootFailure; rootFailure = null;
                        if (failure.reason == RootShell.Reason.TIMEOUT || failure.reason == RootShell.Reason.EXECUTION)
                            current = new WalletNativeGesture.State(current.enabled(), state.action());
                        throw failure;
                    }
                    if (ignoreWrite) { ignoreWrite = false; break; }
                    if (failedWritesRemaining > 0) { failedWritesRemaining--; throw new IOException("Persistent write failure"); }
                    if (failWrite) { failWrite = false; current = new WalletNativeGesture.State(current.enabled(), state.action()); throw new IOException("Write failed"); }
                    current = state;
                }
            }
            return current.enabled() + "\n" + current.action();
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
