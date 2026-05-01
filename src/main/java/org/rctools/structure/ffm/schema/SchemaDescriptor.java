package org.rctools.structure.ffm.schema;

import org.rctools.structure.ffm.annotation.KeyFieldType;

import java.lang.foreign.ValueLayout;
import java.util.List;

/**
 * Immutable schema descriptor built from {@code @KeyField} annotations.
 */
public record SchemaDescriptor(
        List<FieldDescriptor> fields,
        int elementCount
) {

    public record FieldDescriptor(
            String name,
            int index,
            KeyFieldType type,
            int segmentIndex,
            ValueLayout valueLayout
    ) {}
}
