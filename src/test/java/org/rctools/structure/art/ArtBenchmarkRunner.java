package org.rctools.structure.art;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH 基准测试运行器
 */
public class ArtBenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ArtBenchmark.class.getSimpleName())
                .threads(1) // 单线程测试（因为 ART 非线程安全）
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .shouldFailOnError(true)
                .jvmArgs("-Xmx2g")
                .build();

        new Runner(opt).run();
    }
}
