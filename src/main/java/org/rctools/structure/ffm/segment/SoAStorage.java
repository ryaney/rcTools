package org.rctools.structure.ffm.segment;

import org.rctools.structure.ffm.schema.SchemaDescriptor;
import org.rctools.structure.ffm.schema.SchemaDescriptor.FieldDescriptor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * SoA MemorySegments: one data Segment per field + one version marker Segment.
 */
public final class SoAStorage implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment[] dataSegments;
    private final MemorySegment versionSegment;
    private final SchemaDescriptor schema;
    private volatile boolean closed;

    public SoAStorage(SchemaDescriptor schema) {
        this.arena = Arena.ofShared();
        this.schema = schema;
        int n = schema.elementCount();

        this.dataSegments = new MemorySegment[schema.fields().size()];
        for (int i = 0; i < dataSegments.length; i++) {
            FieldDescriptor fd = schema.fields().get(i);
            this.dataSegments[i] = arena.allocate(
                    (long) n * fd.valueLayout().byteSize(), 64);
        }
        this.versionSegment = arena.allocate(
                (long) n * ValueLayout.JAVA_BYTE.byteSize(), 64);
    }

    public MemorySegment dataSegment(int fieldIndex) { return dataSegments[fieldIndex]; }
    public MemorySegment versionSegment() { return versionSegment; }
    public int elementCount() { return schema.elementCount(); }
    public SchemaDescriptor schema() { return schema; }

    public double getDouble(int segIdx, int elemIdx) {
        return dataSegments[segIdx].get(ValueLayout.JAVA_DOUBLE, (long) elemIdx * 8);
    }
    public void setDouble(int segIdx, int elemIdx, double v) {
        dataSegments[segIdx].set(ValueLayout.JAVA_DOUBLE, (long) elemIdx * 8, v);
    }
    public long getLong(int segIdx, int elemIdx) {
        return dataSegments[segIdx].get(ValueLayout.JAVA_LONG, (long) elemIdx * 8);
    }
    public void setLong(int segIdx, int elemIdx, long v) {
        dataSegments[segIdx].set(ValueLayout.JAVA_LONG, (long) elemIdx * 8, v);
    }
    public int getInt(int segIdx, int elemIdx) {
        return dataSegments[segIdx].get(ValueLayout.JAVA_INT, (long) elemIdx * 4);
    }
    public void setInt(int segIdx, int elemIdx, int v) {
        dataSegments[segIdx].set(ValueLayout.JAVA_INT, (long) elemIdx * 4, v);
    }
    public byte getVersion(int idx) {
        return versionSegment.get(ValueLayout.JAVA_BYTE, (long) idx);
    }
    public void setVersion(int idx, byte v) {
        versionSegment.set(ValueLayout.JAVA_BYTE, (long) idx, v);
    }

    @Override
    public void close() {
        if (!closed) { closed = true; arena.close(); }
    }
}
