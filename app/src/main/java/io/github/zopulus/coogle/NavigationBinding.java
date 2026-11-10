package io.github.zopulus.coogle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.FieldUsingType;
import org.luckypray.dexkit.wrap.DexField;
import org.luckypray.dexkit.wrap.DexMethod;

/** Native gesture gates and live observers discovered from their actual dispatch paths. */
final class NavigationBinding {
    private static final String HANDLE = "com.android.launcher3.circlesearch.OplusHideNavHandleController$INSTANCE";
    final Method circle, hidden, supported, gemini;
    final GateBinding navigation;
    final List<Executable> callers;
    private final Field holder;
    private final List<Method> predicates;

    private NavigationBinding(List<Method> gates, Field holder, List<Method> predicates,
                              List<Executable> callers) throws ReflectiveOperationException {
        circle = gates.get(0); hidden = gates.get(1); supported = gates.get(2); gemini = gates.get(3);
        this.holder = holder; this.predicates = List.copyOf(predicates); this.callers = List.copyOf(callers);
        navigation = GateBinding.bind(holder, predicates);
    }

    static NavigationBinding parse(ClassLoader loader, String saved) throws ReflectiveOperationException {
        String[] lines = saved.split("\n");
        if (lines.length < 9) return null;
        List<Method> gates = new ArrayList<>(), predicates = new ArrayList<>();
        for (int i = 0; i < 4; i++) gates.add(new DexMethod(lines[i]).getMethodInstance(loader));
        Field holder = new DexField(lines[4]).getFieldInstance(loader);
        for (int i = 5; i < 8; i++) predicates.add(new DexMethod(lines[i]).getMethodInstance(loader));
        if (gates.stream().distinct().count() != 4) return null;
        for (Method gate : gates) {
            if (gate.getReturnType() != boolean.class || gate.getParameterCount() != 0
                    || !Modifier.isStatic(gate.getModifiers()) || Modifier.isNative(gate.getModifiers())) return null;
        }
        Class<?> features = gates.get(0).getDeclaringClass();
        if (gates.get(1).getDeclaringClass() != features || gates.get(3).getDeclaringClass() != features
                || !gates.get(2).getDeclaringClass().getName().equals(HANDLE)
                || !gates.get(2).getName().equals("isSupportCircleToSearchHandle")) return null;
        List<Executable> callers = new ArrayList<>();
        for (int i = 8; i < lines.length; i++) {
            DexMethod descriptor = new DexMethod(lines[i]);
            if (descriptor.isStaticInitializer()) return null;
            Executable caller = descriptor.isConstructor() ? descriptor.getConstructorInstance(loader)
                    : descriptor.getMethodInstance(loader);
            if (Modifier.isAbstract(caller.getModifiers()) || Modifier.isNative(caller.getModifiers())) return null;
            callers.add(caller);
        }
        return new NavigationBinding(gates, holder, predicates, callers);
    }

    String serialize() {
        List<String> lines = new ArrayList<>();
        for (Method gate : List.of(circle, hidden, supported, gemini)) lines.add(new DexMethod(gate).serialize());
        lines.add(new DexField(holder).serialize());
        for (Method predicate : predicates) lines.add(new DexMethod(predicate).serialize());
        for (Executable caller : callers) lines.add(caller instanceof Constructor<?> constructor
                ? new DexMethod(constructor).serialize() : new DexMethod((Method) caller).serialize());
        return String.join("\n", lines);
    }

    static NavigationBinding find(DexKitBridge bridge, ClassLoader loader) throws ReflectiveOperationException {
        var handles = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(HANDLE).name("isSupportCircleToSearchHandle").returnType("boolean").paramCount(0)));
        var dispatches = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("void").paramTypes("int", "android.content.Context")
                .usingStrings(List.of("startCircleToSearch, type: ", " isRecentsAnimRealFinish: "))));
        var observers = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("int").paramTypes("android.content.Context")
                .usingStrings(List.of("circle_to_search_corner_assist_enable_navi"))));
        if (handles.size() != 1 || dispatches.size() != 1 || observers.size() != 1) return null;
        MethodData supported = handles.get(0);
        List<MethodData> hidden = booleanInvokes(supported, true);
        List<MethodData> predicates = booleanInvokes(supported, false);
        if (hidden.size() != 1 || predicates.size() != 3) return null;
        String features = hidden.get(0).getClassName(), navigation = predicates.get(0).getClassName();
        if (predicates.stream().anyMatch(method -> !method.getClassName().equals(navigation))) return null;
        List<MethodData> circle = booleanInvokes(dispatches.get(0), true).stream()
                .filter(method -> method.getClassName().equals(features)).toList();
        List<MethodData> gemini = booleanInvokes(observers.get(0), true).stream()
                .filter(method -> method.getClassName().equals(features)).toList();
        if (circle.size() != 1 || gemini.size() != 1) return null;
        Set<FieldData> holders = new LinkedHashSet<>();
        for (var usage : supported.getUsingFields()) {
            FieldData field = usage.getField();
            if (usage.getUsingType() == FieldUsingType.Read && field.getClassName().equals(navigation)
                    && Modifier.isStatic(field.getModifiers())) holders.add(field);
        }
        if (holders.size() != 1) return null;
        List<MethodData> gates = List.of(circle.get(0), hidden.get(0), supported, gemini.get(0));
        List<String> descriptors = new ArrayList<>();
        for (MethodData gate : gates) descriptors.add(gate.getDescriptor());
        descriptors.add(holders.iterator().next().getDescriptor());
        for (MethodData predicate : predicates) descriptors.add(predicate.getDescriptor());
        Set<String> callers = new LinkedHashSet<>();
        for (MethodData gate : gates) {
            for (MethodData caller : gate.getCallers()) {
                // Constructors also initialize native support flags before onCreate.
                if ((caller.isMethod() || caller.isConstructor()) && !Modifier.isAbstract(caller.getModifiers())
                        && !Modifier.isNative(caller.getModifiers())) callers.add(caller.getDescriptor());
            }
        }
        descriptors.addAll(callers);
        return parse(loader, String.join("\n", descriptors));
    }

    private static List<MethodData> booleanInvokes(MethodData method, boolean isStatic) {
        return method.getInvokes().stream().filter(target -> target.getReturnTypeName().equals("boolean")
                && target.getParamCount() == 0 && Modifier.isStatic(target.getModifiers()) == isStatic).distinct().toList();
    }
}
