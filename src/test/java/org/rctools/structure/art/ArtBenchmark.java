package org.rctools.structure.art;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * ART 数据结构性能基准测试
 * <p>
 * 对比以下数据结构的性能：
 * - ArtTree: Adaptive Radix Tree
 * - TreeMap: 红黑树实现，基于比较器排序
 * - HashMap: 哈希表实现
 * - ConcurrentSkipListMap: 跳表实现
 * <p>
 * 测试场景：
 * - put: 插入操作
 * - get: 查找操作
 * - range: 范围查询
 * - prefix: 前缀查询
 * - iterate: 遍历操作
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ArtBenchmark {

    // 测试数据量
    private static final int SMALL_SIZE = 100;
    private static final int MEDIUM_SIZE = 1_000;
    private static final int LARGE_SIZE = 10_000;
    private static final int VERY_LARGE_SIZE = 100_000;

    @Param({"100", "1000", "10000"})
    private int size;

    // 随机数据
    private List<String> randomKeys;
    private List<String> orderedKeys;
    private List<String> prefixKeys;

    // 数据结构实例
    private ArtTree<Integer> artTree;
    private TreeMap<String, Integer> treeMap;
    private HashMap<String, Integer> hashMap;
    private ConcurrentSkipListMap<String, Integer> skipListMap;

    // 前缀查询用的前缀
    private String searchPrefix;
    private String rangeStart;
    private String rangeEnd;

    @Setup(Level.Trial)
    public void setup() {
        // 生成测试数据
        randomKeys = generateRandomKeys(size);
        orderedKeys = generateOrderedKeys(size);
        prefixKeys = generatePrefixKeys(size, "user_");

        // 设置查询参数
        searchPrefix = "user_";
        rangeStart = orderedKeys.get(size / 4);
        rangeEnd = orderedKeys.get(3 * size / 4);

        // 初始化数据结构
        initDataStructures();
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        // 每次调用前重新初始化，避免热数据影响
        initDataStructures();
    }

    private void initDataStructures() {
        artTree = new ArtTree<>();
        treeMap = new TreeMap<>();
        hashMap = new HashMap<>();
        skipListMap = new ConcurrentSkipListMap<>();

        // 填充数据
        for (String key : orderedKeys) {
            int value = key.hashCode();
            artTree.put(key, value);
            treeMap.put(key, value);
            hashMap.put(key, value);
            skipListMap.put(key, value);
        }
    }

    private List<String> generateRandomKeys(int size) {
        Random random = new Random(42);
        List<String> keys = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            keys.add(generateRandomString(random));
        }
        return keys;
    }

    private List<String> generateOrderedKeys(int size) {
        List<String> keys = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            keys.add(String.format("key_%08d", i));
        }
        return keys;
    }

    private List<String> generatePrefixKeys(int size, String prefix) {
        List<String> keys = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            keys.add(prefix + String.format("%06d", i));
        }
        return keys;
    }

    private String generateRandomString(Random random) {
        StringBuilder sb = new StringBuilder();
        int length = 8 + random.nextInt(8); // 8-16 字符
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    // ==================== Put 操作 ====================

    @Benchmark
    public void artTreePut(Blackhole bh) {
        ArtTree<Integer> art = new ArtTree<>();
        for (String key : orderedKeys) {
            art.put(key, key.hashCode());
        }
        bh.consume(art.size());
    }

    @Benchmark
    public void treeMapPut(Blackhole bh) {
        TreeMap<String, Integer> map = new TreeMap<>();
        for (String key : orderedKeys) {
            map.put(key, key.hashCode());
        }
        bh.consume(map.size());
    }

    @Benchmark
    public void hashMapPut(Blackhole bh) {
        HashMap<String, Integer> map = new HashMap<>();
        for (String key : orderedKeys) {
            map.put(key, key.hashCode());
        }
        bh.consume(map.size());
    }

    @Benchmark
    public void skipListMapPut(Blackhole bh) {
        ConcurrentSkipListMap<String, Integer> map = new ConcurrentSkipListMap<>();
        for (String key : orderedKeys) {
            map.put(key, key.hashCode());
        }
        bh.consume(map.size());
    }

    // ==================== Get 操作 ====================

    @Benchmark
    public void artTreeGet(Blackhole bh) {
        for (String key : orderedKeys) {
            bh.consume(artTree.get(key));
        }
    }

    @Benchmark
    public void treeMapGet(Blackhole bh) {
        for (String key : orderedKeys) {
            bh.consume(treeMap.get(key));
        }
    }

    @Benchmark
    public void hashMapGet(Blackhole bh) {
        for (String key : orderedKeys) {
            bh.consume(hashMap.get(key));
        }
    }

    @Benchmark
    public void skipListMapGet(Blackhole bh) {
        for (String key : orderedKeys) {
            bh.consume(skipListMap.get(key));
        }
    }

    // ==================== 范围查询 ====================

    @Benchmark
    public void artTreeRange(Blackhole bh) {
        List<ArtTree.Entry<Integer>> result = artTree.range(rangeStart, rangeEnd);
        bh.consume(result.size());
    }

    @Benchmark
    public void treeMapRange(Blackhole bh) {
        NavigableMap<String, Integer> result = treeMap.subMap(rangeStart, true, rangeEnd, true);
        bh.consume(result.size());
    }

    @Benchmark
    public void skipListMapRange(Blackhole bh) {
        NavigableMap<String, Integer> result = skipListMap.subMap(rangeStart, true, rangeEnd, true);
        bh.consume(result.size());
    }

    // ==================== 前缀查询 ====================

    @Benchmark
    public void artTreePrefix(Blackhole bh) {
        List<ArtTree.Entry<Integer>> result = artTree.prefixSearch(searchPrefix);
        bh.consume(result.size());
    }

    @Benchmark
    public void treeMapPrefix(Blackhole bh) {
        // TreeMap 没有原生前缀查询，使用范围查询模拟
        String start = searchPrefix;
        String end = searchPrefix.substring(0, searchPrefix.length() - 1) +
                (char) (searchPrefix.charAt(searchPrefix.length() - 1) + 1);
        NavigableMap<String, Integer> result = treeMap.subMap(start, true, end, false);
        bh.consume(result.size());
    }

    @Benchmark
    public void skipListMapPrefix(Blackhole bh) {
        // ConcurrentSkipListMap 没有原生前缀查询，使用范围查询模拟
        String start = searchPrefix;
        String end = searchPrefix.substring(0, searchPrefix.length() - 1) +
                (char) (searchPrefix.charAt(searchPrefix.length() - 1) + 1);
        NavigableMap<String, Integer> result = skipListMap.subMap(start, true, end, false);
        bh.consume(result.size());
    }

    // ==================== 遍历操作 ====================

    @Benchmark
    public void artTreeIterate(Blackhole bh) {
        int sum = 0;
        for (ArtTree.Entry<Integer> entry : artTree) {
            sum += entry.getValue();
        }
        bh.consume(sum);
    }

    @Benchmark
    public void treeMapIterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : treeMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
    }

    @Benchmark
    public void hashMapIterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : hashMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
    }

    @Benchmark
    public void skipListMapIterate(Blackhole bh) {
        int sum = 0;
        for (Map.Entry<String, Integer> entry : skipListMap.entrySet()) {
            sum += entry.getValue();
        }
        bh.consume(sum);
    }

    // ==================== KeySet 操作 ====================

    @Benchmark
    public void artTreeKeySet(Blackhole bh) {
        Set<String> keys = artTree.keySet();
        bh.consume(keys.size());
    }

    @Benchmark
    public void treeMapKeySet(Blackhole bh) {
        Set<String> keys = treeMap.keySet();
        bh.consume(keys.size());
    }

    @Benchmark
    public void hashMapKeySet(Blackhole bh) {
        Set<String> keys = hashMap.keySet();
        bh.consume(keys.size());
    }

    @Benchmark
    public void skipListMapKeySet(Blackhole bh) {
        Set<String> keys = skipListMap.keySet();
        bh.consume(keys.size());
    }

    // ==================== Size 操作 ====================

    @Benchmark
    public void artTreeSize(Blackhole bh) {
        bh.consume(artTree.size());
    }

    @Benchmark
    public void treeMapSize(Blackhole bh) {
        bh.consume(treeMap.size());
    }

    @Benchmark
    public void hashMapSize(Blackhole bh) {
        bh.consume(hashMap.size());
    }

    @Benchmark
    public void skipListMapSize(Blackhole bh) {
        bh.consume(skipListMap.size());
    }
}
