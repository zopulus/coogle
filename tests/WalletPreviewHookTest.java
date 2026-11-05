import android.app.Application;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import io.github.zopulus.coogle.FeatureConfig;
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime;
import java.lang.reflect.*;
import java.util.*;

/** Exercises production interceptors; authentication responses remain owned by Wallet. */
public final class WalletPreviewHookTest {
    static boolean enabled = true;
    static final Method callback;
    static final Constructor<?> constructor;
    static {
        try { callback = xht.class.getDeclaredMethod("b", Object.class); constructor = xir.class.getDeclaredConstructor(Application.class); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    public static void main(String[] args) throws Throwable {
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, m, a) -> m.getName().equals("getBoolean") ? enabled : null));
        Class<?> hooks = Class.forName("io.github.zopulus.coogle.migration.WalletPreviewHooks");
        Object instance = hooks.getField("INSTANCE").get(null);
        hooks.getMethod("install", ClassLoader.class, String.class).invoke(instance, WalletPreviewHookTest.class.getClassLoader(), "missing.apk");
        check(ModernHookRuntime.INSTANCE.module.callbacks.isEmpty(), "Unknown APK installed authentication hooks");
        var directory = java.nio.file.Files.createTempDirectory("wallet-unverified-apk");
        var apk = directory.resolve("base.apk");
        var cache = directory.resolve("coogle_app_targets_v1.cache");
        try {
            java.nio.file.Files.write(apk, new byte[]{1});
            var stored = new Properties();
            stored.setProperty("apk", "2:" + apk + ":" + apk.toFile().lastModified() + ":" + apk.toFile().length());
            stored.setProperty("wallet.preview-candidates-v1", "");
            stored.setProperty("wallet.preview-candidates-v2", "");
            try (var output = java.nio.file.Files.newOutputStream(cache)) { stored.store(output, "retired discovery cache"); }
            byte[] previousCache = java.nio.file.Files.readAllBytes(cache);
            hooks.getMethod("install", ClassLoader.class, String.class).invoke(instance,
                    WalletPreviewHookTest.class.getClassLoader(), apk.toString());
            check(ModernHookRuntime.INSTANCE.module.callbacks.isEmpty(), "Unverified APK authorized an authentication Hook");
            check(Arrays.equals(previousCache, java.nio.file.Files.readAllBytes(cache)), "Wallet startup still maintains diagnostic discovery caches");
        } finally {
            java.nio.file.Files.deleteIfExists(cache); java.nio.file.Files.deleteIfExists(apk); java.nio.file.Files.deleteIfExists(directory);
        }
        Method bind = hooks.getDeclaredMethod("bindVerified", ClassLoader.class); bind.setAccessible(true);
        bind.invoke(instance, WalletPreviewHookTest.class.getClassLoader());
        check(ModernHookRuntime.INSTANCE.module.callbacks.size() == 2, "Wrong interceptor count");

        xir warm = model();
        construct(warm, () -> dispatch(warm, true, brat.a));
        check(!warm.q.value && warm.callbacks == 0, "Warm native preview requested new authentication");
        dispatch(warm, false, brat.a);
        check(warm.q.value && warm.callbacks == 1, "Expiry after initialization was bypassed");
        warm.q.value = false;
        dispatch(warm, true, brat.a);
        check(warm.q.value && warm.callbacks == 2, "Later true callback was globally bypassed");

        for (Boolean recent : Arrays.asList(false, null)) {
            xir cold = model(); construct(cold, () -> dispatch(cold, recent, brat.a));
            check(cold.callbacks == 1, "Cold/loading callback was swallowed");
            check(cold.q.value == Boolean.FALSE.equals(recent), "Cold authentication result changed");
        }
        List<java.util.function.Consumer<xir>> unrelated = List.of(
                m -> m.b.activityManager.runningTasks.get(0).baseIntent.setAction("android.intent.action.MAIN"),
                m -> m.b.activityManager.runningTasks.get(0).topActivity = new ComponentName("other", "Activity"),
                m -> m.b.activityManager.runningTasks.get(0).topActivity = new ComponentName("com.google.android.apps.walletnfcrel", "MainActivity"),
                m -> m.b.activityManager.runningTasks.get(0).baseIntent.putExtra("paymentCardInfo", true),
                m -> m.b.activityManager.runningTasks.get(0).baseIntent.putExtra("safePaymentCardInfo", true),
                m -> m.b.activityManager.runningTasks.get(0).baseIntent.putExtra("valuables", true),
                m -> m.b.activityManager.runningTasks.get(0).baseIntent.putExtra("eventType", 5),
                m -> m.b.activityManager.failTasks = true,
                m -> m.b.failServiceQuery = true,
                m -> m.q.value = true,
                m -> m.ui.g = new Object(),
                m -> m.ui.h = List.of(new Object()));
        for (var change : unrelated) {
            xir m = model(); change.accept(m); construct(m, () -> dispatch(m, true, brat.a));
            check(m.callbacks == 1, "Unrelated/failed/already requested path was swallowed");
        }
        xir unlocked = model(); unlocked.b.keyguardManager.locked = false;
        construct(unlocked, () -> dispatch(unlocked, true, brat.a));
        check(unlocked.callbacks == 1 && !unlocked.q.value, "Unlocked original behavior changed");
        xir disabled = model(); enabled = false; construct(disabled, () -> dispatch(disabled, true, brat.a));
        check(disabled.q.value, "Disabled feature altered authentication"); enabled = true;
        xir switched = model(); construct(switched, () -> { enabled = false; dispatch(switched, true, brat.a); });
        check(switched.q.value, "Switch change during initialization ignored"); enabled = true;

        xir payment = model(); construct(payment, () -> {
            dispatch(payment, true, brat.a);
            for (int reason : new int[]{19, 21, 45, 48}) {
                payment.q.value = false; payment.payment(reason);
                check(payment.q.value, "Explicit CVM/payment request was intercepted");
            }
        });
        xir wrongModel = model(); construct(model(), () -> dispatch(wrongModel, true, brat.a));
        check(wrongModel.q.value, "Different model borrowed initialization gate");
        xir background = model(); construct(background, () -> {
            Throwable[] failure = new Throwable[1];
            Thread t = new Thread(() -> { try { dispatch(background, true, brat.a); } catch (Throwable e) { failure[0] = e; } });
            t.start(); t.join(); if (failure[0] != null) throw failure[0];
        });
        check(background.q.value, "Other thread borrowed initialization gate");
        xir failed = model();
        try { construct(failed, () -> { throw new IllegalStateException("constructor failed"); }); throw new AssertionError("Constructor error lost"); }
        catch (IllegalStateException expected) { }
        dispatch(failed, true, brat.a); check(failed.q.value, "Failed constructor leaked gate");
        xir error = model();
        try { construct(error, () -> dispatch(error, true, new IllegalArgumentException("coroutine failed"))); throw new AssertionError("Coroutine error swallowed"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS: warm preview, cold/expiry, CVM, unrelated tasks, switches, threads and error cleanup");
    }
    static xir model() {
        Application app = new Application();
        var task = new ActivityManager.RunningTaskInfo();
        task.topActivity = new ComponentName("com.google.android.apps.walletnfcrel", "com.google.android.apps.wallet.pmode.PresentationModeActivity");
        task.baseIntent = new Intent("com.google.android.apps.wallet.main.QUICKDRAW");
        app.activityManager.runningTasks.add(task);
        return new xir(app);
    }
    interface Body { Object run() throws Throwable; }
    interface Action { void run() throws Throwable; }
    static void construct(xir m, Action action) throws Throwable { invoke(constructor, m, List.of(m.b), () -> { action.run(); return null; }); }
    static void dispatch(xir m, Boolean recent, Object result) throws Throwable {
        xht c = new xht(m, recent);
        Object returned = invoke(callback, c, List.of(result), () -> c.b(result));
        check(returned == brat.a, "Coroutine completion value changed");
    }
    static Object invoke(Executable method, Object owner, List<?> args, Body original) throws Throwable {
        XposedInterface.Hooker hook = ModernHookRuntime.INSTANCE.module.callbacks.get(method);
        check(hook != null, "Production hook missing");
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(), new Class<?>[]{XposedInterface.Chain.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getExecutable" -> method; case "getThisObject" -> owner; case "getArgs" -> args;
                    case "proceed" -> original.run(); default -> throw new AssertionError(m.getName());
                });
        return hook.intercept(chain);
    }
    static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
}
final class xir {
    final Application b; final previewFlow q = new previewFlow(); final previewUi ui = new previewUi(); int callbacks;
    xir(Application app) { b = app; }
    public previewUi a() { return ui; }
    void payment(int reason) { q.value = true; }
}
final class xht {
    Object a; final xir b;
    xht(xir m, Boolean recent) { b = m; a = new axch(recent); }
    public Object b(Object result) {
        if (result instanceof RuntimeException error) throw error;
        b.callbacks++;
        qag response = axcc.a((axch) a);
        if (response != null && (!response.a || b.b.keyguardManager.isDeviceLocked()) && b.ui.g == null && b.ui.h.isEmpty()) b.q.value = true;
        return brat.a;
    }
}
final class qag { public boolean a; qag(boolean v) { a = v; } }
final class axch { final Boolean recent; axch(Boolean r) { recent = r; } }
final class axcc { public static qag a(axch v) { return v.recent == null ? null : new qag(v.recent); } }
final class brat { public static final brat a = new brat(); }
final class previewFlow { boolean value; public Boolean e() { return value; } }
final class previewUi { public Object g; public List<?> h = List.of(); }
