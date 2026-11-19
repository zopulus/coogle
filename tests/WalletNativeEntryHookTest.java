package io.github.zopulus.coogle;

import android.content.SharedPreferences;
import com.android.server.GestureLauncherService;
import com.android.server.policy.OplusWalletController;
import com.android.server.policy.PhoneWindowManagerExtImpl;
import io.github.libxposed.api.XposedInterface;
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime;
import java.lang.reflect.*;
import java.util.List;

/** The module must only suppress the duplicate vendor route after native Wallet is selected. */
public final class WalletNativeEntryHookTest {
    private static final class LegacyPolicy { boolean mShutdownChordEnabled; }

    public static void main(String[] args) throws Throwable {
        boolean[] enabled = {true};
        FeatureConfig.attach((SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (p, m, a) -> m.getName().equals("getBoolean") ? enabled[0] : null));
        Class<?> owner = Class.forName("io.github.zopulus.coogle.migration.WalletPowerHooks");
        @SuppressWarnings("unchecked")
        var legacyGate = (java.util.function.Predicate<Object>) owner.getMethod("bindRegionGate", Class.class)
                .invoke(null, LegacyPolicy.class);
        var legacy = new LegacyPolicy();
        check(legacyGate.test(legacy), "Older firmware no longer recognizes the regional gate");
        legacy.mShutdownChordEnabled = true;
        check(!legacyGate.test(legacy), "Older firmware overrode an unrelated rejection");
        owner.getMethod("hook", ClassLoader.class).invoke(owner.getField("INSTANCE").get(null), WalletNativeEntryHookTest.class.getClassLoader());
        Method method = OplusWalletController.class.getMethod("isQuickLaunchSupport");
        var module = ModernHookRuntime.INSTANCE.module;
        XposedInterface.Hooker hook = module.callbacks.get(method);
        Method gateMethod = PhoneWindowManagerExtImpl.class.getMethod("isCameraGestureEnabled");
        var gate = module.callbacks.get(gateMethod);
        check(hook != null && gate != null && module.callbacks.size() == 2, "Native entry gate missing or launch/authentication methods hooked");
        OplusWalletController controller = new OplusWalletController();
        GestureLauncherService.selected = true;
        check(invoke(hook, method, controller, true).equals(false), "Selected native Wallet retained duplicate vendor launch");
        check(GestureLauncherService.queriedUser == 10, "Native selection checked for wrong user");
        GestureLauncherService.selected = false;
        check(invoke(hook, method, controller, true).equals(true), "Inactive native Wallet disabled vendor path");
        int queried = GestureLauncherService.queries;
        GestureLauncherService.selected = true;
        check(invoke(hook, method, controller, false).equals(false) && GestureLauncherService.queries == queried,
                "Already disabled vendor route inspected native state");
        enabled[0] = false;
        check(invoke(hook, method, controller, true).equals(true) && GestureLauncherService.queries == queried,
                "Disabled module changed original behavior");
        PhoneWindowManagerExtImpl policy = new PhoneWindowManagerExtImpl();
        enabled[0] = true;
        check(invoke(gate, gateMethod, policy, false).equals(true), "CN region gate still blocks selected native Wallet");
        check(GestureLauncherService.queriedUser == 11, "Power gate used wallet-controller user instead of policy user");
        policy.mPowerButtonRedefineFeatureEnabled = true;
        check(invoke(gate, gateMethod, policy, false).equals(true), "New firmware's domestic market gate still blocks Wallet");
        GestureLauncherService.selected = false;
        check(invoke(gate, gateMethod, policy, false).equals(false), "Power gate enabled an unselected native gesture");
        queried = GestureLauncherService.queries;
        GestureLauncherService.selected = true;
        enabled[0] = false;
        check(invoke(gate, gateMethod, policy, false).equals(false) && GestureLauncherService.queries == queried,
                "Disabled feature changed region gate");
        enabled[0] = true;
        PhoneWindowManagerExtImpl.sSosPowerKeyDownCount = 3;
        check(invoke(gate, gateMethod, policy, false).equals(false) && GestureLauncherService.queries == queried,
                "Native Wallet bypassed three-press SOS restriction");
        check(invoke(hook, method, controller, true).equals(true) && GestureLauncherService.queries == queried,
                "Vendor fallback was suppressed while SOS blocks the native route");
        PhoneWindowManagerExtImpl.sSosPowerKeyDownCount = 5;
        policy.mIsOneplusExp = true;
        check(invoke(gate, gateMethod, policy, false).equals(false) && GestureLauncherService.queries == queried,
                "An unrelated original rejection was overridden");
        check(invoke(gate, gateMethod, policy, true).equals(true) && GestureLauncherService.queries == queried,
                "Already enabled native gate was altered");
        check(policy.mIsOneplusExp && policy.mPowerButtonRedefineFeatureEnabled && PhoneWindowManagerExtImpl.sSosPowerKeyDownCount == 5,
                "Power/SOS fields were changed");
        policy.mIsOneplusExp = false;
        policy.mContext = null;
        check(invoke(gate, gateMethod, policy, false).equals(false), "Missing policy context opened native gate");
        policy.mContext = new android.content.Context();
        GestureLauncherService.fail = true;
        check(invoke(gate, gateMethod, policy, false).equals(false), "Failed native query opened power gate");
        check(invoke(hook, method, controller, true).equals(true), "Failed native query disabled original vendor route");
        GestureLauncherService.fail = false;
        System.out.println("PASS: native region gate, SOS restriction, vendor-route isolation, current users and disabled behavior");
    }
    private static Object invoke(XposedInterface.Hooker hook, Method method, Object owner, boolean original) throws Throwable {
        int[] proceeds = {0};
        var chain = (XposedInterface.Chain) Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),
                new Class<?>[]{XposedInterface.Chain.class}, (p, m, a) -> switch (m.getName()) {
                    case "getExecutable" -> method;
                    case "getThisObject" -> owner;
                    case "getArgs" -> List.of();
                    case "proceed" -> { proceeds[0]++; yield original; }
                    default -> throw new AssertionError("Unexpected hook access " + m.getName());
                });
        Object result = hook.intercept(chain);
        check(proceeds[0] == 1, "Original eligibility was skipped");
        return result;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
