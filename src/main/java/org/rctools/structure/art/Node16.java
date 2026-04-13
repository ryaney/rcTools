package org.rctools.structure.art;

/**
 * Node16: 中等规模的节点类型，适合子节点数量在 5-16 的情况
 * <p>
 * 存储结构：
 * - keys[16]: 存储键字节（有序）
 * - children[16]: 对应的子节点指针
 * <p>
 * 查找：线性搜索（规模仍较小）
 * 插入：找到位置，插入到 keys 和 children 数组
 * 删除：移除后可能触发降级到 Node4
 */
public class Node16<V> extends Node<V> {

    final byte[] keys = new byte[16];
    final Node<V>[] children = new Node[16];

    Node16() {
        childCount = 0;
        prefix = new byte[0];
        prefixLen = 0;
    }

    Node16(byte[] prefix, int prefixLen) {
        this.prefix = prefix;
        this.prefixLen = prefixLen;
        childCount = 0;
    }

    @Override
    public int capacity() {
        return 16;
    }

    @Override
    public NodeType type() {
        return NodeType.NODE16;
    }

    @Override
    public Node<V> findChild(byte keyByte) {
        for (int i = 0; i < childCount; i++) {
            if (keys[i] == keyByte) {
                return children[i];
            }
        }
        return null;
    }

    @Override
    public Node<V> getChild(byte keyByte) {
        return findChild(keyByte);
    }

    @Override
    public boolean hasChild(byte keyByte) {
        for (int i = 0; i < childCount; i++) {
            if (keys[i] == keyByte) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Node<V> addChild(byte keyByte, Node<V> child) {
        for (int i = 0; i < childCount; i++) {
            if (keys[i] == keyByte) {
                children[i] = child;
                return this;
            }
        }

        if (childCount < 16) {
            int idx = 0;
            while (idx < childCount && (keys[idx] & 0xFF) < (keyByte & 0xFF)) {
                idx++;
            }
            for (int i = childCount; i > idx; i--) {
                keys[i] = keys[i - 1];
                children[i] = children[i - 1];
            }
            keys[idx] = keyByte;
            children[idx] = child;
            childCount++;
            return this;
        }

        return grow();
    }

    @Override
    public Node<V> removeChild(byte keyByte) {
        for (int i = 0; i < childCount; i++) {
            if (keys[i] == keyByte) {
                for (int j = i; j < childCount - 1; j++) {
                    keys[j] = keys[j + 1];
                    children[j] = children[j + 1];
                }
                childCount--;
                if (childCount <= 4) {
                    return shrink();
                }
                return this;
            }
        }
        return this;
    }

    @Override
    public byte firstChildKey() {
        if (childCount == 0) {
            throw new IllegalStateException("No children");
        }
        return keys[0];
    }

    @Override
    public byte nextChildKey(byte keyByte) {
        for (int i = 0; i < childCount; i++) {
            if ((keys[i] & 0xFF) > (keyByte & 0xFF)) {
                return keys[i];
            }
        }
        throw new IllegalStateException("No next child");
    }

    private Node<V> grow() {
        Node48<V> node = new Node48<>(prefix, prefixLen);
        node.childCount = childCount;
        node.value = value;
        for (int i = 0; i < childCount; i++) {
            node.index[keys[i] & 0xFF] = (byte) (i + 1);
            node.children[i] = children[i];
        }
        return node;
    }

    private Node<V> shrink() {
        Node4<V> node = new Node4<>(prefix, prefixLen);
        node.childCount = childCount;
        System.arraycopy(keys, 0, node.keys, 0, childCount);
        System.arraycopy(children, 0, node.children, 0, childCount);
        node.value = value;
        return node;
    }

    @Override
    public ChildIterator<V> iterator() {
        return new Node16Iterator();
    }

    private class Node16Iterator implements ChildIterator<V> {
        private int idx = 0;

        @Override
        public boolean hasNext() {
            return idx < childCount;
        }

        @Override
        public byte nextKey() {
            if (idx >= childCount) {
                throw new IllegalStateException();
            }
            return keys[idx++];
        }

        @Override
        public Node<V> nextNode() {
            if (idx - 1 >= childCount) {
                throw new IllegalStateException();
            }
            return children[idx - 1];
        }
    }
}
