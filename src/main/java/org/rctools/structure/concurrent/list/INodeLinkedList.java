package org.rctools.structure.concurrent.list;

import java.util.Comparator;
import java.util.List;

/**
 * @author: ryan_shi@126.com
 * @date: 2023/7/14 22:11
 * @descrption:
 */
public interface INodeLinkedList<E> extends List<E> {
    ConcurrentLinkedList.Node<E> nodeAdd(E e);

    void nodeAdd(E e, ConcurrentLinkedList.Node<E> node);

    boolean nodeRemove(ConcurrentLinkedList.Node<E> node);

    /**
     * 根据 element 移除
     *
     * @return 被移除的 node 节点对象，移除失败时为 null
     */
    ConcurrentLinkedList.Node<E> nodeRemove(E e);

    void nodeUpdate(ConcurrentLinkedList.Node<E> node);

    INodeLinkedList<E> nodeClone();

    INodeLinkedList<E> nodeCloneAndDelete(ConcurrentLinkedList.Node<E> node);

    ConcurrentLinkedList.Node<E> getNode(E e, Comparator<E> comparator);
}
