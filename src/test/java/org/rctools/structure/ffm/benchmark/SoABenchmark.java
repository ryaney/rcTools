package org.rctools.structure.ffm.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.rctools.structure.ffm.annotation.KeyField;
import org.rctools.structure.ffm.annotation.KeyFieldType;
import org.rctools.structure.ffm.iterator.SoAIterator;
import org.rctools.structure.ffm.schema.SchemaBuilder;
import org.rctools.structure.ffm.schema.SchemaDescriptor;
import org.rctools.structure.ffm.segment.SoAStorage;
import org.rctools.structure.ffm.segment.SoAWriter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Panama SoA vs traditional heap traversal JMH benchmark.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SoABenchmark {

    @Param({"100000", "500000"})
    public int elementCount;

    private List<TradeRecord> heapObjects;
    private SoAStorage storage;
    private SoAWriter writer;
    private SoAIterator iterator;
    private final Random rng = new Random(42);

    @Setup(Level.Trial)
    public void setup() {
        heapObjects = new ArrayList<>(elementCount);
        for (int i = 0; i < elementCount; i++) {
            heapObjects.add(new TradeRecord(
                    rng.nextDouble() * 10000,
                    BigDecimal.valueOf(rng.nextDouble() * 1000)
                            .setScale(4, RoundingMode.HALF_UP)));
        }
        SchemaDescriptor schema = SchemaBuilder.build(TradeRecord.class, elementCount);
        storage = new SoAStorage(schema);
        writer = new SoAWriter(storage, TradeRecord.class);
        writer.bulkInitialize(heapObjects);
        iterator = new SoAIterator(storage);
    }

    @TearDown(Level.Trial)
    public void tearDown() { storage.close(); }

    // ── Heap ──
    @Benchmark public void heapSumPrice(Blackhole bh) {
        double s = 0; for (TradeRecord r : heapObjects) s += r.price; bh.consume(s);
    }
    @Benchmark public void heapSumQty(Blackhole bh) {
        double s = 0; for (TradeRecord r : heapObjects) s += r.quantity.doubleValue(); bh.consume(s);
    }
    @Benchmark public void heapSumBoth(Blackhole bh) {
        double ps = 0, qs = 0;
        for (TradeRecord r : heapObjects) { ps += r.price; qs += r.quantity.doubleValue(); }
        bh.consume(ps + qs);
    }

    // ── SoA ──
    @Benchmark public void soaSumPrice(Blackhole bh) {
        double[] s = {0}; iterator.forEach(r -> s[0] += r.getDouble("price")); bh.consume(s[0]);
    }
    @Benchmark public void soaSumQty(Blackhole bh) {
        double[] s = {0}; iterator.forEach(r -> s[0] += r.getBigDecimal("quantity").doubleValue());
        bh.consume(s[0]);
    }
    @Benchmark public void soaSumBoth(Blackhole bh) {
        double[] ps = {0}, qs = {0};
        iterator.forEach(r -> { ps[0] += r.getDouble("price");
            qs[0] += r.getBigDecimal("quantity").doubleValue(); });
        bh.consume(ps[0] + qs[0]);
    }
    @Benchmark public void soaRawDoubleSumPrice(Blackhole bh) {
        double[] s = {0}; iterator.forEachDouble(0, d -> s[0] += d); bh.consume(s[0]);
    }

    // ── Write ──
    @Benchmark public void heapUpdate() {
        TradeRecord r = heapObjects.get(elementCount / 2);
        r.price = rng.nextDouble() * 10000;
        r.quantity = BigDecimal.valueOf(rng.nextDouble() * 1000).setScale(4, RoundingMode.HALF_UP);
    }
    @Benchmark public void soaUpdate() {
        int i = elementCount / 2;
        TradeRecord r = heapObjects.get(i);
        r.price = rng.nextDouble() * 10000;
        r.quantity = BigDecimal.valueOf(rng.nextDouble() * 1000).setScale(4, RoundingMode.HALF_UP);
        writer.writeFromObject(i, r);
    }

    public static class TradeRecord {
        @KeyField(index = 0, type = KeyFieldType.DOUBLE) public double price;
        @KeyField(index = 1, type = KeyFieldType.DECIMAL_TIGHT) public BigDecimal quantity;
        @SuppressWarnings("unused") private long id = System.nanoTime();
        @SuppressWarnings("unused") private String symbol = "BTC-USDT-SWAP";
        @SuppressWarnings("unused") private java.time.Instant ts = java.time.Instant.now();
        @SuppressWarnings("unused") private byte[] pad = new byte[32];
        public TradeRecord() {}
        public TradeRecord(double p, BigDecimal q) { price = p; quantity = q; }
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(SoABenchmark.class.getSimpleName())
                .warmupIterations(3).measurementIterations(5).forks(1).build()).run();
    }
}
