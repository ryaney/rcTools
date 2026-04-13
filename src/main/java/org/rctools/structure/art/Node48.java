package org.rctools.structure.art;

/**
 * Node48: 大规模节点，适合子节点数量在 17-48 的情况
 * <p>
 * 存储结构：
 * - index[256]: 索引表，存储子节点在 children 数组中的索引+1（0表示不存在）
 * - children[48]: 子节点指针
 * <p>
 * 查找：O(1) 通过 index 表直接访问
 * 插入：找到第一个空槽位插入
 * 删除：移除后可能触发降级到 Node16
 * <p>
 * 空间效率：256字节的 index 表固定开销，但支持快速查找
 */
public class Node48<V> extends Node<V> {

    final byte[] index = new byte[256];
    final Node<V>[] children = new Node[48];

    Node48() {
        childCount = 0;
        prefix = new byte[0];
        prefixLen = 0;
    }

    Node48(byte[] prefix, int prefixLen) {
        this.prefix = prefix;
        this.prefixLen = prefixLen;
        childCount = 0;
    }

    @Override
    public int capacity() {
        return 48;
    }

    @Override
    public NodeType type() {
        return NodeType.NODE48;
    }

    @Override
    public Node<V> findChild(byte keyByte) {
        int idx = index[keyByte & 0xFF] - 1;
        if (idx >= 0) {
            return children[idx];
        }
        return null;
    }

    @Override
    public Node<V> getChild(byte keyByte) {
        return findChild(keyByte);
    }

    @Override
    public boolean hasChild(byte keyByte) {
        return index[keyByte & 0xFF] > 0;
    }

    @Override
    public Node<V> addChild(byte keyByte, Node<V> child) {
        int idx = index[keyByte & 0xFF] - 1;
        if (idx >= 0) {
            children[idx] = child;
            return this;
        }

        if (childCount < 48) {
            // 找到第一个空槽位
            for (int i = 0; i < 48; i++) {
                if (children[i] == null) {
                    children[i] = child;
                    index[keyByte & 0xFF] = (byte) (i + 1);
                    childCount++;
                    break;
                }
            }
            return this;
        }

        return grow();
    }

    @Override
    public Node<V> removeChild(byte keyByte) {
        int idx = index[keyByte & 0xFF] - 1;
        if (idx >= 0) {
            children[idx] = null;
            index[keyByte & 0xFF] = 0;
            childCount--;
            if (childCount <= 16) {
                return shrink();
            }
        }
        return this;
    }

    @Override
    public byte firstChildKey() {
        for (int i = 0; i < 256; i++) {
            if (index[i] > 0) {
                return (byte) i;
            }
        }
        throw new IllegalStateException("No children");
    }

    @Override
    public byte nextChildKey(byte keyByte) {
        int start = (keyByte & 0xFF) + 1;
        for (int i = start; i < 256; i++) {
            if (index[i] > 0) {
                return (byte) i;
            }
        }
        throw new IllegalStateException("No next child");
    }

    private Node<V> grow() {
        Node256<V> node = new Node256<>(prefix, prefixLen);
        node.childCount = childCount;
        node.value = value;
        for (int i = 0; i < 256; i++) {
            int childIdx = index[i] - 1;
            if (childIdx >= 0) {
                node.children[i] = children[childIdx];
            }
        }
        return node;
    }

    private Node<V> shrink() {
        Node16<V> node = new Node16<>(prefix, prefixLen);
        node.value = value;
        int pos = 0;
        for (int i = 0; i < 256; i++) {
            int childIdx = index[i] - 1;
            if (childIdx >= 0) {
                node.keys[pos] = (byte) i;
                node.children[pos] = children[childIdx];
                pos++;
            }
        }
        node.childCount = pos;
        return node;
    }

    @Override
    public ChildIterator<V> iterator() {
        return new Node48Iterator();
    }

    private class Node48Iterator implements ChildIterator<V> {
        private int idx = 0;

        @Override
        public boolean hasNext() {
            while (idx < 256 && index[idx] == 0) {
                idx++;
            }
            return idx < 256;
        }

        @Override
        public byte nextKey() {
            while (idx < 256 && index[idx] == 0) {
                idx++;
            }
            if (idx >= 256) {
                throw new IllegalStateException();
            }
            return (byte) idx++;
        }

        @Override
        public Node<V> nextNode() {
            int childIdx = index[idx - 1] - 1;
            if (childIdx < 0) {
                throw new IllegalStateException();
            }
            return children[childIdx];
        }
    }
}
