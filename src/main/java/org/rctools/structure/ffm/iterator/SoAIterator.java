package org.rctools.structure.ffm.iterator;

import org.rctools.structure.ffm.annotation.KeyFieldType;
import org.rctools.structure.ffm.schema.SchemaDescriptor;
import org.rctools.structure.ffm.schema.SchemaDescriptor.FieldDescriptor;
import org.rctools.structure.ffm.segment.SoAStorage;
import org.rctools.structure.ffm.segment.SoAWriter;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Lock-free SoA iterator with Cache-Line version marker spin-on-read.
 */
public class SoAIterator {

    private final SoAStorage storage;
    private final SchemaDescriptor schema;

    public SoAIterator(SoAStorage storage) {
        this.storage = storage;
        this.schema = storage.schema();
    }

    public int size() { return storage.elementCount(); }

    /** Iterate all elements with a ValueReader callback. */
    public void forEach(Consumer<ValueReader> consumer) {
        ValueReaderImpl reader = new ValueReaderImpl();
        for (int i = 0; i < storage.elementCount(); i++) {
            awaitStable(i);
            reader.idx = i;
            consumer.accept(reader);
        }
    }

    /** Fastest path: read one double field for all elements. */
    public void forEachDouble(int fieldIdx, Consumer<Double> consumer) {
        for (int i = 0; i < storage.elementCount(); i++) {
            awaitStable(i);
            consumer.accept(storage.getDouble(fieldIdx, i));
        }
    }

    private void awaitStable(int i) {
        while (storage.getVersion(i) == SoAWriter.MARK_WRITING) Thread.onSpinWait();
    }

    public interface ValueReader {
        double getDouble(String fieldName);
        long getLong(String fieldName);
        BigDecimal getBigDecimal(String fieldName);
    }

    private class ValueReaderImpl implements ValueReader {
        int idx;

        @Override public double getDouble(String name) {
            for (FieldDescriptor fd : schema.fields())
                if (fd.name().equals(name)) return storage.getDouble(fd.segmentIndex(), idx);
            return 0;
        }
        @Override public long getLong(String name) {
            for (FieldDescriptor fd : schema.fields())
                if (fd.name().equals(name)) return storage.getLong(fd.segmentIndex(), idx);
            return 0;
        }
        @Override public BigDecimal getBigDecimal(String name) {
            for (FieldDescriptor fd : schema.fields()) {
                if (fd.name().equals(name + "$unscaled") && fd.type() == KeyFieldType.DECIMAL_TIGHT) {
                    return BigDecimal.valueOf(
                            storage.getLong(fd.segmentIndex(), idx),
                            storage.getInt(fd.segmentIndex() + 1, idx));
                }
            }
            return BigDecimal.ZERO;
        }
    }
}
