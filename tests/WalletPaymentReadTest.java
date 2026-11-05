package io.github.zopulus.coogle;

import java.io.IOException;

/** Hidden Settings.Secure keys are unreadable to ordinary Android apps, even when already default. */
public final class WalletPaymentReadTest {
    private static final String GOOGLE = "com.google.android.gms/.pay.RenamedService";
    public static void main(String[] args) throws Exception {
        selectionRollback();
        WalletPayment.ReadSetting noRoot = () -> { throw new AssertionError("Unnecessary Root request"); };
        check(GOOGLE.equals(WalletPayment.readCurrent(() -> GOOGLE, noRoot)), "Readable default changed");
        check(WalletPayment.readCurrent(() -> null, noRoot) == null, "Readable empty default changed");
        WalletPayment.ReadSetting restricted = () -> { throw new SecurityException("Settings key is not readable"); };
        int[] rootReads = {0};
        check(GOOGLE.equals(WalletPayment.readCurrent(restricted, () -> { rootReads[0]++; return GOOGLE + "\n"; })),
                "Restricted default was not read through Root");
        check(rootReads[0] == 1, "Default query repeated Root work");
        check(WalletPayment.readCurrent(restricted, () -> "null\n") == null, "Shell null treated as component");
        check(WalletPayment.readCurrent(restricted, () -> "\n") == null, "Shell empty result treated as component");
        IOException denied = new IOException("Root denied");
        try {
            WalletPayment.readCurrent(restricted, () -> { throw denied; });
            throw new AssertionError("Root denial concealed");
        } catch (IOException expected) { check(expected == denied, "Root failure replaced"); }
        try {
            WalletPayment.readCurrent(() -> { throw denied; }, noRoot);
            throw new AssertionError("Ordinary failure concealed");
        } catch (IOException expected) { check(expected == denied, "Non-permission failure triggered fallback"); }
        for (String malformed : new String[]{"Error: permission denied", GOOGLE + "\nother.wallet/.Service", "invalid"}) {
            try {
                WalletPayment.readCurrent(restricted, () -> malformed);
                throw new AssertionError("Invalid shell default accepted");
            } catch (IOException expected) { }
        }
        check(WalletPayment.readCommand(10).equals("settings --user 10 get secure nfc_payment_default_component"),
                "Wrong user or default query");
        System.out.println("PASS: hidden payment setting, Root fallback, no-op readable path, users and failure propagation");
    }
    private static void selectionRollback() throws Exception {
        var context = new android.content.Context() {
            @Override public <T> T getSystemService(Class<T> type) {
                return type.cast(new android.app.role.RoleManager());
            }
        };
        for (RootShell.Reason reason : RootShell.Reason.values()) {
            for (boolean rollbackFails : new boolean[]{false, true}) {
                var commands = new java.util.ArrayList<String>();
                RootShell.Failure failure = new RootShell.Failure(reason);
                IOException rollback = new IOException("restore failed");
                String select = WalletPayment.selectionCommand(10, true, null);
                String restore = WalletPayment.restoreCommand(10, "other.wallet/.Pay", true, "other.wallet");
                var payment = new WalletPayment(context, 10, command -> {
                    commands.add(command);
                    if (command.startsWith("cmd role get-role-holders")) return "other.wallet\n";
                    if (command.equals(select)) throw failure;
                    check(command.equals(restore), "Unexpected payment command");
                    if (rollbackFails) throw rollback;
                    return "";
                });
                try { payment.selectGoogle(); throw new AssertionError("Selection failure accepted"); }
                catch (RootShell.Failure expected) { check(expected == failure, "Original selection failure replaced"); }
                boolean notStarted = reason == RootShell.Reason.AUTHORIZATION || reason == RootShell.Reason.UNAVAILABLE;
                check(commands.size() == (notStarted ? 2 : 3), "Unnecessary or missing payment rollback: " + reason);
                check(failure.getSuppressed().length == (!notStarted && rollbackFails ? 1 : 0), "Wrong rollback failure reporting");
            }
        }
        var commands = new java.util.ArrayList<String>();
        var payment = new WalletPayment(context, 10, command -> {
            commands.add(command);
            return command.startsWith("cmd role get-role-holders") ? "other.wallet\n" : "";
        });
        var undo = payment.selectGoogle();
        check(commands.size() == 2, "Successful selection rolled back early");
        undo.restore();
        check(commands.get(2).equals(WalletPayment.restoreCommand(10, "other.wallet/.Pay", true, "other.wallet")),
                "Later transaction failure cannot restore previous payment choice");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
