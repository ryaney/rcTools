package org.rctools.structure.art;

/**
 * Node256: 超大规模节点，适合子节点数量在 49-256 的情况
 * <p>
 * 存储结构：
 * - children[256]: 直接索引的子节点指针数组
 * <p>
 * 查找：O(1) 直接数组访问
 * 插入：直接在对应位置设置
 * 删除：可能触发降级到 Node48
 * <p>
 * 空间效率：256个引用，每个8字节，共2KB（在64位JVM上）
 * 当子节点较少时空间利用率低，但查找性能最优
 */
public class Node256<V> extends Node<V> {

    final Node<V>[] children = new Node[256];

    Node256() {
        childCount = 0;
        prefix = new byte[0];
        prefixLen = 0;
    }

    Node256(byte[] prefix, int prefixLen) {
        this.prefix = prefix;
        this.prefixLen = prefixLen;
        childCount = 0;
    }

    @Override
    public int capacity() {
        return 256;
    }

    @Override
    public NodeType type() {
        return NodeType.NODE256;
    }

    @Override
    public Node<V> findChild(byte keyByte) {
        return children[keyByte & 0xFF];
    }

    @Override
    public Node<V> getChild(byte keyByte) {
        return children[keyByte & 0xFF];
    }

    @Override
    public boolean hasChild(byte keyByte) {
        return children[keyByte & 0xFF] != null;
    }

    @Override
    public Node<V> addChild(byte keyByte, Node<V> child) {
        int idx = keyByte & 0xFF;
        if (children[idx] == null) {
            childCount++;
        }
        children[idx] = child;
        return this;
    }

    @Override
    public Node<V> removeChild(byte keyByte) {
        int idx = keyByte & 0xFF;
        if (children[idx] != null) {
            children[idx] = null;
            childCount--;
            if (childCount <= 48) {
                return shrink();
            }
        }
        return this;
    }

    @Override
    public byte firstChildKey() {
        for (int i = 0; i < 256; i++) {
            if (children[i] != null) {
                return (byte) i;
            }
        }
        throw new IllegalStateException("No children");
    }

    @Override
    public byte nextChildKey(byte keyByte) {
        int start = (keyByte & 0xFF) + 1;
        for (int i = start; i < 256; i++) {
            if (children[i] != null) {
                return (byte) i;
            }
        }
        throw new IllegalStateException("No next child");
    }

    private Node<V> shrink() {
        Node48<V> node = new Node48<>(prefix, prefixLen);
        node.value = value;
        int pos = 0;
        for (int i = 0; i < 256; i++) {
            if (children[i] != null) {
                node.children[pos] = children[i];
                node.index[i] = (byte) (pos + 1);
                pos++;
            }
        }
        node.childCount = pos;
        return node;
    }

    @Override
    public ChildIterator<V> iterator() {
        return new Node256Iterator();
    }

    private class Node256Iterator implements ChildIterator<V> {
        private int idx = 0;

        @Override
        public boolean hasNext() {
            while (idx < 256 && children[idx] == null) {
                idx++;
            }
            return idx < 256;
        }

        @Override
        public byte nextKey() {
            while (idx < 256 && children[idx] == null) {
                idx++;
            }
            if (idx >= 256) {
                throw new IllegalStateException();
            }
            return (byte) idx++;
        }

        @Override
        public Node<V> nextNode() {
            if (idx - 1 < 0 || idx - 1 >= 256) {
                throw new IllegalStateException();
            }
            return children[idx - 1];
        }
    }
}
