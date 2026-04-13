package org.rctools.structure.art;

/**
 * Node4: 最紧凑的节点类型，适合子节点数量较少的情况
 * <p>
 * 存储结构：
 * - keys[4]: 存储键字节（有序）
 * - children[4]: 对应的子节点指针
 * <p>
 * 查找：线性搜索（小规模下比二分查找更快）
 * 插入：找到位置，插入到 keys 和 children 数组
 * 删除：移除后可能触发降级
 */
public class Node4<V> extends Node<V> {

    final byte[] keys = new byte[4];
    final Node<V>[] children = new Node[4];

    Node4() {
        childCount = 0;
        prefix = new byte[0];
        prefixLen = 0;
    }

    Node4(byte[] prefix, int prefixLen) {
        this.prefix = prefix;
        this.prefixLen = prefixLen;
        childCount = 0;
    }

    @Override
    public int capacity() {
        return 4;
    }

    @Override
    public NodeType type() {
        return NodeType.NODE4;
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
        // 检查是否已存在
        for (int i = 0; i < childCount; i++) {
            if (keys[i] == keyByte) {
                children[i] = child;
                return this;
            }
        }

        if (childCount < 4) {
            // 找到插入位置（保持有序）
            int idx = 0;
            while (idx < childCount && (keys[idx] & 0xFF) < (keyByte & 0xFF)) {
                idx++;
            }
            // 后移
            for (int i = childCount; i > idx; i--) {
                keys[i] = keys[i - 1];
                children[i] = children[i - 1];
            }
            keys[idx] = keyByte;
            children[idx] = child;
            childCount++;
            return this;
        }

        // 需要升级到 Node16
        return grow();
    }

    @Override
    public Node<V> removeChild(byte keyByte) {
        for (int i = 0; i < childCount; i++) {
            if (keys[i] == keyByte) {
                // 后移填补
                for (int j = i; j < childCount - 1; j++) {
                    keys[j] = keys[j + 1];
                    children[j] = children[j + 1];
                }
                childCount--;
                // Node4 不能再降级
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
        Node16<V> node = new Node16<>(prefix, prefixLen);
        node.childCount = childCount;
        System.arraycopy(keys, 0, node.keys, 0, childCount);
        System.arraycopy(children, 0, node.children, 0, childCount);
        node.value = value;
        return node;
    }

    @Override
    public ChildIterator<V> iterator() {
        return new Node4Iterator();
    }

    private class Node4Iterator implements ChildIterator<V> {
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
