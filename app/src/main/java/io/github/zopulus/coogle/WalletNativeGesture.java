package io.github.zopulus.coogle;

import android.content.Context;
import android.os.Process;
import java.io.IOException;

/** Configures the platform's existing gesture; never dispatches Wallet or authenticates a user. */
final class WalletNativeGesture implements WalletSettings.Gesture {
    private static final String ENABLED = "double_tap_power_button_gesture_enabled";
    private static final String ACTION = "double_tap_power_button_gesture";
    private static final State WALLET = new State("1", "1");
    interface Shell { String run(String command) throws Exception; }
    interface Backup { State read() throws Exception; boolean write(State state); }
    record State(String enabled, String action) {
        State {
            if (!("null".equals(enabled) || "0".equals(enabled) || "1".equals(enabled)) || !validAction(action))
                throw new IllegalArgumentException("原生钱包手势配置无效，未更改系统设置。");
        }
        private static boolean validAction(String value) {
            if ("null".equals(value)) return true;
            if (value == null || !value.matches("[0-9]{1,10}")) return false;
            try { return Integer.parseInt(value) >= 0; }
            catch (NumberFormatException invalid) { return false; }
        }
    }
    private final int user;
    private final Shell shell;
    private final Backup backup;

    WalletNativeGesture(Context context) {
        this(Process.myUid() / 100000, command -> RootShell.run(command, 20), preferences(context));
    }
    WalletNativeGesture(int user, Shell shell, Backup backup) {
        if (user < 0) throw new IllegalArgumentException("Invalid Android user");
        this.user = user; this.shell = shell; this.backup = backup;
    }
    private static Backup preferences(Context context) {
        var settings = context.getSharedPreferences("wallet_native_gesture", Context.MODE_PRIVATE);
        return new Backup() {
            public State read() {
                return settings.getBoolean("managed", false) ? new State(settings.getString("enabled", null), settings.getString("action", null)) : null;
            }
            public boolean write(State value) {
                var edit = settings.edit().clear();
                if (value != null) edit.putBoolean("managed", true).putString("enabled", value.enabled()).putString("action", value.action());
                return edit.commit();
            }
        };
    }

    public WalletSettings.Undo apply(boolean enabled) throws Exception {
        State saved = backup.read();
        if (!enabled && saved == null) return () -> {};
        State before = read(enabled);
        // Restore only if our choice is still selected; preserve a newer choice made in system settings.
        State next = before;
        State nextBackup = null;
        if (enabled) { next = WALLET; nextBackup = saved == null ? before : saved; }
        else if (WALLET.equals(before)) next = saved;
        boolean changedBackup = !java.util.Objects.equals(saved, nextBackup);
        boolean changedNative = !before.equals(next);
        boolean writeStarted = false;
        try {
            // Persist a recovery copy before selection; remove it only after restoration succeeds.
            if (changedBackup && nextBackup != null) saveBackup(nextBackup);
            if (changedNative) { writeStarted = true; write(next); }
            if (changedBackup && nextBackup == null) saveBackup(null);
        } catch (Exception failure) {
            boolean restored = !writeStarted || RootShell.commandNotStarted(failure);
            if (!restored) {
                try { write(before); restored = true; }
                catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            }
            // Retain the recovery backup if restoring the platform setting failed.
            if (changedBackup && (restored || saved != null)) {
                try { saveBackup(saved); }
                catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            }
            throw failure;
        }
        return () -> {
            if (changedBackup && saved != null) saveBackup(saved);
            if (changedNative) write(before);
            if (changedBackup && saved == null) saveBackup(null);
        };
    }
    private void saveBackup(State value) throws IOException {
        if (!backup.write(value)) throw new IOException("原生钱包手势的原设置未保存，未完成操作。");
    }

    private State read(boolean requireSupport) throws Exception {
        return parse(shell.run((requireSupport ? supportCommand(user) : "") + readCommand(user)));
    }
    private void write(State value) throws Exception {
        if (!value.equals(parse(shell.run(writeCommand(user, value)))))
            throw new IOException("原生钱包手势配置未生效，已尝试恢复原设置。");
    }
    private static State parse(String output) throws IOException {
        switch (output.trim()) {
            case "COOGLE_WALLET_MODE_UNAVAILABLE": throw new IOException("无法读取系统原生双击模式，未更改手势设置。");
            case "COOGLE_WALLET_MODE_UNSUPPORTED": throw new IOException("当前系统不支持原生双击钱包模式，未更改手势设置。");
            case "COOGLE_WALLET_FLAGS_UNAVAILABLE": throw new IOException("无法读取原生钱包支持标志，未更改手势设置。");
            case "COOGLE_WALLET_FLAGS_DISABLED": throw new IOException("当前系统未启用原生双击钱包支持，未更改手势设置。");
        }
        String[] values = output.trim().split("\\R", -1);
        if (values.length != 2) throw new IOException("原生钱包手势配置读取失败。");
        try { return new State(values[0], values[1]); }
        catch (IllegalArgumentException invalid) { throw new IOException(invalid.getMessage(), invalid); }
    }
    static String supportCommand(int user) {
        return "m=$(cmd overlay lookup --user " + user + " android android:integer/config_doubleTapPowerGestureMode) || " + unsupported("MODE_UNAVAILABLE")
                + "[ \"$m\" = 2 ] || " + unsupported("MODE_UNSUPPORTED")
                // Android's printf is external: never pass the full aflags list as one argument.
                + "f=$(set -o pipefail; aflags list | grep -E '^android\\.service\\.quickaccesswallet\\.launch_wallet_(option_on_power_double_tap|via_sysui_callbacks)[[:space:]]') || " + unsupported("FLAGS_UNAVAILABLE")
                + "printf '%s\\n' \"$f\" | grep -Eq '^android\\.service\\.quickaccesswallet\\.launch_wallet_option_on_power_double_tap[[:space:]]+enabled[[:space:]]' || " + unsupported("FLAGS_DISABLED")
                + "printf '%s\\n' \"$f\" | grep -Eq '^android\\.service\\.quickaccesswallet\\.launch_wallet_via_sysui_callbacks[[:space:]]+enabled[[:space:]]' || " + unsupported("FLAGS_DISABLED");
    }
    private static String unsupported(String reason) {
        // A capability failure is not a Root denial; report it through the native parser.
        return "{ printf 'COOGLE_WALLET_" + reason + "\\n'; exit 0; }; ";
    }
    static String readCommand(int user) {
        return "settings --user " + user + " get secure " + ENABLED + " || exit 1; settings --user " + user + " get secure " + ACTION + " || exit 1";
    }
    static String writeCommand(int user, State value) {
        // Select Wallet before enabling; disable before restoring a former target.
        String enabled = settingCommand(user, ENABLED, value.enabled());
        String action = settingCommand(user, ACTION, value.action());
        return ("1".equals(value.enabled()) ? action + " || exit 1; " + enabled : enabled + " || exit 1; " + action)
                + " || exit 1; " + readCommand(user);
    }
    private static String settingCommand(int user, String key, String value) {
        // settings delete prints a row count; the parser must receive only the final two get results.
        // Keep stderr and the exit status so failed writes still trigger rollback.
        return "settings --user " + user + ("null".equals(value) ? " delete secure " + key : " put secure " + key + " " + value)
                + " >/dev/null";
    }
}
