package io.github.zopulus.coogle;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.*;
import android.provider.Settings;
import android.app.contextualsearch.*;
import com.android.systemui.assist.AssistManager;
import io.github.libxposed.api.XposedInterface;
import io.github.zopulus.coogle.migration.AssistantRuntime;
import io.github.zopulus.coogle.migration.compat.ModernHookRuntime;
import java.lang.reflect.*;
import java.util.List;

/** Exercise the installed production AssistManager callback, not a copied policy. */
public class HiddenCircleNoAssistantTest {
    public static class SearchService implements IBinder,IContextualSearchManager {
        int calls;
        public void startContextualSearch(int entry,ContextualSearchConfig config) {
            check(entry==1 && config==null,"Native contextual defaults changed");
            calls++;
        }
    }
    public static void main(String[] args) throws Throwable {
        boolean[] navigation={true};
        FeatureConfig.attach((SharedPreferences)Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},
            (p,m,a)->m.getName().equals("getBoolean") ? navigation[0] : null));
        AssistantRuntime.INSTANCE.setSystemContext(new Context());
        Class<?> owner=Class.forName("io.github.zopulus.coogle.migration.AssistantSystemUiHooks");
        Method install=owner.getDeclaredMethod("hookNativeAssistantSystemUiGate",ClassLoader.class);
        install.setAccessible(true);
        install.invoke(owner.getField("INSTANCE").get(null),HiddenCircleNoAssistantTest.class.getClassLoader());
        Method method=AssistManager.class.getMethod("startAssist$1",Bundle.class);
        XposedInterface.Hooker hook=ModernHookRuntime.INSTANCE.module.callbacks.get(method);
        check(hook!=null,"Native AssistManager hook not installed");
        AssistManager manager=new AssistManager();
        SearchService search=new SearchService();
        ServiceManager.current=search;
        invoke(hook,method,manager,8,0);
        check(search.calls==1,"Missing assistant prevented hidden circle invocation");
        invoke(hook,method,manager,1000,0);
        check(search.calls==2,"Alternate circle invocation was not dispatched");
        manager.assistant=new Object(); invoke(hook,method,manager,8,0);
        check(search.calls==3,"Selected assistant did not use unified search path");
        manager.assistant=null; manager.mActivityManager.lockTaskMode=1;
        invoke(hook,method,manager,8,1); check(search.calls==3,"Lock task bypassed");
        manager.mActivityManager.lockTaskMode=0; Settings.Secure.circle=0;
        invoke(hook,method,manager,8,1); check(search.calls==3,"Disabled circle invoked");
        Settings.Secure.circle=1; navigation[0]=false;
        invoke(hook,method,manager,8,1); check(search.calls==3,"Disabled module invoked");
        navigation[0]=true; invoke(hook,method,manager,1,1);
        check(search.calls==3,"Corner assistant was redirected to search");
        ServiceManager.current=null; invoke(hook,method,manager,8,1);
        check(search.calls==3,"Unavailable service counted as success");
        verifyNestedRoutes(hook,method,manager);
        Field frames=owner.getDeclaredField("nativeAssistantRouteFrames"); frames.setAccessible(true);
        check(((ThreadLocal<?>)frames.get(owner.getField("INSTANCE").get(null))).get()==null,"Route frames leaked");
        check(Binder.cleared==Binder.restored,"Calling identity leaked");
        System.out.println("PASS: native circle, switches, lock task, nested route scope, thread isolation and exception cleanup");
    }
    static void invoke(XposedInterface.Hooker hook,Method method,AssistManager manager,int type,int expectedProceed) throws Throwable {
        invoke(hook,method,manager,type,expectedProceed,() -> {});
    }
    interface NativeCall { void run() throws Throwable; }
    static void invoke(XposedInterface.Hooker hook,Method method,AssistManager manager,int type,int expectedProceed,NativeCall nativeCall) throws Throwable {
        Bundle bundle=new Bundle(); bundle.putInt("invocation_type",type); int[] nativeCalls={0};
        XposedInterface.Chain chain=(XposedInterface.Chain)Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(p,m,a)->switch(m.getName()) {
            case "getExecutable" -> method; case "getThisObject" -> manager; case "getArgs" -> List.of(bundle);
            case "proceed" -> {nativeCalls[0]++;nativeCall.run();yield null;}
            default -> throw new AssertionError("Unexpected chain access: "+m.getName());
        });
        hook.intercept(chain);
        check(nativeCalls[0]==expectedProceed,"Unexpected native dispatch for type="+type+": "+nativeCalls[0]);
    }
    static void verifyNestedRoutes(XposedInterface.Hooker hook,Method method,AssistManager manager) throws Throwable {
        check(!regionEnabled(),"Region override escaped assistant invocation");
        invoke(hook,method,manager,8,1,() -> {
            check(regionEnabled(),"Enabled outer route not visible");
            invoke(hook,method,manager,0,1,() -> check(regionEnabled(),"Disabled inner route hid enabled outer route"));
            check(regionEnabled(),"Inner exit cleared enabled outer route");
            var error=new IllegalStateException("native assistant failed");
            try {
                invoke(hook,method,manager,8,1,() -> { throw error; });
                throw new AssertionError("Native failure swallowed");
            } catch(IllegalStateException expected) { check(expected==error,"Native failure changed"); }
            check(regionEnabled(),"Inner exception cleared enabled outer route");
            var threadFailure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread other=new Thread(() -> {
                try { check(!regionEnabled(),"Route state leaked between threads"); }
                catch(Throwable failure) { threadFailure.set(failure); }
            });
            other.start(); other.join(2000);
            check(!other.isAlive(),"Thread isolation check did not finish");
            if(threadFailure.get()!=null) throw threadFailure.get();
        });
        check(!regionEnabled(),"Outer exit left region override enabled");
        invoke(hook,method,manager,0,1,() -> {
            check(!regionEnabled(),"Disabled outer route enabled region override");
            invoke(hook,method,manager,8,1,() -> check(regionEnabled(),"Enabled inner route not visible"));
            check(!regionEnabled(),"Inner exit enabled disabled outer route");
        });
        var error=new IllegalStateException("outer native failure");
        try {
            invoke(hook,method,manager,8,1,() -> { throw error; });
            throw new AssertionError("Outer native failure swallowed");
        } catch(IllegalStateException expected) { check(expected==error,"Outer native failure changed"); }
        check(!regionEnabled(),"Outer exception leaked region override");
    }
    static boolean regionEnabled() throws Throwable {
        Method method=com.oplusos.systemui.common.feature.FeatureOption.class.getMethod("isExpRegion");
        XposedInterface.Hooker hook=ModernHookRuntime.INSTANCE.module.callbacks.get(method);
        check(hook!=null,"Region gate hook missing");
        var chain=(XposedInterface.Chain)Proxy.newProxyInstance(XposedInterface.Chain.class.getClassLoader(),new Class<?>[]{XposedInterface.Chain.class},(p,m,a)->switch(m.getName()) {
            case "getExecutable" -> method; case "getThisObject" -> null; case "getArgs" -> List.of();
            case "proceed" -> false;
            default -> throw new AssertionError("Unexpected gate access: "+m.getName());
        });
        return (Boolean)hook.intercept(chain);
    }
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
}
