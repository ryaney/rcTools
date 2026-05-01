package org.rctools.structure.ffm.segment;

import org.rctools.structure.ffm.annotation.KeyFieldType;
import org.rctools.structure.ffm.schema.SchemaDescriptor;
import org.rctools.structure.ffm.schema.SchemaDescriptor.FieldDescriptor;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

/**
 * Single-thread writer: sync-dual-write with Cache-Line version marker.
 */
public class SoAWriter {

    public static final byte MARK_STABLE  = 0x00;
    public static final byte MARK_WRITING = 0x01;

    private final SoAStorage storage;
    private final SchemaDescriptor schema;
    private final Field[] sourceFields;

    public SoAWriter(SoAStorage storage, Class<?> sourceClass) {
        this.storage = storage;
        this.schema = storage.schema();
        this.sourceFields = new Field[schema.fields().size()];
        for (FieldDescriptor fd : schema.fields()) {
            try {
                Field f = sourceClass.getDeclaredField(stripSuffix(fd.name()));
                f.setAccessible(true);
                sourceFields[fd.index()] = f;
            } catch (NoSuchFieldException ignored) {}
        }
    }

    /** Write a single element: marks WRITING → writes data → marks STABLE. */
    public void writeFromObject(int idx, Object sourceObj) {
        storage.setVersion(idx, MARK_WRITING);
        VarHandle.fullFence();

        for (FieldDescriptor fd : schema.fields()) {
            if (fd.type() == KeyFieldType.DECIMAL_TIGHT && fd.name().endsWith("$scale")) continue;
            Field field = sourceFields[fd.index() / 2];
            if (field == null) continue;
            try {
                writeFieldValue(idx, fd, field.get(sourceObj));
            } catch (IllegalAccessException ignored) {}
        }

        VarHandle.fullFence();
        storage.setVersion(idx, MARK_STABLE);
    }

    /** Bulk-initialize from a list of business objects. */
    public void bulkInitialize(List<?> list) {
        for (int i = 0; i < list.size(); i++) writeFromObject(i, list.get(i));
    }

    private void writeFieldValue(int idx, FieldDescriptor fd, Object val) {
        switch (fd.type()) {
            case DOUBLE, DECIMAL_DOUBLE -> {
                double d = val == null ? 0.0
                        : (val instanceof BigDecimal bd ? bd.doubleValue() : (double) val);
                storage.setDouble(fd.segmentIndex(), idx, d);
            }
            case LONG ->
                storage.setLong(fd.segmentIndex(), idx, val == null ? 0L : (long) val);
            case DECIMAL_TIGHT -> {
                BigDecimal bd = val instanceof BigDecimal b ? b : BigDecimal.ZERO;
                storage.setLong(fd.segmentIndex(), idx, bd.unscaledValue().longValue());
                storage.setInt(fd.segmentIndex() + 1, idx, bd.scale());
            }
        }
    }

    private static String stripSuffix(String s) {
        int i = s.lastIndexOf('$');
        return i > 0 ? s.substring(0, i) : s;
    }
}
