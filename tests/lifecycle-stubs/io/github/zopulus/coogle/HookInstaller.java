package io.github.zopulus.coogle;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Executable;
import java.util.LinkedHashMap;
import java.util.Map;
public class HookInstaller {
    public HookInstaller() {}
    public HookInstaller(io.github.libxposed.api.XposedModule module) {}
    public static Class<?> target(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }
    public static java.lang.reflect.Method booleanMethod(Class<?> type, String name) throws NoSuchMethodException {
        var method = type.getDeclaredMethod(name);
        if (method.getReturnType() != boolean.class || !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(type.getName() + "." + name + " incompatible signature");
        return method;
    }
    public int installed;
    public XposedInterface.Hooker callback;
    public final Map<Executable,XposedInterface.Hooker> callbacks = new LinkedHashMap<>();
    public int failAt = -1, constructorsDeoptimized;
    public void install(Executable method,XposedInterface.Hooker hook) {
        if (installed + 1 == failAt) throw new IllegalStateException("Injected hook failure");
        installed++; callback=hook; callbacks.put(method, hook);
    }
    public int mark() { return installed; }
    public void unhookSince(int mark) {
        while (callbacks.size() > mark) {
            Executable last = null;
            for (Executable method : callbacks.keySet()) last = method;
            callbacks.remove(last);
        }
        installed = mark;
    }
    public boolean deoptimize(Executable method) {
        if (method instanceof java.lang.reflect.Constructor) constructorsDeoptimized++;
        return true;
    }
    public void info(String message) {}
    public void error(String message,Throwable failure) {}
}
