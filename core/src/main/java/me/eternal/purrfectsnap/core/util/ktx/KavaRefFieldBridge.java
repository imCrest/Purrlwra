package cock.crest.purrfectsnap.lite.core.util.ktx;

import com.highcapable.kavaref.KavaRef;
import com.highcapable.kavaref.condition.FieldCondition;
import com.highcapable.kavaref.resolver.FieldResolver;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import java.util.ArrayList;
import java.util.List;

public final class KavaRefFieldBridge {
    private KavaRefFieldBridge() {}

    private static FieldResolver<Object> resolveField(Object instance, String fieldName) {
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            @SuppressWarnings("unchecked")
            KavaRef.MemberScope<Object> scope = (KavaRef.MemberScope<Object>) KavaRef.resolveClass((Class<Object>) current);
            FieldResolver<Object> resolver = scope.firstFieldOrNull(new Function1<FieldCondition<Object>, Unit>() {
                @Override
                public Unit invoke(FieldCondition<Object> condition) {
                    condition.name(fieldName);
                    return Unit.INSTANCE;
                }
            });
            if (resolver != null) {
                return resolver.of(instance);
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Object getField(Object instance, String fieldName) throws NoSuchFieldException {
        FieldResolver<Object> resolver = resolveField(instance, fieldName);
        if (resolver == null) throw new NoSuchFieldException(instance.getClass().getName() + "#" + fieldName);
        return resolver.get();
    }

    public static void setField(Object instance, String fieldName, Object value) throws NoSuchFieldException {
        FieldResolver<Object> resolver = resolveField(instance, fieldName);
        if (resolver == null) throw new NoSuchFieldException(instance.getClass().getName() + "#" + fieldName);
        resolver.set(value);
    }

    public static Class<?> getFieldType(Object instance, String fieldName) throws NoSuchFieldException {
        FieldResolver<Object> resolver = resolveField(instance, fieldName);
        if (resolver == null) throw new NoSuchFieldException(instance.getClass().getName() + "#" + fieldName);
        return resolver.getSelf().getType();
    }

    public static String[] findFieldNamesByType(Object instance, Class<?> fieldType) {
        List<String> result = new ArrayList<>();
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            @SuppressWarnings("unchecked")
            KavaRef.MemberScope<Object> scope = (KavaRef.MemberScope<Object>) KavaRef.resolveClass((Class<Object>) current);
            List<FieldResolver<Object>> fields = scope.field(new Function1<FieldCondition<Object>, Unit>() {
                @Override
                public Unit invoke(FieldCondition<Object> condition) {
                    condition.type(fieldType);
                    return Unit.INSTANCE;
                }
            });
            for (FieldResolver<Object> field : fields) {
                result.add(field.getSelf().getName());
            }
            current = current.getSuperclass();
        }
        return result.toArray(new String[0]);
    }

    public static String[] getAllFieldNames(Object instance) {
        List<String> result = new ArrayList<>();
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            @SuppressWarnings("unchecked")
            KavaRef.MemberScope<Object> scope = (KavaRef.MemberScope<Object>) KavaRef.resolveClass((Class<Object>) current);
            List<FieldResolver<Object>> fields = scope.field(new Function1<FieldCondition<Object>, Unit>() {
                @Override
                public Unit invoke(FieldCondition<Object> condition) {
                    return Unit.INSTANCE;
                }
            });
            for (FieldResolver<Object> field : fields) {
                result.add(field.getSelf().getName());
            }
            current = current.getSuperclass();
        }
        return result.toArray(new String[0]);
    }

    public static Object getStaticField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        @SuppressWarnings("unchecked")
        KavaRef.MemberScope<Object> scope = (KavaRef.MemberScope<Object>) KavaRef.resolveClass((Class<Object>) clazz);
        FieldResolver<Object> resolver = scope.firstFieldOrNull(new Function1<FieldCondition<Object>, Unit>() {
            @Override
            public Unit invoke(FieldCondition<Object> condition) {
                condition.name(fieldName);
                return Unit.INSTANCE;
            }
        });
        if (resolver == null) throw new NoSuchFieldException(clazz.getName() + "#" + fieldName);
        return resolver.get();
    }

    public static Object findStaticFieldByType(Class<?> clazz, Class<?> fieldType) {
        @SuppressWarnings("unchecked")
        KavaRef.MemberScope<Object> scope = (KavaRef.MemberScope<Object>) KavaRef.resolveClass((Class<Object>) clazz);
        List<FieldResolver<Object>> fields = scope.field(new Function1<FieldCondition<Object>, Unit>() {
            @Override
            public Unit invoke(FieldCondition<Object> condition) {
                condition.type(fieldType);
                return Unit.INSTANCE;
            }
        });
        for (FieldResolver<Object> field : fields) {
            try {
                Object value = field.get();
                if (value != null && value.getClass() == fieldType) return value;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
