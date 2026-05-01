package org.rctools.structure.ffm.sync;

import org.rctools.structure.ffm.segment.SoAStorage;
import org.rctools.structure.ffm.segment.SoAWriter;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

/**
 * Low-level Cache-Line version marker utilities.
 *
 * <p>Each element has a 1-byte marker: 0x00=STABLE, 0x01=WRITING.
 * With 64-byte alignment, one Cache Line holds 64 contiguous markers.
 */
public final class CacheLineVersionMarker {

    private CacheLineVersionMarker() {}

    /** Mark an element as WRITING (pre-write). */
    public static void markWriting(SoAStorage storage, int idx) {
        storage.setVersion(idx, SoAWriter.MARK_WRITING);
        VarHandle.fullFence();
    }

    /** Mark an element as STABLE (post-write). */
    public static void markStable(SoAStorage storage, int idx) {
        VarHandle.fullFence();
        storage.setVersion(idx, SoAWriter.MARK_STABLE);
    }

    /** Spin-wait until marker is STABLE. */
    public static void awaitStable(SoAStorage storage, int idx) {
        MemorySegment seg = storage.versionSegment();
        long off = (long) idx;
        while (seg.get(ValueLayout.JAVA_BYTE, off) == SoAWriter.MARK_WRITING)
            Thread.onSpinWait();
    }
}
