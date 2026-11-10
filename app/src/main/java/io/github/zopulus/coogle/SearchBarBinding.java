package io.github.zopulus.coogle;

import android.content.Context;
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
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.FieldUsingType;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.wrap.DexField;
import org.luckypray.dexkit.wrap.DexMethod;

/** Obfuscated Launcher 17.5 features: four hooks, three flags and QSB callers. */
final class SearchBarBinding {
    final Method init, query, breeno, search;
    final Field exportFlag, breenoFlag, searchFlag;
    final List<Method> callers;

    private SearchBarBinding(List<Method> methods, List<Field> fields) {
        init = methods.get(0); query = methods.get(1);
        breeno = methods.get(2); search = methods.get(3);
        exportFlag = fields.get(0); breenoFlag = fields.get(1); searchFlag = fields.get(2);
        callers = List.copyOf(methods.subList(4, methods.size()));
    }

    static SearchBarBinding parse(ClassLoader loader, String saved) throws ReflectiveOperationException {
        String[] descriptors = saved.split("\n");
        if (descriptors.length < 8 || descriptors.length > 23) return null;
        List<Method> methods = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        for (int i = 0; i < descriptors.length; i++) {
            if (i >= 4 && i < 7) fields.add(new DexField(descriptors[i]).getFieldInstance(loader));
            else methods.add(new DexMethod(descriptors[i]).getMethodInstance(loader));
        }
        Class<?> owner = methods.get(0).getDeclaringClass();
        for (int i = 0; i < 4; i++) {
            Method method = methods.get(i);
            if (method.getDeclaringClass() != owner || !Modifier.isStatic(method.getModifiers())
                    || Modifier.isNative(method.getModifiers())
                    || method.getReturnType() != (i == 1 ? boolean.class : void.class)
                    || !List.of(method.getParameterTypes()).equals(i == 1 ? List.of() : List.of(Context.class))) return null;
        }
        if (methods.subList(0, 4).stream().distinct().count() != 4 || fields.stream().distinct().count() != 3) return null;
        for (Field field : fields) {
            if (field.getDeclaringClass() != owner || field.getType() != boolean.class
                    || !Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) return null;
            field.setAccessible(true);
        }
        for (Method caller : methods.subList(4, methods.size())) {
            if (Modifier.isAbstract(caller.getModifiers()) || Modifier.isNative(caller.getModifiers())) return null;
        }
        return new SearchBarBinding(methods, fields);
    }

    String serialize() {
        List<String> descriptors = new ArrayList<>();
        for (Method method : List.of(init, query, breeno, search)) descriptors.add(new DexMethod(method).serialize());
        for (Field field : List.of(exportFlag, breenoFlag, searchFlag)) descriptors.add(new DexField(field).serialize());
        for (Method caller : callers) descriptors.add(new DexMethod(caller).serialize());
        return String.join("\n", descriptors);
    }

    static SearchBarBinding find(DexKitBridge bridge, ClassLoader loader) throws ReflectiveOperationException {
        var initializers = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("void").paramTypes("android.content.Context")
                .usingStrings(List.of("initFeature. The pm is null!", "IS_EXP_VERSION:"))));
        if (initializers.size() != 1) return null;
        MethodData init = initializers.get(0);
        var breeno = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(init.getClassName()).returnType("void").paramTypes("android.content.Context")
                .usingStrings(List.of("initSupportBreenoEntryFeature: Export, sIsSupportBreenoEntry = false"))));
        var search = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .declaredClass(init.getClassName()).returnType("void").paramTypes("android.content.Context")
                .usingStrings(List.of("initSupportSearchEntryFeature: search entry disable, sIsSupportSearchEntry = false"))));
        var hosts = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .returnType("void").usingStrings(List.of("dealBottomSearchHostUpdate: qsb is enabled?"))));
        if (breeno.size() != 1 || search.size() != 1 || hosts.size() != 1) return null;
        // The native export host keeps widget availability checks; only its feature
        // getter is overridden. Never force the whole host eligibility method true.
        Set<MethodData> queries = new LinkedHashSet<>();
        for (MethodData method : hosts.get(0).getDeclaredClass().getMethods()) {
            if (!method.getReturnTypeName().equals("boolean")
                    || !method.getParamTypeNames().equals(List.of("android.content.Context"))) continue;
            for (MethodData invoked : method.getInvokes()) {
                if (invoked.getClassName().equals(init.getClassName()) && invoked.getParamCount() == 0
                        && invoked.getReturnTypeName().equals("boolean") && Modifier.isStatic(invoked.getModifiers()))
                    queries.add(invoked);
            }
        }
        Set<FieldData> export = fields(init, FieldUsingType.Write);
        export.retainAll(fields(breeno.get(0), FieldUsingType.Read));
        export.retainAll(fields(search.get(0), FieldUsingType.Read));
        Set<FieldData> breenoFlags = fields(breeno.get(0), FieldUsingType.Write);
        Set<FieldData> searchFlags = fields(search.get(0), FieldUsingType.Write);
        if (queries.size() != 1 || export.size() != 1 || breenoFlags.size() != 1 || searchFlags.size() != 1) return null;
        MethodData query = queries.iterator().next();
        List<String> descriptors = new ArrayList<>();
        for (MethodData method : List.of(init, query, breeno.get(0), search.get(0))) descriptors.add(method.getDescriptor());
        for (Set<FieldData> flags : List.of(export, breenoFlags, searchFlags)) descriptors.add(flags.iterator().next().getDescriptor());
        for (MethodData caller : query.getCallers()) {
            if (caller.isMethod() && !Modifier.isAbstract(caller.getModifiers()) && !Modifier.isNative(caller.getModifiers()))
                descriptors.add(caller.getDescriptor());
        }
        return parse(loader, String.join("\n", descriptors));
    }

    private static Set<FieldData> fields(MethodData method, FieldUsingType access) {
        Set<FieldData> result = new LinkedHashSet<>();
        for (var usage : method.getUsingFields()) {
            FieldData field = usage.getField();
            if (usage.getUsingType() == access && field.getClassName().equals(method.getClassName())
                    && field.getTypeName().equals("boolean") && Modifier.isStatic(field.getModifiers())
                    && !Modifier.isFinal(field.getModifiers())) result.add(field);
        }
        return result;
    }
}
