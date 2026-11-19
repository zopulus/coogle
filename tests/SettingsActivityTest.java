package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import androidx.activity.ComponentActivity;
import io.github.zopulus.coogle.ui.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Runs production Activity actions with controlled UI, preferences and root boundaries. */
public final class SettingsActivityTest {
    static final class Preferences {
        final Map<String, Boolean> values = new ConcurrentHashMap<>();
        final Map<String, String> strings = new HashMap<>();
        final CountDownLatch committing = new CountDownLatch(1), release = new CountDownLatch(1);
        boolean block, fail;
        int commits;
        final SharedPreferences settings = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (p,m,a) -> switch (m.getName()) {
            case "getBoolean" -> values.getOrDefault((String) a[0], (Boolean) a[1]);
            case "getString" -> strings.getOrDefault((String) a[0], (String) a[1]);
            case "edit" -> editor();
            default -> throw new AssertionError("Unexpected preferences call: " + m.getName());
        });
        SharedPreferences.Editor editor() {
            Map<String, Boolean> pending = new HashMap<>();
            Map<String, String> pendingStrings = new HashMap<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (p,m,a) -> {
                if (m.getName().equals("putBoolean")) pending.put((String) a[0], (Boolean) a[1]);
                else if (m.getName().equals("putString")) pendingStrings.put((String) a[0], (String) a[1]);
                else if (m.getName().equals("commit")) {
                    commits++;
                    committing.countDown();
                    if (block && !release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Commit boundary timeout");
                    if (!fail) values.putAll(pending);
                    return !fail;
                } else if (m.getName().equals("apply")) {
                    values.putAll(pending);
                    strings.putAll(pendingStrings);
                    return null;
                } else throw new AssertionError(m.getName());
                return p;
            });
        }
    }

    public static void main(String[] args) throws Exception {
        List<Throwable> failures = new ArrayList<>();
        try {
            for (String scenario : List.of("retry", "restart", "recreated-restart",
                    "save-state-success", "save-state-failure", "save-error", "root-wait", "ui-style")) {
                try {
                    if (scenario.equals("retry")) retry();
                    else if (scenario.startsWith("save-state")) saveState(scenario.endsWith("failure"));
                    else if (scenario.equals("save-error")) saveError();
                    else if (scenario.equals("root-wait")) rootWait();
                    else if (scenario.equals("ui-style")) uiStyle();
                    else restart(scenario.startsWith("recreated"));
                    System.out.println("PASS: " + scenario);
                } catch (AssertionError failure) {
                    failures.add(failure);
                    System.out.println("FAIL: " + scenario + ": " + failure.getMessage());
                }
            }
            if (!failures.isEmpty()) throw new AssertionError(failures.toString());
        } finally {
            for (String name : List.of("worker", "settingsWorker")) {
                ExecutorService executor = executor(name);
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }
    static void retry() throws Exception {
        MainActivity activity = activity(new Preferences());
        invoke(activity, "refreshKnownCompanion");
        invoke(activity, "refreshKnownCompanion");
        idle("worker"); drainUi();
        check(CompanionSettings.checks == 1, "Automatic check was not deduplicated");
        check(!state(activity).getCompanionError().isEmpty(), "Detection error was hidden");
        invoke(activity, "refreshKnownCompanion");
        idle("worker");
        check(CompanionSettings.checks == 1, "Automatic check retried without a new resume");
        invoke(activity, "checkCompanion");
        invoke(activity, "checkCompanion");
        idle("worker"); drainUi();
        check(CompanionSettings.checks == 2, "Manual check did not retry after failure");
        CompanionSettings.fail = false;
        invoke(activity, "checkCompanion");
        idle("worker"); drainUi();
        check(CompanionSettings.checks == 3 && state(activity).getCompanionError().isEmpty(),
                "Successful manual retry did not clear the error");
    }
    static void restart(boolean recreate) throws Exception {
        Preferences preferences = new Preferences();
        preferences.block = true;
        MainActivity original = activity(preferences);
        RootShell.restarted = new CountDownLatch(1);
        save(original);
        check(preferences.committing.await(5, TimeUnit.SECONDS), "Save did not start");
        MainActivity current = original;
        if (recreate) {
            original.destroyed = true;
            current = activity(preferences);
        }
        try {
            invoke(current, "restartScopes");
            check(!RootShell.restarted.await(200, TimeUnit.MILLISECONDS), "Restart overtook the pending save");
        } finally {
            preferences.release.countDown();
            idle("settingsWorker"); idle("worker"); drainUi();
        }
        check(RootShell.restarted.getCount() == 0, "Restart never followed the save");
        check(preferences.values.get(FeaturePolicy.QUICK_ENTRIES), "Setting was not saved");
    }
    static void save(MainActivity activity) throws Exception {
        invoke(activity, "saveSetting", new Class<?>[]{String.class, boolean.class}, FeaturePolicy.QUICK_ENTRIES, true);
    }
    static void saveState(boolean fail) throws Exception {
        Preferences preferences = new Preferences();
        preferences.block = true;
        preferences.fail = fail;
        MainActivity original = activity(preferences);
        save(original);
        check(preferences.committing.await(5, TimeUnit.SECONDS), "Save did not start");
        original.destroyed = true;
        MainActivity recreated = activity(preferences);
        try {
            save(recreated);
        } finally {
            preferences.release.countDown();
            idle("settingsWorker"); drainUi();
        }
        check(preferences.commits == 1, "Recreated Activity duplicated a pending save");
        preferences.fail = false;
        save(recreated);
        idle("settingsWorker"); drainUi();
        check(preferences.commits == 2, "Destroyed Activity left the save permanently pending");
        check(state(recreated).getFeatures().get(FeaturePolicy.QUICK_ENTRIES), "Save result did not update the active UI");
    }
    static void rootWait() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<?> root = executor("worker").submit(() -> {
            started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Root boundary timeout"); }
            catch (InterruptedException failure) { throw new AssertionError(failure); }
        });
        try {
            check(started.await(5, TimeUnit.SECONDS), "Root operation did not start");
            Preferences preferences = new Preferences();
            MainActivity activity = activity(preferences);
            save(activity);
            check(preferences.committing.await(2, TimeUnit.SECONDS), "Root wait blocked an ordinary setting");
            idle("settingsWorker"); drainUi();
            check(state(activity).getFeatures().get(FeaturePolicy.QUICK_ENTRIES), "Ordinary setting did not finish during root wait");
        } finally {
            release.countDown();
            root.get(5, TimeUnit.SECONDS);
        }
    }
    static void saveError() throws Exception {
        Preferences preferences = new Preferences();
        preferences.fail = true;
        MainActivity activity = activity(preferences);
        save(activity);
        idle("settingsWorker"); drainUi();
        check(state(activity).getDialog() == DialogKind.ERROR && !state(activity).getDialogText().isEmpty(),
                "Save failure was hidden");
        preferences.fail = false;
        save(activity);
        idle("settingsWorker"); drainUi();
        check(preferences.commits == 2 && state(activity).getFeatures().get(FeaturePolicy.QUICK_ENTRIES),
                "Failed save could not be retried");
    }
    static MainActivity activity(Preferences preferences) throws Exception {
        return activity(preferences, new Preferences());
    }
    static void uiStyle() throws Exception {
        Preferences preferences = new Preferences();
        Preferences cache = new Preferences();
        cache.strings.put("companion", "READY");
        MainActivity activity = activity(preferences, cache);
        CoogleState initial = state(activity);
        check(invoke(activity, "getUiStyle") == UiStyle.COUI, "Initial appearance is not COUI");
        check(UiStyle.fromPreference(null) == UiStyle.COUI, "Fresh install did not default to COUI");
        check(UiStyle.fromPreference("unknown") == UiStyle.COUI, "Unknown stored style did not fall back");
        check(UiStyle.values()[0] == UiStyle.COUI, "COUI is not the first selector option");
        for (UiStyle choice : List.of(UiStyle.MATERIAL, UiStyle.COUI)) {
            invoke(activity, "changeUiStyle", new Class<?>[]{UiStyle.class}, choice);
            check(invoke(activity, "getUiStyle") == choice, "Appearance did not update immediately");
            check(UiStyle.fromPreference(cache.settings.getString("ui_style", null)) == choice, "Appearance was not saved");
        }
        check(state(activity) == initial && preferences.values.isEmpty() && preferences.strings.isEmpty(),
                "Appearance changed feature state or remote preferences");
        check(cache.strings.equals(Map.of("companion", "READY", "ui_style", "COUI")),
                "Appearance overwrote unrelated local settings");
    }
    static MainActivity activity(Preferences preferences, Preferences cache) throws Exception {
        MainActivity activity = new MainActivity();
        field("preferences").set(activity, preferences.settings);
        field("uiCache$delegate").set(activity, new kotlin.Lazy<SharedPreferences>() {
            public SharedPreferences getValue() { return cache.settings; }
            public boolean isInitialized() { return true; }
        });
        invoke(activity, "setState", new Class<?>[]{CoogleState.class},
                new CoogleState(true, Map.of(FeaturePolicy.QUICK_ENTRIES, false), false, false, false,
                        "", CompanionState.READY, false, "", false, DialogKind.NONE, ""));
        return activity;
    }
    static CoogleState state(MainActivity activity) throws Exception {
        return (CoogleState) invoke(activity, "getState");
    }
    static void drainUi() {
        Runnable action;
        while ((action = ComponentActivity.ui.poll()) != null) action.run();
    }
    static Field field(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    static ExecutorService executor(String name) throws Exception { return (ExecutorService) field(name).get(null); }
    static void idle(String name) throws Exception { executor(name).submit(() -> {}).get(5, TimeUnit.SECONDS); }
    static Object invoke(Object target, String name) throws Exception { return invoke(target, name, new Class<?>[0]); }
    static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(target, args);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
