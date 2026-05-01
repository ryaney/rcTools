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
 *
 * <h3>Tier summary:</h3>
 * <ul>
 *   <li>heap*           — traditional Java heap objects</li>
 *   <li>soa*            — ValueReader with string field name lookup + BigDecimal rebuild</li>
 *   <li>soaRawDouble*   — direct Segment double access (no allocation)</li>
 *   <li>soaRawDecimal*  — raw long+int access (no BigDecimal allocation)</li>
 *   <li>soaBoth*        — combined raw double + raw decimal (optimal for multi-field traversal)</li>
 * </ul>
 *
 * <p>Segment field indices (from SchemaBuilder):</p>
 * <ul>
 *   <li>price (double)       → segment index 0</li>
 *   <li>quantity $unscaled   → segment index 1 (long)</li>
 *   <li>quantity $scale      → segment index 2 (int)</li>
 * </ul>
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

    // Segment field indices (from SchemaBuilder)
    private static final int SEG_PRICE    = 0;  // double
    private static final int SEG_UNSCALED = 1;  // long
    private static final int SEG_SCALE    = 2;  // int

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

    // ═══════════════════════════════════════════════════
    // Heap traversal (baseline)
    // ═══════════════════════════════════════════════════

    @Benchmark public void heapSumPrice(Blackhole bh) {
        double s = 0;
        for (TradeRecord r : heapObjects) s += r.price;
        bh.consume(s);
    }

    @Benchmark public void heapSumQty(Blackhole bh) {
        double s = 0;
        for (TradeRecord r : heapObjects) s += r.quantity.doubleValue();
        bh.consume(s);
    }

    @Benchmark public void heapSumBoth(Blackhole bh) {
        double ps = 0, qs = 0;
        for (TradeRecord r : heapObjects) {
            ps += r.price;
            qs += r.quantity.doubleValue();
        }
        bh.consume(ps + qs);
    }

    // ═══════════════════════════════════════════════════
    // SoA — ValueReader (string field name lookup, BigDecimal rebuild)
    // ═══════════════════════════════════════════════════

    @Benchmark public void soaSumPrice(Blackhole bh) {
        double[] s = {0};
        iterator.forEach(r -> s[0] += r.getDouble("price"));
        bh.consume(s[0]);
    }

    @Benchmark public void soaSumQty(Blackhole bh) {
        double[] s = {0};
        iterator.forEach(r -> s[0] += r.getBigDecimal("quantity").doubleValue());
        bh.consume(s[0]);
    }

    @Benchmark public void soaSumBoth(Blackhole bh) {
        double[] ps = {0}, qs = {0};
        iterator.forEach(r -> {
            ps[0] += r.getDouble("price");
            qs[0] += r.getBigDecimal("quantity").doubleValue();
        });
        bh.consume(ps[0] + qs[0]);
    }

    // ═══════════════════════════════════════════════════
    // SoA — Raw double (direct Segment, no allocation)
    // ═══════════════════════════════════════════════════

    @Benchmark public void soaRawDoubleSumPrice(Blackhole bh) {
        double[] s = {0};
        iterator.forEachDouble(SEG_PRICE, d -> s[0] += d);
        bh.consume(s[0]);
    }

    // ═══════════════════════════════════════════════════
    // SoA — Raw decimal with Math.pow (before optimization)
    // ═══════════════════════════════════════════════════

    @Benchmark public void soaRawDecimalSumQty_pow(Blackhole bh) {
        double[] s = {0};
        iterator.forEachDecimalRaw(SEG_UNSCALED, SEG_SCALE,
                (unscaled, scale) -> s[0] += unscaled * Math.pow(10, -scale));
        bh.consume(s[0]);
    }

    // ═══════════════════════════════════════════════════
    // SoA — Decimal→double via precomputed lookup table
    // ═══════════════════════════════════════════════════

    @Benchmark public void soaDecimalAsDoubleSumQty(Blackhole bh) {
        double[] s = {0};
        iterator.forEachDecimalAsDouble(SEG_UNSCALED, SEG_SCALE, d -> s[0] += d);
        bh.consume(s[0]);
    }

    // ═══════════════════════════════════════════════════
    // SoA — Integer-only aggregation (sum unscaled, divide once)
    // ═══════════════════════════════════════════════════

    @Benchmark public void soaSumDecimalUnscaled(Blackhole bh) {
        long[] result = iterator.sumDecimalWithScale(SEG_UNSCALED, SEG_SCALE);
        double sum = result[0] * SoAIterator.SCALE_TO_DOUBLE[(int) result[1]];
        bh.consume(sum);
    }

    // ═══════════════════════════════════════════════════
    // SoA — Uniform-scale decimal (scale read once, skip per-element)
    // ═══════════════════════════════════════════════════

    @Benchmark public void soaDecimalUniformScaleSumQty(Blackhole bh) {
        double s = iterator.sumDecimalUniformScale(SEG_UNSCALED, SEG_SCALE);
        bh.consume(s);
    }

    @Benchmark public void soaDecimalUniformScaleForEach(Blackhole bh) {
        double[] s = {0};
        iterator.forEachDecimalUniformScale(SEG_UNSCALED, SEG_SCALE, d -> s[0] += d);
        bh.consume(s[0]);
    }

    // ═══════════════════════════════════════════════════
    // SoA — Combined: double + uniform-scale decimal (optimal)
    // ═══════════════════════════════════════════════════

    @Benchmark public void soaRawBothUniformScale(Blackhole bh) {
        double[] ps = {0}, qs = {0};
        // Read scale once from element 0
        int scale = storage.getInt(SEG_SCALE, 0);
        double[] lookup = SoAIterator.SCALE_TO_DOUBLE;
        double factor = (scale < lookup.length) ? lookup[scale] : Math.pow(10, -scale);
        int n = iterator.size();
        for (int i = 0; i < n; i++) {
            ps[0] += storage.getDouble(SEG_PRICE, i);
            qs[0] += storage.getLong(SEG_UNSCALED, i) * factor;
        }
        bh.consume(ps[0] + qs[0]);
    }

    // ═══════════════════════════════════════════════════
    // Write overhead
    // ═══════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════
    // Business object
    // ═══════════════════════════════════════════════════

    public static class TradeRecord {
        @KeyField(index = 0, type = KeyFieldType.DOUBLE)
        public double price;

        @KeyField(index = 1, type = KeyFieldType.DECIMAL_TIGHT)
        public BigDecimal quantity;

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
