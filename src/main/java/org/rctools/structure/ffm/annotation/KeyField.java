package org.rctools.structure.ffm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for SoA aggregation in the Panama MemorySegment layout.
 *
 * <p>Fields annotated with {@code @KeyField} will be extracted from business objects
 * during initialization and stored in contiguous MemorySegments for cache-friendly traversal.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface KeyField {

    /** Field index in the SoA layout (optional, defaults to declaration order). */
    int index() default -1;

    /** Storage type for this field (defaults to AUTO). */
    KeyFieldType type() default KeyFieldType.AUTO;
}
