package org.demo.concurrent.structure.map;

import lombok.Builder;
import lombok.Data;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author: ryan_scy@126.com
 * @date: 2025-01-27
 * @time: 17:45
 * @desc:
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class HashMapConcurrentBenchmark {

    private Map<Integer, Balance> map;

    @Setup
    public void setup() {
        map = new HashMap<>();
    }

    @Benchmark
    @Group("read_write")
    @GroupThreads(1)
    public void write() {
        for (int i = 0; i < 100; i++) {
            map.put(i, Balance.builder().coinId(i).balance(BigDecimal.ONE).build());
        }
    }

    @Benchmark
    @Group("read_write")
    @GroupThreads(3)
    public void read() {
        for (int i = 0; i < 100; i++) {
            map.get(i);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(HashMapConcurrentBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }

    @Data
    @Builder
    public static class Balance implements ISequence {

        @Builder.Default
        private long sequence = 0L;
        private BigDecimal balance;
        private int coinId;

        public Balance update(BigDecimal balance) {
            this.balance = balance;
            return this;
        }

        @Override
        public void updateSequence(long sequence) {
            this.sequence = sequence;
        }
    }
}