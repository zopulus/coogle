package io.github.zopulus.coogle.migration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Locate the launcher's native assistant callback without relying on its proxy class name. */
public final class LauncherAssistantBinding {
    private LauncherAssistantBinding() {}

    public static List<Method> findCallbacks(Class<?> service) {
        Set<Class<?>> candidates = new LinkedHashSet<>();
        candidates.add(service);
        candidates.addAll(List.of(service.getDeclaredClasses()));
        // Recent launchers move the Binder proxy out of the service's nested classes.
        for (Field field : service.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) candidates.add(field.getType());
        }
        List<Method> callbacks = new ArrayList<>();
        for (Class<?> candidate : candidates) {
            for (Method method : candidate.getDeclaredMethods()) {
                if (method.getName().equals("onAssistantAvailable")
                        && method.getReturnType() == void.class
                        && !Modifier.isStatic(method.getModifiers())
                        && !Modifier.isAbstract(method.getModifiers())
                        && !Modifier.isNative(method.getModifiers())
                        && List.of(method.getParameterTypes()).equals(List.of(boolean.class, boolean.class))) {
                    callbacks.add(method);
                }
            }
        }
        return List.copyOf(callbacks);
    }
}
