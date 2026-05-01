package org.rctools.structure.ffm.schema;

import org.rctools.structure.ffm.annotation.KeyField;
import org.rctools.structure.ffm.annotation.KeyFieldType;

import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a {@link SchemaDescriptor} by reflecting over a target class.
 */
public final class SchemaBuilder {

    private SchemaBuilder() {}

    public static SchemaDescriptor build(Class<?> clazz, int elementCount) {
        List<Field> annotatedFields = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            KeyField ann = field.getAnnotation(KeyField.class);
            if (ann != null) {
                field.setAccessible(true);
                annotatedFields.add(field);
            }
        }
        if (annotatedFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "No @KeyField annotations found on " + clazz.getName());
        }
        annotatedFields.sort(Comparator.comparingInt(f -> {
            KeyField ann = f.getAnnotation(KeyField.class);
            return ann.index() >= 0 ? ann.index() : Integer.MAX_VALUE;
        }));

        List<SchemaDescriptor.FieldDescriptor> descriptors = new ArrayList<>();
        int segIdx = 0;
        for (Field field : annotatedFields) {
            KeyField ann = field.getAnnotation(KeyField.class);
            KeyFieldType resolved = resolveType(ann.type(), field.getType());
            int currentSegIdx = segIdx;
            switch (resolved) {
                case DOUBLE, DECIMAL_DOUBLE -> {
                    descriptors.add(new SchemaDescriptor.FieldDescriptor(
                            field.getName(), descriptors.size(), resolved,
                            currentSegIdx, ValueLayout.JAVA_DOUBLE));
                    segIdx++;
                }
                case LONG -> {
                    descriptors.add(new SchemaDescriptor.FieldDescriptor(
                            field.getName(), descriptors.size(), resolved,
                            currentSegIdx, ValueLayout.JAVA_LONG));
                    segIdx++;
                }
                case DECIMAL_TIGHT -> {
                    descriptors.add(new SchemaDescriptor.FieldDescriptor(
                            field.getName() + "$unscaled", descriptors.size(), resolved,
                            currentSegIdx, ValueLayout.JAVA_LONG));
                    segIdx++;
                    descriptors.add(new SchemaDescriptor.FieldDescriptor(
                            field.getName() + "$scale", descriptors.size(), resolved,
                            currentSegIdx, ValueLayout.JAVA_INT));
                    segIdx++;
                }
            }
        }
        return new SchemaDescriptor(List.copyOf(descriptors), elementCount);
    }

    private static KeyFieldType resolveType(KeyFieldType declared, Class<?> fieldType) {
        if (declared != KeyFieldType.AUTO) return declared;
        if (fieldType == double.class || fieldType == Double.class) return KeyFieldType.DOUBLE;
        if (fieldType == long.class || fieldType == Long.class) return KeyFieldType.LONG;
        if (fieldType == int.class || fieldType == Integer.class) return KeyFieldType.LONG;
        if (BigDecimal.class.isAssignableFrom(fieldType)) return KeyFieldType.DECIMAL_TIGHT;
        throw new IllegalArgumentException("Unsupported field type: " + fieldType.getName());
    }
}
