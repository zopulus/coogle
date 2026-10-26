package io.github.zopulus.coogle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;

/** Names may change; retain the inspected eligibility signatures and controller layouts. */
final class LensBinding {
    static boolean isPair(Method create, Method talk) {
        for (Constructor<?> constructor : create.getDeclaringClass().getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length != 4) continue;
            Method[] pair = select(java.util.List.of(create, talk), p[1]);
            if (pair != null && pair[0].equals(create) && pair[1].equals(talk)) return true;
        }
        return false;
    }
    static Method[] select(Collection<Method> candidates, Class<?> gate) {
        Method create = null, talk = null;
        for (Method method : candidates) {
            if (!isPredicate(method)) continue;
            Class<?> owner = method.getDeclaringClass();
            for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 4 && p[0].getName().equals("com.google.apps.tiktok.account.AccountId")
                        && p[1] == gate && p[2] == boolean.class && p[3] == long.class) {
                    if (create != null && !create.equals(method)) return null;
                    create = method;
                }
                if (isTalkConstructor(p) && hasModeGetter(owner)) {
                    if (talk != null && !talk.equals(method)) return null;
                    talk = method;
                }
            }
        }
        return create == null || talk == null || create.equals(talk) ? null : new Method[]{create, talk};
    }

    static boolean isPredicate(Method method) {
        int flags = method.getModifiers();
        return method.getParameterCount() == 0 && method.getReturnType() == boolean.class
                && !Modifier.isStatic(flags) && !Modifier.isAbstract(flags) && !Modifier.isNative(flags)
                && !method.isSynthetic();
    }

    private static boolean isTalkConstructor(Class<?>[] p) {
        // Inspected Talk layouts: 17.67 has two flags, 17.68 six, and 17.70 ten.
        if ((p.length != 6 && p.length != 10 && p.length != 14) || p[0].isPrimitive() || p[1].isPrimitive()
                || p[2].isPrimitive() || p[p.length - 1].isPrimitive()) return false;
        for (int i = 3; i < p.length - 1; i++) if (p[i] != boolean.class) return false;
        return true;
    }

    private static boolean hasModeGetter(Class<?> owner) {
        int count = 0;
        for (Method method : owner.getDeclaredMethods())
            if (method.getReturnType() == long.class && method.getParameterCount() == 0
                    && !Modifier.isStatic(method.getModifiers())) count++;
        return count == 1;
    }
    private LensBinding() {}
}
