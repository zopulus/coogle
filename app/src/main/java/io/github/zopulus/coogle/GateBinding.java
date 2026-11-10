package io.github.zopulus.coogle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Bind only the inspected firmware's native observer predicates; do not cache their values. */
final class GateBinding {
    private final Field holder;
    private final Method get, gesture, hidden, circle;

    private GateBinding(Field holder, Method get, Method gesture, Method hidden, Method circle) {
        this.holder = holder;
        this.get = get;
        this.gesture = gesture;
        this.hidden = hidden;
        this.circle = circle;
    }

    static GateBinding bind(Class<?> navigation) throws ReflectiveOperationException {
        return bind(navigation.getDeclaredField("u0"), List.of(navigation.getDeclaredMethod("E"),
                navigation.getDeclaredMethod("w"), navigation.getDeclaredMethod("q")));
    }

    static GateBinding bind(Field holder, List<Method> predicates) throws ReflectiveOperationException {
        Class<?> navigation = holder.getDeclaringClass();
        if (!Modifier.isStatic(holder.getModifiers()) || predicates.size() != 3
                || predicates.stream().distinct().count() != 3) throw new NoSuchFieldException("Invalid native observer holder");
        for (Method method : predicates) {
            if (method.getDeclaringClass() != navigation || method.getParameterCount() != 0
                    || method.getReturnType() != boolean.class || Modifier.isStatic(method.getModifiers()))
                throw new NoSuchMethodException("Invalid native observer predicate");
            method.setAccessible(true);
        }
        holder.setAccessible(true);
        Method get = holder.getType().getMethod("get");
        if (Modifier.isStatic(get.getModifiers()) || (get.getReturnType() != Object.class
                && !navigation.isAssignableFrom(get.getReturnType()))) throw new NoSuchMethodException("Invalid observer accessor");
        get.setAccessible(true);
        return new GateBinding(holder, get, predicates.get(0), predicates.get(1), predicates.get(2));
    }

    boolean eligible() throws ReflectiveOperationException {
        Object navigation = get.invoke(holder.get(null));
        if (navigation == null) return false;
        return (boolean) gesture.invoke(navigation)
                && (boolean) hidden.invoke(navigation)
                && (boolean) circle.invoke(navigation);
    }
}
