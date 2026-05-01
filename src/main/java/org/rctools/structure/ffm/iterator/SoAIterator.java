package org.rctools.structure.ffm.iterator;

import org.rctools.structure.ffm.annotation.KeyFieldType;
import org.rctools.structure.ffm.schema.SchemaDescriptor;
import org.rctools.structure.ffm.schema.SchemaDescriptor.FieldDescriptor;
import org.rctools.structure.ffm.segment.SoAStorage;
import org.rctools.structure.ffm.segment.SoAWriter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;

/**
 * Lock-free SoA iterator with Cache-Line version marker spin-on-read.
 *
 * <h3>Performance tiers:</h3>
 * <ol>
 *   <li>{@link #forEachDouble(int, DoubleConsumer)} — fastest, direct Segment double read</li>
 *   <li>{@link #forEachDecimalRaw(int, int, DecimalRawConsumer)} — raw long+int, no BigDecimal allocation</li>
 *   <li>{@link #forEachDecimalAsDouble(int, int, DoubleConsumer)} — long→double via precomputed lookup table</li>
 *   <li>{@link #sumDecimalUnscaled(int)} — integer-only aggregation, divide once at the end</li>
 *   <li>{@link #forEach(ValueReader)} — string field name lookup, rebuilds BigDecimal on demand</li>
 * </ol>
 */
public class SoAIterator {

    private final SoAStorage storage;
    private final SchemaDescriptor schema;
    private final Map<String, FieldDescriptor> fieldLookup;

    /**
     * Precomputed lookup table: SCALE_TO_DOUBLE[n] = 10^(-n) for n in [0, 18].
     * Avoids Math.pow() on every iteration.
     */
    public static final double[] SCALE_TO_DOUBLE = buildScaleTable();

    private static double[] buildScaleTable() {
        double[] t = new double[19];
        t[0] = 1.0;
        for (int i = 1; i < t.length; i++) t[i] = t[i - 1] * 0.1;
        return t;
    }

    public SoAIterator(SoAStorage storage) {
        this.storage = storage;
        this.schema = storage.schema();
        this.fieldLookup = new HashMap<>();
        for (FieldDescriptor fd : schema.fields()) {
            fieldLookup.put(fd.name(), fd);
        }
    }

    public int size() { return storage.elementCount(); }

    // ──────────────────────────────────────────────────────────────
    // Tier 1: Fastest — primitive double, direct Segment index access
    // ──────────────────────────────────────────────────────────────

    public void forEachDouble(int fieldIdx, DoubleConsumer consumer) {
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            consumer.accept(storage.getDouble(fieldIdx, i));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Tier 2: Raw decimal — long(unscaled) + int(scale), no allocation
    // ──────────────────────────────────────────────────────────────

    @FunctionalInterface
    public interface DecimalRawConsumer {
        void accept(long unscaled, int scale);
    }

    public void forEachDecimalRaw(int unscaledFieldIdx, int scaleFieldIdx,
                                  DecimalRawConsumer consumer) {
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            consumer.accept(
                    storage.getLong(unscaledFieldIdx, i),
                    storage.getInt(scaleFieldIdx, i));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Tier 3: Decimal→double via precomputed lookup (no Math.pow)
    // ──────────────────────────────────────────────────────────────

    /**
     * Iterate decimal field as double using precomputed 10^(-scale) lookup.
     * Replaces Math.pow(10, -scale) with a static table access.
     */
    public void forEachDecimalAsDouble(int unscaledFieldIdx, int scaleFieldIdx,
                                       DoubleConsumer consumer) {
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            long unscaled = storage.getLong(unscaledFieldIdx, i);
            int scale = storage.getInt(scaleFieldIdx, i);
            double factor = (scale < SCALE_TO_DOUBLE.length) ? SCALE_TO_DOUBLE[scale] : Math.pow(10, -scale);
            consumer.accept(unscaled * factor);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Tier 4: Uniform-scale decimal — read scale once, skip per-element scale read
    // ──────────────────────────────────────────────────────────────

    /**
     * Iterate decimal field as double, assuming uniform scale across all elements.
     * Reads scale once from element 0, then only reads unscaled per element.
     * Eliminates 1 Segment read per element vs forEachDecimalAsDouble.
     */
    public void forEachDecimalUniformScale(int unscaledFieldIdx, int scaleFieldIdx,
                                           DoubleConsumer consumer) {
        awaitStable(0);
        int scale = storage.getInt(scaleFieldIdx, 0);
        double factor = (scale < SCALE_TO_DOUBLE.length) ? SCALE_TO_DOUBLE[scale] : Math.pow(10, -scale);
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            consumer.accept(storage.getLong(unscaledFieldIdx, i) * factor);
        }
    }

    /**
     * Sum decimal field as double, assuming uniform scale.
     * Returns the sum directly as double (no intermediate BigDecimal).
     */
    public double sumDecimalUniformScale(int unscaledFieldIdx, int scaleFieldIdx) {
        awaitStable(0);
        int scale = storage.getInt(scaleFieldIdx, 0);
        double factor = (scale < SCALE_TO_DOUBLE.length) ? SCALE_TO_DOUBLE[scale] : Math.pow(10, -scale);
        double sum = 0;
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            sum += storage.getLong(unscaledFieldIdx, i) * factor;
        }
        return sum;
    }

    // ──────────────────────────────────────────────────────────────
    // Tier 5: Integer-only aggregation — sum unscaled, divide once
    // ──────────────────────────────────────────────────────────────

    /**
     * Sum all unscaled values as long, return the result.
     * Caller divides by 10^scale once at the end.
     * Requires all elements to share the same scale (common in practice).
     */
    public long sumDecimalUnscaled(int unscaledFieldIdx) {
        long sum = 0;
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            sum += storage.getLong(unscaledFieldIdx, i);
        }
        return sum;
    }

    /**
     * Sum all unscaled values and return the scale (assumes uniform scale).
     * Returns [sum, scale].
     */
    public long[] sumDecimalWithScale(int unscaledFieldIdx, int scaleFieldIdx) {
        long sum = 0;
        int scale = 0;
        boolean first = true;
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            sum += storage.getLong(unscaledFieldIdx, i);
            if (first) { scale = storage.getInt(scaleFieldIdx, i); first = false; }
        }
        return new long[]{sum, scale};
    }

    // ──────────────────────────────────────────────────────────────
    // Tier 5: Indexed long
    // ──────────────────────────────────────────────────────────────

    public void forEachLong(int fieldIdx, LongConsumer consumer) {
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            consumer.accept(storage.getLong(fieldIdx, i));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Tier 6: String-based ValueReader (backward compatible)
    // ──────────────────────────────────────────────────────────────

    public void forEach(ValueReaderConsumer consumer) {
        ValueReaderImpl reader = new ValueReaderImpl();
        for (int i = 0, n = storage.elementCount(); i < n; i++) {
            awaitStable(i);
            reader.idx = i;
            consumer.accept(reader);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Version marker spin
    // ──────────────────────────────────────────────────────────────

    private void awaitStable(int i) {
        if (storage.getVersion(i) == SoAWriter.MARK_STABLE) return;
        while (storage.getVersion(i) == SoAWriter.MARK_WRITING) Thread.onSpinWait();
    }

    // ──────────────────────────────────────────────────────────────
    // ValueReader interface (legacy)
    // ──────────────────────────────────────────────────────────────

    public interface ValueReader {
        double getDouble(String fieldName);
        long getLong(String fieldName);
        BigDecimal getBigDecimal(String fieldName);
    }

    @FunctionalInterface
    public interface ValueReaderConsumer {
        void accept(ValueReader reader);
    }

    private class ValueReaderImpl implements ValueReader {
        int idx;

        @Override public double getDouble(String name) {
            FieldDescriptor fd = fieldLookup.get(name);
            return fd != null ? storage.getDouble(fd.segmentIndex(), idx) : 0;
        }
        @Override public long getLong(String name) {
            FieldDescriptor fd = fieldLookup.get(name);
            return fd != null ? storage.getLong(fd.segmentIndex(), idx) : 0;
        }
        @Override public BigDecimal getBigDecimal(String name) {
            FieldDescriptor fd = fieldLookup.get(name + "$unscaled");
            if (fd != null && fd.type() == KeyFieldType.DECIMAL_TIGHT) {
                return BigDecimal.valueOf(
                        storage.getLong(fd.segmentIndex(), idx),
                        storage.getInt(fd.segmentIndex() + 1, idx));
            }
            return BigDecimal.ZERO;
        }
    }
}
