package io.github.zopulus.coogle;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.nfc.cardemulation.HostApduService;
import android.os.Process;
import android.provider.Settings;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;

/** Selects Google's NFC payment provider using the native role or legacy NFC setting. */
final class WalletPayment implements WalletSettings.Payment {
    private static final String WALLET = "com.google.android.apps.walletnfcrel";
    private static final String GMS = "com.google.android.gms";
    private static final String DEFAULT = "nfc_payment_default_component";
    private final Context context;
    interface Shell { String run(String command) throws Exception; }
    private final Shell shell;
    // Android's UserHandle.PER_USER_RANGE.
    private final int user;
    WalletPayment(Context context) { this(context, Process.myUid() / 100000, command -> RootShell.run(command, 20)); }
    WalletPayment(Context context, int user, Shell shell) {
        this.context = context; this.user = user; this.shell = shell;
    }
    interface ReadSetting { String read() throws Exception; }
    private String current() throws Exception {
        return readCurrent(() -> Settings.Secure.getString(context.getContentResolver(), DEFAULT),
                () -> shell.run(readCommand(user)));
    }
    static String readCurrent(ReadSetting direct, ReadSetting privileged) throws Exception {
        try { return direct.read(); }
        catch (SecurityException restricted) {
            // Android S+ hides this key from ordinary apps, including an already selected wallet.
            String value = privileged.read().trim();
            if (value.isEmpty() || "null".equals(value)) return null;
            if (!value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+"))
                throw new IOException("无法读取系统默认付款应用，未更改付款设置。");
            return value;
        }
    }
    static String readCommand(int user) {
        return "settings --user " + user + " get secure " + DEFAULT;
    }

    public boolean isGoogleDefault() throws Exception {
        PackageManager pm = context.getPackageManager();
        try {
            if (!pm.getApplicationInfo(WALLET, 0).enabled) throw new IOException("请先安装并启用 Google 钱包。");
        } catch (PackageManager.NameNotFoundException missing) {
            throw new IOException("请先安装并启用 Google 钱包。", missing);
        }
        if (!pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION))
            throw new IOException("当前设备不支持 NFC 付款。");
        String component = current();
        if (!WalletPowerPolicy.isGooglePayment(component)) return false;
        ComponentName selected = ComponentName.unflattenFromString(component);
        if (selected == null) return false;
        try {
            ServiceInfo service = pm.getServiceInfo(selected, PackageManager.MATCH_DIRECT_BOOT_AWARE
                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            return service.enabled && service.exported && service.applicationInfo.enabled
                    && "android.permission.BIND_NFC_SERVICE".equals(service.permission);
        } catch (PackageManager.NameNotFoundException missing) { return false; }
    }

    public WalletSettings.Undo selectGoogle() throws Exception {
        String previous = current();
        RoleManager roles = context.getSystemService(RoleManager.class);
        boolean useRole = roles != null && roles.isRoleAvailable(RoleManager.ROLE_WALLET);
        String oldHolder = useRole ? shell.run("cmd role get-role-holders --user " + user
                + " " + RoleManager.ROLE_WALLET).trim() : "";
        if (!oldHolder.isEmpty() && !oldHolder.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))
            throw new IOException("无法读取系统默认钱包，未更改付款设置。");
        String component = useRole ? null : paymentService();
        WalletSettings.Undo undo = () -> shell.run(restoreCommand(user, previous, useRole, oldHolder));
        try { shell.run(selectionCommand(user, useRole, component)); }
        catch (Exception failure) {
            if (RootShell.commandNotStarted(failure)) throw failure;
            try { undo.restore(); }
            catch (Exception rollback) { if (rollback != failure) failure.addSuppressed(rollback); }
            throw failure;
        }
        return undo;
    }

    private String paymentService() throws Exception {
        PackageManager pm = context.getPackageManager();
        String candidate = null;
        for (var result : pm.queryIntentServices(new Intent(HostApduService.SERVICE_INTERFACE).setPackage(GMS),
                PackageManager.GET_META_DATA | PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE)) {
            ServiceInfo service = result.serviceInfo;
            if (service == null || !service.enabled || !service.exported || !GMS.equals(service.packageName)
                    || !"android.permission.BIND_NFC_SERVICE".equals(service.permission)) continue;
            var parser = service.loadXmlMetaData(pm, HostApduService.SERVICE_META_DATA);
            if (parser == null) continue;
            try {
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() != XmlPullParser.START_TAG || !"aid-group".equals(parser.getName())
                            || !"payment".equals(parser.getAttributeValue("http://schemas.android.com/apk/res/android", "category"))) continue;
                    String found = new ComponentName(service.packageName, service.name).flattenToString();
                    if (candidate != null && !candidate.equals(found))
                        throw new IOException("存在多个 Google 付款服务，请先在系统 NFC 设置中选择 Google 付款应用。");
                    candidate = found;
                    break;
                }
            } finally { parser.close(); }
        }
        if (candidate == null) throw new IOException("未找到 Google 付款服务，请完成 Google 钱包的初始化。");
        return candidate;
    }

    static String selectionCommand(int user, boolean role, String component) {
        String select = role ? "cmd role add-role-holder --user " + user + " "
                + RoleManager.ROLE_WALLET + " " + GMS + " 0"
                : "settings --user " + user + " put secure " + DEFAULT + " " + quote(component);
        // The role callback precedes NFC's update; verify the native route with a bounded wait.
        return select + " || exit 1; i=0; while [ \"$i\" -lt 10 ]; do "
                + "p=$(settings --user " + user + " get secure " + DEFAULT + "); "
                + "case \"$p\" in com.google.android.gms/*) exit 0;; esac; "
                + "i=$((i+1)); sleep 0.2; done; exit 1";
    }
    static String restoreCommand(int user, String component, boolean role, String holder) {
        String restore = role ? (holder.isEmpty() ? "cmd role clear-role-holders" : "cmd role add-role-holder")
                + " --user " + user + " " + RoleManager.ROLE_WALLET
                + (holder.isEmpty() ? " 0" : " " + quote(holder) + " 0") + " && " : "";
        return restore + "settings --user " + user + (component == null || component.isEmpty()
                ? " delete secure " + DEFAULT : " put secure " + DEFAULT + " " + quote(component));
    }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
