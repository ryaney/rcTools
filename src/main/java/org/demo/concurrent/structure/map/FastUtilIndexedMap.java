package org.demo.concurrent.structure.map;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 高性能 Map：Fastutil 索引 + 原生数组（预扩容版）
 * <p>
 * 设计目标：高频遍历（每 500ms 一次），低频写入（每 28 秒一次），极低频删除
 * <p>
 * 线程模型：单线程写 + 多线程遍历
 * - put/remove：仅允许单线程调用，由调用方保证（不做并发写检测）
 * - forEach/forEachValue：多线程安全（基于 volatile 可见性）
 * - get/containsKey：不支持（抛 UnsupportedOperationException）
 * - containsValue：多线程安全（遍历 values 数组）
 * <p>
 * 并发安全机制：
 * - values/size 为 volatile，新增元素时 size++ 触发 happens-before
 * - 更新/删除为非 volatile 数组写，最迟在下次 size++（volatile写）后对读线程可见
 * - 预扩容 COW：size >= 60% 时触发，创建新数组后 volatile 替换
 * - 读线程先读 size 再读 values：若读到新 size，由 HB 保证必读到新 values，
 *   消除旧数组 + 新 size 导致的 AIOOBE 风险
 * <p>
 * 约束：
 * - 写操作必须单线程（调用方保证，不做运行时检测）
 * - get/containsKey 不支持
 *
 * @param <K> key 类型
 * @param <V> value 类型
 * @author ryan_scy@126.com
 */
public class FastUtilIndexedMap<K, V> extends AbstractMap<K, V> {

    private volatile Object2IntOpenHashMap<K> keyToIndex;
    private volatile Object[] values;
    private volatile int size;
    private volatile int removed;
    private volatile int expandThreshold;

    private final Function<V, K> keyExtractor;

    private static final float EXPAND_RATIO = 0.6f;
    private static final float LOAD_FACTOR = 0.75f;

    /**
     * @param capacity     预分配容量
     * @param keyExtractor 从 value 中提取 key 的函数
     */
    public FastUtilIndexedMap(int capacity, Function<V, K> keyExtractor) {
        int mapCapacity = (int) (capacity / LOAD_FACTOR) + 1;
        this.keyToIndex = new Object2IntOpenHashMap<>(mapCapacity, LOAD_FACTOR);
        this.keyToIndex.defaultReturnValue(-1);
        this.values = new Object[capacity];
        this.size = 0;
        this.expandThreshold = (int) (capacity * EXPAND_RATIO);
        this.keyExtractor = keyExtractor;
    }

    // ==================== 复合视图：多个 Map 作为一个 Map 遍历 ====================

    /**
     * 将多个 Map（通常是按 shard 分桶的 FastutilIndexedMap[]）包装为一个只读复合视图。
     * <p>
     * forEach / forEachValue / values() / size() 会依次遍历所有分片，
     * get 会遍历所有分片查找（O(shardCount)）。
     * put / remove 不支持，写操作应直接操作对应 shard 的 Map。
     * <p>
     * 线程安全性与底层 Map 一致：如果底层是 FastutilIndexedMap，则遍历多线程安全。
     */
    public static <K, V> Map<K, V> compositeView(Map<K, V>[] shards) {
        return new CompositeView<>(shards);
    }

    private static class CompositeView<K, V> extends AbstractMap<K, V> {
        private final Map<K, V>[] shards;

        CompositeView(Map<K, V>[] shards) {
            this.shards = shards;
        }

        @Override
        public void forEach(BiConsumer<? super K, ? super V> action) {
            for (Map<K, V> shard : shards) {
                if (shard != null) {
                    shard.forEach(action);
                }
            }
        }

        @SuppressWarnings("unchecked")
        public void forEachValue(Consumer<V> action) {
            for (Map<K, V> shard : shards) {
                if (shard instanceof FastUtilIndexedMap) {
                    ((FastUtilIndexedMap<K, V>) shard).forEachValue(action);
                } else if (shard != null) {
                    shard.values().forEach(action);
                }
            }
        }

        @Override
        public V get(Object key) {
            for (Map<K, V> shard : shards) {
                if (shard != null) {
                    V v = shard.get(key);
                    if (v != null) return v;
                }
            }
            return null;
        }

        @Override
        public boolean containsKey(Object key) {
            for (Map<K, V> shard : shards) {
                if (shard != null && shard.containsKey(key)) return true;
            }
            return false;
        }

        @Override
        public int size() {
            int total = 0;
            for (Map<K, V> shard : shards) {
                if (shard != null) total += shard.size();
            }
            return total;
        }

        @Override
        public boolean isEmpty() {
            for (Map<K, V> shard : shards) {
                if (shard != null && !shard.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public Collection<V> values() {
            List<V> result = new ArrayList<>(size());
            forEachValue(result::add);
            return result;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            Set<Entry<K, V>> result = new LinkedHashSet<>(size());
            forEach((k, v) -> result.add(new SimpleImmutableEntry<>(k, v)));
            return result;
        }

        @Override
        public V put(K key, V value) {
            throw new UnsupportedOperationException("Use shard-specific map for put");
        }

        @Override
        public V remove(Object key) {
            throw new UnsupportedOperationException("Use shard-specific map for remove");
        }
    }

    // ==================== 写操作（单线程，调用方保证不并发写入） ====================

    @Override
    public V put(K key, V value) {
        return doPut(key, value);
    }

    @SuppressWarnings("unchecked")
    private V doPut(K key, V value) {
        int existingIndex = keyToIndex.getInt(key);
        if (existingIndex >= 0) {
            V old = (V) values[existingIndex];
            values[existingIndex] = value;
            if (old == null) {
                removed--;
            }
            return old;
        }
        if (size >= expandThreshold) {
            return expandAndPut(key, value);
        }
        values[size] = value;
        keyToIndex.put(key, size);
        size++;  // volatile 写，flush 之前的 values[size]=value
        return null;
    }

    @SuppressWarnings("unchecked")
    private V expandAndPut(K key, V value) {
        int newArrayCapacity = size * 2;
        int newMapCapacity = (int) (newArrayCapacity / LOAD_FACTOR) + 1;

        Object[] newValues = new Object[newArrayCapacity];
        Object2IntOpenHashMap<K> newIndex = new Object2IntOpenHashMap<>(newMapCapacity, LOAD_FACTOR);
        newIndex.defaultReturnValue(-1);

        int newSize = 0;
        for (int i = 0; i < size; i++) {
            V v = (V) values[i];
            if (v != null) {
                newValues[newSize] = v;
                newIndex.put(keyExtractor.apply(v), newSize);
                newSize++;
            }
        }
        newValues[newSize] = value;
        newIndex.put(key, newSize);
        newSize++;

        // 先替换 keyToIndex（写线程自用），再替换 values 和 size（读线程可见）
        keyToIndex = newIndex;
        values = newValues;  // volatile 写
        removed = 0;         // 压缩后 tombstone 已清除
        size = newSize;      // volatile 写
        expandThreshold = (int) (newArrayCapacity * EXPAND_RATIO);
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        int index = keyToIndex.getInt(key);
        if (index < 0) return null;

        V old = (V) values[index];
        if (old == null) return null;  // 已是 tombstone，避免 removed 重复计数
        values[index] = null;  // 非 volatile 写，最迟在下次 size++ 后对读线程可见
        removed++;
        return old;
    }

    // ==================== 不支持随机读（线程模型仅支持遍历） ====================

    /**
     * 不支持。线程模型为单线程写 + 多线程遍历，不保证 get 的并发安全。
     * 写线程如需按 key 查找，直接使用 keyToIndex 内部索引（通过 put 路径）。
     */
    @Override
    public V get(Object key) {
        throw new UnsupportedOperationException("get not supported, use forEach to iterate");
    }

    /**
     * 不支持，原因同 get。
     */
    @Override
    public boolean containsKey(Object key) {
        throw new UnsupportedOperationException("containsKey not supported, use forEach to iterate");
    }

    @Override
    public boolean containsValue(Object value) {
        if (value == null) return false;
        int len = size;
        Object[] arr = values;
        for (int i = 0; i < len; i++) {
            if (value.equals(arr[i])) return true;
        }
        return false;
    }

    @Override
    public int size() {
        return size - removed;
    }

    @Override
    public boolean isEmpty() {
        return (size - removed) == 0;
    }

    // ==================== 高性能遍历（多线程安全） ====================

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super K, ? super V> action) {
        int len = size;         // volatile 读 ①，先读 size
        Object[] arr = values;  // volatile 读 ②，若 ① 读到新 size，② 必读到新 values（HB 保证）
        for (int i = 0; i < len; i++) {
            V v = (V) arr[i];
            if (v != null) {
                action.accept(keyExtractor.apply(v), v);
            }
        }
    }

    /**
     * 仅遍历 value（无需提取 key，更快）
     */
    @SuppressWarnings("unchecked")
    public void forEachValue(Consumer<V> action) {
        int len = size;         // volatile 读 ①
        Object[] arr = values;  // volatile 读 ②
        for (int i = 0; i < len; i++) {
            V v = (V) arr[i];
            if (v != null) {
                action.accept(v);
            }
        }
    }

    // ==================== entrySet / values / keySet ====================

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    @Override
    public Collection<V> values() {
        return new Values();
    }

    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    private class EntrySet extends AbstractSet<Entry<K, V>> {
        @Override
        public Iterator<Entry<K, V>> iterator() { return new EntryIterator(); }

        @Override
        public int size() { return FastUtilIndexedMap.this.size(); }
    }

    private class Values extends AbstractCollection<V> {
        @Override
        public Iterator<V> iterator() { return new ValueIterator(); }

        @Override
        public int size() { return FastUtilIndexedMap.this.size(); }
    }

    private class KeySet extends AbstractSet<K> {
        @Override
        public Iterator<K> iterator() { return new KeyIterator(); }

        @Override
        public int size() { return FastUtilIndexedMap.this.size(); }
    }

    @SuppressWarnings("unchecked")
    private class EntryIterator implements Iterator<Entry<K, V>> {
        private final int len = size;
        private final Object[] arr = values;
        private int cursor = 0;

        EntryIterator() { advanceToNext(); }

        private void advanceToNext() {
            while (cursor < len && arr[cursor] == null) cursor++;
        }

        @Override public boolean hasNext() { return cursor < len; }

        @Override
        public Entry<K, V> next() {
            if (cursor >= len) throw new NoSuchElementException();
            V v = (V) arr[cursor];
            K k = keyExtractor.apply(v);
            cursor++;
            advanceToNext();
            return new SimpleImmutableEntry<>(k, v);
        }
    }

    @SuppressWarnings("unchecked")
    private class ValueIterator implements Iterator<V> {
        private final int len = size;
        private final Object[] arr = values;
        private int cursor = 0;

        ValueIterator() { advanceToNext(); }

        private void advanceToNext() {
            while (cursor < len && arr[cursor] == null) cursor++;
        }

        @Override public boolean hasNext() { return cursor < len; }

        @Override
        public V next() {
            if (cursor >= len) throw new NoSuchElementException();
            V v = (V) arr[cursor];
            cursor++;
            advanceToNext();
            return v;
        }
    }

    @SuppressWarnings("unchecked")
    private class KeyIterator implements Iterator<K> {
        private final int len = size;
        private final Object[] arr = values;
        private int cursor = 0;

        KeyIterator() { advanceToNext(); }

        private void advanceToNext() {
            while (cursor < len && arr[cursor] == null) cursor++;
        }

        @Override public boolean hasNext() { return cursor < len; }

        @Override
        public K next() {
            if (cursor >= len) throw new NoSuchElementException();
            V v = (V) arr[cursor];
            cursor++;
            advanceToNext();
            return keyExtractor.apply(v);
        }
    }

    // ==================== 其他操作 ====================

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        m.forEach(this::put);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear not supported");
    }
}
