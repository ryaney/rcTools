package org.rctools.structure.art;

import java.util.*;

/**
 * Adaptive Radix Tree (ART) 实现
 * <p>
 * ART 是一种基于前缀树的高效索引结构，具有以下特点：
 * 1. 自适应节点类型：根据子节点数量自动选择 Node4/Node16/Node48/Node256
 * 2. 路径压缩：存储公共前缀减少树的高度
 * 3. 高效内存使用：紧凑的节点表示，空间利用率高
 * <p>
 * 性能特点：
 * - 查找：O(k) 其中 k 是键长度
 * - 插入：O(k) 平均情况
 * - 删除：O(k) 平均情况
 * <p>
 * 线程安全：
 * - 本实现是非线程安全的
 * - 如需并发访问，请在外部使用同步机制
 * <p>
 * @param <V> 值类型
 */
public class ArtTree<V> implements Iterable<ArtTree.Entry<V>> {

    private Node<V> root;

    private static final int MAX_PREFIX_LEN = 10;

    public ArtTree() {
        root = new Node4<>();
    }

    /**
     * 插入键值对
     *
     * @param key   键（字符串）
     * @param value 值
     * @return 如果键已存在，返回旧值；否则返回 null
     */
    public V put(String key, V value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        InsertResult<V> result = new InsertResult<>();
        root = insert(root, bytes, 0, value, result);
        return result.oldValue;
    }

    /**
     * 插入键值对（使用字节数组作为键）
     *
     * @param key   键字节数组
     * @param value 值
     * @return 如果键已存在，返回旧值；否则返回 null
     */
    public V put(byte[] key, V value) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        InsertResult<V> result = new InsertResult<>();
        root = insert(root, key, 0, value, result);
        return result.oldValue;
    }

    private static class InsertResult<V> {
        V oldValue;
    }

    private Node<V> insert(Node<V> node, byte[] key, int depth, V value, InsertResult<V> result) {
        if (node == null) {
            node = new Node4<>();
        }

        // 检查前缀匹配
        int prefixMatchLen = checkPrefix(node, key, depth);

        // 情况1: 完全前缀不匹配，需要分裂
        if (prefixMatchLen < node.prefixLen) {
            // 需要分裂节点
            return splitAndInsert(node, key, depth, prefixMatchLen, value, result);
        }

        // 情况2: 前缀完全匹配
        depth += node.prefixLen;

        // 检查是否到达键的结尾
        if (depth == key.length) {
            result.oldValue = node.value;
            node.value = value;
            return node;
        }

        // 情况3: 前缀匹配但键未结束，继续向下
        byte nextByte = key[depth];
        Node<V> child = node.findChild(nextByte);

        if (child == null) {
            // 创建新的叶子节点
            Node<V> leaf = new Node4<>();
            // 叶子节点存储剩余的键作为前缀
            int remainingLen = key.length - depth - 1;
            if (remainingLen > 0) {
                leaf.prefix = new byte[remainingLen];
                System.arraycopy(key, depth + 1, leaf.prefix, 0, remainingLen);
                leaf.prefixLen = remainingLen;
            }
            leaf.value = value;
            node.addChild(nextByte, leaf);
            return node;
        } else {
            // 递归插入
            Node<V> newChild = insert(child, key, depth + 1, value, result);
            node.addChild(nextByte, newChild);
            return node;
        }
    }

    private int checkPrefix(Node<V> node, byte[] key, int depth) {
        int len = Math.min(node.prefixLen, key.length - depth);
        for (int i = 0; i < len; i++) {
            if (node.prefix[i] != key[depth + i]) {
                return i;
            }
        }
        return len;
    }

    private Node<V> splitAndInsert(Node<V> node, byte[] key, int depth, int splitPos, V value, InsertResult<V> result) {
        // 创建新节点作为父节点
        Node<V> newNode = new Node4<>();

        // 设置匹配部分为新前缀
        byte[] newPrefix = new byte[splitPos];
        System.arraycopy(node.prefix, 0, newPrefix, 0, splitPos);
        newNode.prefix = newPrefix;
        newNode.prefixLen = splitPos;

        // 处理旧节点的剩余部分
        byte branchKey = node.prefix[splitPos];
        int remainingPrefixLen = node.prefixLen - splitPos - 1;

        if (remainingPrefixLen == 0 && node.childCount == 0) {
            // 旧节点是纯叶子节点，直接作为子节点
            newNode.addChild(branchKey, node);
            node.prefix = new byte[0];
            node.prefixLen = 0;
            result.oldValue = node.value;
        } else {
            // 创建中间节点存储剩余前缀
            Node<V> intermediate = new Node4<>();
            byte[] remainingPrefix = new byte[remainingPrefixLen];
            System.arraycopy(node.prefix, splitPos + 1, remainingPrefix, 0, remainingPrefixLen);
            intermediate.prefix = remainingPrefix;
            intermediate.prefixLen = remainingPrefixLen;

            // 转移子节点和值
            intermediate.value = node.value;
            if (node.childCount > 0) {
                for (Node.ChildIterator<V> it = node.iterator(); it.hasNext(); ) {
                    intermediate.addChild(it.nextKey(), it.nextNode());
                }
            }
            newNode.addChild(branchKey, intermediate);
            result.oldValue = node.value;
        }

        // 插入新键
        depth += splitPos;
        if (depth < key.length) {
            byte nextByte = key[depth];
            Node<V> leaf = new Node4<>();
            int remainingLen = key.length - depth - 1;
            if (remainingLen > 0) {
                leaf.prefix = new byte[remainingLen];
                System.arraycopy(key, depth + 1, leaf.prefix, 0, remainingLen);
                leaf.prefixLen = remainingLen;
            }
            leaf.value = value;
            newNode.addChild(nextByte, leaf);
        } else {
            result.oldValue = newNode.value;
            newNode.value = value;
        }

        return newNode;
    }

    /**
     * 获取键对应的值
     *
     * @param key 键（字符串）
     * @return 值，如果不存在返回 null
     */
    public V get(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return get(bytes, 0, root);
    }

    /**
     * 获取键对应的值（使用字节数组）
     *
     * @param key 键字节数组
     * @return 值，如果不存在返回 null
     */
    public V get(byte[] key) {
        if (key == null || key.length == 0) {
            return null;
        }
        return get(key, 0, root);
    }

    private V get(byte[] key, int depth, Node<V> node) {
        if (node == null) {
            return null;
        }

        // 检查前缀匹配
        int prefixMatchLen = checkPrefix(node, key, depth);
        if (prefixMatchLen < node.prefixLen) {
            return null;
        }

        depth += node.prefixLen;

        if (depth == key.length) {
            return node.value;
        }

        byte nextByte = key[depth];
        Node<V> child = node.findChild(nextByte);
        return get(key, depth + 1, child);
    }

    /**
     * 检查键是否存在
     *
     * @param key 键
     * @return 如果存在返回 true
     */
    public boolean containsKey(String key) {
        return get(key) != null;
    }

    /**
     * 检查键是否存在（字节数组）
     *
     * @param key 键字节数组
     * @return 如果存在返回 true
     */
    public boolean containsKey(byte[] key) {
        return get(key) != null;
    }

    /**
     * 范围查询：查找所有在 startKey 和 endKey 之间的键
     *
     * @param startKey 起始键（包含）
     * @param endKey   结束键（包含）
     * @return 匹配的条目列表
     */
    public List<Entry<V>> range(String startKey, String endKey) {
        List<Entry<V>> result = new ArrayList<>();
        byte[] startBytes = startKey != null ? startKey.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        byte[] endBytes = endKey != null ? endKey.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        range(root, startBytes, endBytes, new byte[0], 0, result);
        return result;
    }

    private void range(Node<V> node, byte[] startKey, byte[] endKey, byte[] path, int depth, List<Entry<V>> result) {
        if (node == null) {
            return;
        }

        byte[] currentPath = new byte[path.length + node.prefixLen];
        System.arraycopy(path, 0, currentPath, 0, path.length);
        System.arraycopy(node.prefix, 0, currentPath, path.length, node.prefixLen);
        depth = currentPath.length;

        // 如果有值，检查是否在范围内
        if (node.value != null) {
            if (inRange(currentPath, startKey, endKey)) {
                result.add(new Entry<>(new String(currentPath, java.nio.charset.StandardCharsets.UTF_8), node.value));
            }
        }

        // 遍历子节点
        byte startByte = startKey != null && depth < startKey.length ? startKey[depth] : Byte.MIN_VALUE;
        byte endByte = endKey != null && depth < endKey.length ? endKey[depth] : Byte.MAX_VALUE;

        for (Node.ChildIterator<V> it = node.iterator(); it.hasNext(); ) {
            byte keyByte = it.nextKey();
            if ((keyByte & 0xFF) >= (startByte & 0xFF) && (keyByte & 0xFF) <= (endByte & 0xFF)) {
                byte[] newPath = Arrays.copyOf(currentPath, currentPath.length + 1);
                newPath[newPath.length - 1] = keyByte;
                range(it.nextNode(), startKey, endKey, newPath, depth + 1, result);
            }
        }
    }

    private boolean inRange(byte[] key, byte[] startKey, byte[] endKey) {
        if (startKey != null && compare(key, startKey) < 0) {
            return false;
        }
        if (endKey != null && compare(key, endKey) > 0) {
            return false;
        }
        return true;
    }

    private int compare(byte[] a, byte[] b) {
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int cmp = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (cmp != 0) {
                return cmp;
            }
        }
        return a.length - b.length;
    }

    /**
     * 删除键
     *
     * @param key 键
     * @return 被删除的值，如果不存在返回 null
     */
    public V remove(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        byte[] bytes = key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // TODO: 实现删除
        return null;
    }

    /**
     * 获取所有键
     *
     * @return 键集合
     */
    public Set<String> keySet() {
        Set<String> keys = new TreeSet<>();
        collectKeys(root, new byte[0], 0, keys);
        return keys;
    }

    private void collectKeys(Node<V> node, byte[] path, int depth, Set<String> keys) {
        if (node == null) {
            return;
        }

        byte[] currentPath = new byte[path.length + node.prefixLen];
        System.arraycopy(path, 0, currentPath, 0, path.length);
        System.arraycopy(node.prefix, 0, currentPath, path.length, node.prefixLen);
        depth = currentPath.length;

        if (node.value != null) {
            keys.add(new String(currentPath, java.nio.charset.StandardCharsets.UTF_8));
        }

        for (Node.ChildIterator<V> it = node.iterator(); it.hasNext(); ) {
            byte keyByte = it.nextKey();
            byte[] newPath = Arrays.copyOf(currentPath, currentPath.length + 1);
            newPath[newPath.length - 1] = keyByte;
            collectKeys(it.nextNode(), newPath, depth + 1, keys);
        }
    }

    /**
     * 获取所有值
     *
     * @return 值集合
     */
    public Collection<V> values() {
        List<V> values = new ArrayList<>();
        collectValues(root, values);
        return values;
    }

    private void collectValues(Node<V> node, List<V> values) {
        if (node == null) {
            return;
        }

        if (node.value != null) {
            values.add(node.value);
        }

        for (Node.ChildIterator<V> it = node.iterator(); it.hasNext(); ) {
            it.nextKey(); // 消耗 key
            collectValues(it.nextNode(), values);
        }
    }

    /**
     * 获取树的大小
     *
     * @return 键值对数量
     */
    public int size() {
        return countNodes(root);
    }

    private int countNodes(Node<V> node) {
        if (node == null) {
            return 0;
        }

        int count = node.value != null ? 1 : 0;
        for (Node.ChildIterator<V> it = node.iterator(); it.hasNext(); ) {
            it.nextKey(); // 消耗 key
            count += countNodes(it.nextNode());
        }
        return count;
    }

    /**
     * 检查是否为空
     *
     * @return 如果为空返回 true
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Iterator<Entry<V>> iterator() {
        List<Entry<V>> entries = new ArrayList<>();
        collectEntries(root, new byte[0], 0, entries);
        return entries.iterator();
    }

    private void collectEntries(Node<V> node, byte[] path, int depth, List<Entry<V>> entries) {
        if (node == null) {
            return;
        }

        byte[] currentPath = new byte[path.length + node.prefixLen];
        System.arraycopy(path, 0, currentPath, 0, path.length);
        System.arraycopy(node.prefix, 0, currentPath, path.length, node.prefixLen);
        depth = currentPath.length;

        if (node.value != null) {
            entries.add(new Entry<>(new String(currentPath, java.nio.charset.StandardCharsets.UTF_8), node.value));
        }

        for (Node.ChildIterator<V> it = node.iterator(); it.hasNext(); ) {
            byte keyByte = it.nextKey();
            byte[] newPath = Arrays.copyOf(currentPath, currentPath.length + 1);
            newPath[newPath.length - 1] = keyByte;
            collectEntries(it.nextNode(), newPath, depth + 1, entries);
        }
    }

    /**
     * 前缀查询：查找所有以指定前缀开头的键
     *
     * @param prefix 前缀
     * @return 匹配的条目列表
     */
    public List<Entry<V>> prefixSearch(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            List<Entry<V>> all = new ArrayList<>();
            for (Entry<V> entry : this) {
                all.add(entry);
            }
            return all;
        }

        byte[] prefixBytes = prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        List<Entry<V>> result = new ArrayList<>();
        Node<V> node = findNodeByPrefix(root, prefixBytes, 0);
        if (node != null) {
            byte[] path = prefixBytes.clone();
            collectEntries(node, path, path.length, result);
        }
        return result;
    }

    private Node<V> findNodeByPrefix(Node<V> node, byte[] prefix, int depth) {
        if (node == null) {
            return null;
        }

        // 检查前缀匹配
        int prefixMatchLen = checkPrefix(node, prefix, depth);
        if (prefixMatchLen < node.prefixLen && prefixMatchLen < prefix.length - depth) {
            return null;
        }

        depth += node.prefixLen;

        if (depth >= prefix.length) {
            return node;
        }

        byte nextByte = prefix[depth];
        Node<V> child = node.findChild(nextByte);
        return findNodeByPrefix(child, prefix, depth + 1);
    }

    /**
     * 清空树
     */
    public void clear() {
        root = new Node4<>();
    }

    /**
     * 条目类
     */
    public static class Entry<V> implements Map.Entry<String, V> {
        private final String key;
        private final V value;

        public Entry(String key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException("Entry is immutable");
        }
    }
}
