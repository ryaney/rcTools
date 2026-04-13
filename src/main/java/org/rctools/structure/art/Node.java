package org.rctools.structure.art;

/**
 * ART 节点基类
 * <p>
 * Adaptive Radix Tree 的核心是自适应节点，根据子节点数量选择不同的节点类型：
 * - Node4: 0-4 个子节点，紧凑存储
 * - Node16: 5-16 个子节点
 * - Node48: 17-48 个子节点，使用256字节索引表
 * - Node256: 49-256 个子节点，直接索引
 * <p>
 * @param <V> 值类型
 */
public abstract class Node<V> {

    /**
     * 子节点数量
     */
    int childCount;

    /**
     * 获取指定键字节对应的子节点
     */
    abstract Node<V> findChild(byte keyByte);

    /**
     * 添加子节点，返回新的节点（可能触发升级）
     */
    abstract Node<V> addChild(byte keyByte, Node<V> child);

    /**
     * 删除子节点，返回新的节点（可能触发降级）
     */
    abstract Node<V> removeChild(byte keyByte);

    /**
     * 获取第一个子节点的键字节
     */
    abstract byte firstChildKey();

    /**
     * 获取大于等于给定键字节的子节点键
     */
    abstract byte nextChildKey(byte keyByte);

    /**
     * 判断是否包含指定键字节的子节点
     */
    abstract boolean hasChild(byte keyByte);

    /**
     * 获取指定键字节的子节点（不进行查找优化）
     */
    abstract Node<V> getChild(byte keyByte);

    /**
     * 获取所有子节点
     */
    abstract ChildIterator<V> iterator();

    /**
     * 节点容量上限
     */
    abstract int capacity();

    /**
     * 节点类型
     */
    abstract NodeType type();

    /**
     * 设置值（叶子节点）
     */
    V value;

    /**
     * 压缩前缀（用于路径压缩）
     */
    byte[] prefix;

    /**
     * 前缀长度
     */
    int prefixLen;

    /**
     * 子节点迭代器
     */
    public interface ChildIterator<V> {
        boolean hasNext();
        byte nextKey();
        Node<V> nextNode();
    }

    /**
     * 节点类型
     */
    public enum NodeType {
        NODE4, NODE16, NODE48, NODE256
    }
}
