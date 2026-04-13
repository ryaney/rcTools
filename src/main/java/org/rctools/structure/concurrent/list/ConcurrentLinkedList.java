package org.rctools.structure.concurrent.list;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyListIterator;

/**
 * @author ryan_scy@126.com
 * @since 2023/7/14 22:11
 * @description
 */
public class ConcurrentLinkedList<E> extends AbstractSequentialList<E> implements INodeLinkedList<E> {

    /** Sentinel nodes. */
    private final transient Node<E> head = new Node<>();
    private final transient Node<E> tail = new Node<>();

    private transient Node<E> tailPrev;

    private AtomicInteger atomicSize = new AtomicInteger(0);

    Comparator<E> comparator;

    /**
     * Constructor.
     */
    public ConcurrentLinkedList() {
        head.next = tail;
        tailPrev = head;
    }

    /**
     * Constructs an empty list.
     */
    public ConcurrentLinkedList(final Comparator<E> comparator) {
        this.comparator = comparator;
        head.next = tail;
        tailPrev = head;
    }

    /**
     * Returns a list-iterator of the elements in this list (in proper
     * sequence), starting at the specified position in the list.
     * Obeys the general contract of {@code List.listIterator(int)}.<p>
     * <p>
     * The list-iterator is <i>fail-fast</i>: if the list is structurally
     * modified at any time after the Iterator is created, in any way except
     * through the list-iterator's own {@code remove} or {@code add}
     * methods, the list-iterator will throw a
     * {@code ConcurrentModificationException}.  Thus, in the face of
     * concurrent modification, the iterator fails quickly and cleanly, rather
     * than risking arbitrary, non-deterministic behavior at an undetermined
     * time in the future.
     *
     * @param index index of the first element to be returned from the
     *              list-iterator (by a call to {@code next})
     * @return a ListIterator of the elements in this list (in proper
     * sequence), starting at the specified position in the list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @see List#listIterator(int)
     */
    @Override
    public ListIterator<E> listIterator(final int index) {
        return new ListItr();
    }

    @Override
    public int size() {
        return atomicSize.get();
    }

    @Override
    public Node<E> nodeAdd(E e) {
        if (Objects.nonNull(this.comparator)
                && tailPrev != head
                && this.comparator.compare(e, tailPrev.item) < 0) {
            return this.linkSort(e);
        } else {
            return this.linkLast(e);
        }
    }

    @Override
    public void nodeAdd(E e, Node<E> node) {
        if (Objects.nonNull(this.comparator)
                && tailPrev != head
                && this.comparator.compare(e, tailPrev.item) < 0) {
            this.linkSort(e, node);
        } else {
            this.linkLast(e, node);
        }
    }

    @Override
    public boolean nodeRemove(Node<E> node) {
        Node<E> pred = null, curr = null, succ = null;
        // initialization
        pred = head;
        curr = pred.next;
        // traverse linked list
        while (curr != tail) {
            succ = curr.next;
            if (curr == node) {
                pred.casNext(curr, succ);
                if (succ == tail) {
                    tailPrev = pred;
                }
                atomicSize.decrementAndGet();
                return true;
            } else {
                // continue searching
                pred = curr;
                curr = succ;
            }
        }
        return false;
    }

    @Override
    public Node<E> nodeRemove(E e) {
        Node<E> pred = null, curr = null, succ = null;
        // initialization
        pred = head;
        curr = pred.next;
        // traverse linked list
        while (curr != tail) {
            succ = curr.next;
            if (e.equals(curr.item)) {
                pred.casNext(curr, succ);
                if (succ == tail) {
                    tailPrev = pred;
                }
                atomicSize.decrementAndGet();
                return curr;
            } else {
                // continue searching
                pred = curr;
                curr = succ;
            }
        }
        return null;
    }

    @Override
    public void nodeUpdate(final Node<E> node) {
        final E item = node.item;
        this.nodeRemove(node);
        this.nodeAdd(item, node);
    }

    @Override
    public ConcurrentLinkedList<E> nodeClone() {
        return this.nodeCloneAndDelete(null);
    }

    @Override
    public Node<E> getNode(E e, Comparator<E> comparator) {
        Node<E> curr = head.next;
        while (curr != tail) {
            if (comparator.compare(e, curr.item) == 0) {
                return curr;
            }
            curr = curr.next;
        }
        return null;
    }

    @Override
    public ConcurrentLinkedList<E> nodeCloneAndDelete(final Node<E> node) {
        final ConcurrentLinkedList<E> clone = new ConcurrentLinkedList<>(this.comparator);

        // Put clone into "virgin" state
        clone.atomicSize = new AtomicInteger(atomicSize.get());
        clone.modCount = 0;
        clone.comparator = this.comparator;

        // Initialize clone with our elements
        for (Node<E> x = this.head.next; x != tail; x = x.next) {
            if (x.equals(node)) {
                continue;
            }
            clone.linkLast(x.item);
        }
        return clone;
    }

    /**
     * Returns {@code true} if this list contains the specified element.
     * More formally, returns {@code true} if and only if this list contains
     * at least one element {@code e} such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     *
     * @param o element whose presence in this list is to be tested
     * @return {@code true} if this list contains the specified element
     */
    @Override
    public boolean contains(final Object o) {
        Node<E> curr = head.next;
        while (curr != tail) {
            if (o.equals(curr.item)) {
                return true;
            }
            curr = curr.next;
        }
        return false;
    }

    /**
     * Links e as last element.
     */
    private Node<E> linkSort(final E e) {
        Node<E> pred, curr, succ;
        // initialization
        pred = head;
        curr = pred.next;
        // traverse linked list
        while (curr != tail) {
            succ = curr.next;
            int compare = this.comparator.compare(e, curr.item);
            // continue searching
            if (compare < 0) {
                // locate a window: do insert
                Node<E> node = new Node<>(e, curr);
                pred.casNext(curr, node);
                atomicSize.incrementAndGet();
                return node;
            } else {
                pred = curr;
                curr = succ;
            }
        }
        Node<E> node = new Node<>(e, curr);
        pred.casNext(curr, node);
        tailPrev = node;
        atomicSize.incrementAndGet();
        return node;
    }

    /**
     * Links e as last element.
     */
    private void linkSort(final E e, Node<E> node) {
        Node<E> pred, curr, succ;
        // initialization
        pred = head;
        curr = pred.next;
        // traverse linked list
        while (curr != tail) {
            succ = curr.next;
            int compare = this.comparator.compare(e, curr.item);
            // continue searching
            if (compare < 0) {
                // locate a window: do insert
                node.next = curr;
                pred.casNext(curr, node);
                atomicSize.incrementAndGet();
                return;
            } else {
                pred = curr;
                curr = succ;
            }
        }
        node.next = curr;
        pred.casNext(curr, node);
        tailPrev = node;
        atomicSize.incrementAndGet();
    }

    /**
     * Links e as last element.
     */
    private Node<E> linkLast(final E e) {
        final Node<E> pred = tailPrev;
        final Node<E> node = new Node<>(e, tail);
        pred.casNext(tail, node);
        tailPrev = node;
        atomicSize.incrementAndGet();
        return node;
    }

    /**
     * Links e as last element.
     */
    private void linkLast(final E e, Node<E> node) {
        node.next = tail;
        final Node<E> pred = tailPrev;
        pred.casNext(tail, node);
        tailPrev = node;
        atomicSize.incrementAndGet();
    }

    // Positional Access Operations

    /**
     * Returns the element at the specified position in this list.
     *
     * @param index index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E get(int index) {
        checkElementIndex(index);
        return node(index).item;
    }

    /**
     * Removes the element at the specified position in this list.  Shifts any
     * subsequent elements to the left (subtracts one from their indices).
     * Returns the element that was removed from the list.
     *
     * @param index the index of the element to be removed
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    public E remove(int index) {
        checkElementIndex(index);
        Node<E> node = node(index);
        nodeRemove(node);
        return node.item;
    }

    /**
     * Tells if the argument is the index of an existing element.
     */
    private boolean isElementIndex(int index) {
        return index >= 0 && index < atomicSize.get();
    }

    /**
     * Tells if the argument is the index of a valid position for an
     * iterator or an add operation.
     */
    private boolean isPositionIndex(int index) {
        return index >= 0 && index <= atomicSize.get();
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: "+index+", Size: " + atomicSize.get();
    }

    private void checkElementIndex(int index) {
        if (!isElementIndex(index)) {
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }
    }

    private void checkPositionIndex(int index) {
        if (!isPositionIndex(index)) {
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
        }
    }

    /**
     * Returns the (non-null) Node at the specified element index.
     */
    Node<E> node(int index) {
        Node<E> x = head.next;
        for (int i = 0; i < index; i++) {
            x = x.next;
        }
        return x;
    }

    private class ListItr implements ListIterator<E> {
        private Node<E> next = head.next;

        ListItr() {
        }

        @Override
        public boolean hasNext() {
            return this.next != tail;
        }

        @Override
        public E next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            E item = this.next.item;
            this.next = this.next.next;
            return item;
        }

        @Override
        public boolean hasPrevious() {
            return this.next != head;
        }

        @Override
        public E previous() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int previousIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove() {
            nodeRemove(this.next);
        }

        @Override
        public void set(final E e) {
            this.next.item = e;
        }

        @Override
        public void add(final E e) {
            nodeAdd(e);
        }

        @Override
        public void forEachRemaining(final Consumer<? super E> action) {
            Objects.requireNonNull(action);
            while (hasNext()) {
                action.accept(next());
            }
        }
    }

    /**
     * Internal Node<E> class.
     */
    public static class Node<E> {
        protected E item;

        protected volatile Node<E> next;

        private static final AtomicReferenceFieldUpdater<Node, Node> nextUpdater
                = AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "next");

        Node(E item, Node<E> next) {
            this.item = item;
            this.next = next;
        }

        Node() {
            this.item = null;
        }

        private boolean casNext(Node<E> o, Node<E> n) {
            return nextUpdater.compareAndSet(this, o, n);
        }

        public E getItem() {
            return item;
        }
    }

    /**
     * Returns an array containing all of the elements in this list
     * in proper sequence (from first to last element).
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this list.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this list
     * in proper sequence
     */
    @Override
    public Object[] toArray() {
        final Object[] result = new Object[this.atomicSize.get()];
        int i = 0;
        for (Node<E> x = this.head.next; x != tail; x = x.next) {
            result[i++] = x.item;
        }
        return result;
    }

    /**
     * Returns an array containing all of the elements in this list in
     * proper sequence (from first to last element); the runtime type of
     * the returned array is that of the specified array.  If the list fits
     * in the specified array, it is returned therein.  Otherwise, a new
     * array is allocated with the runtime type of the specified array and
     * the size of this list.
     *
     * <p>If the list fits in the specified array with room to spare (i.e.,
     * the array has more elements than the list), the element in the array
     * immediately following the end of the list is set to {@code null}.
     * (This is useful in determining the length of the list <i>only</i> if
     * the caller knows that the list does not contain any null elements.)
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a list known to contain only strings.
     * The following code can be used to dump the list into a newly
     * allocated array of {@code String}:
     *
     * <pre>
     *     String[] y = x.toArray(new String[0]);</pre>
     * <p>
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the list are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing the elements of the list
     * @throws ArrayStoreException  if the runtime type of the specified array
     *                              is not a supertype of the runtime type of every element in
     *                              this list
     * @throws NullPointerException if the specified array is null
     */
    @Override
    public <T> T[] toArray(T[] a) {
        int size = atomicSize.get();
        if (a.length < size) {
            a = (T[]) java.lang.reflect.Array.newInstance(
                    a.getClass().getComponentType(), size);
        }
        int i = 0;
        final Object[] result = a;
        for (Node<E> x = this.head.next; x != null; x = x.next) {
            result[i++] = x.item;
        }

        if (a.length > size) {
            a[size] = null;
        }

        return a;
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * list.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED} and
     * {@link Spliterator#ORDERED}.  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this list
     * @implNote The {@code Spliterator} additionally reports {@link Spliterator#SUBSIZED}
     * and implements {@code trySplit} to permit limited parallelism..
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return new LLSpliterator<>(this, -1, 0);
    }

    /**
     * A customized variant of Spliterators.IteratorSpliterator
     */
    static final class LLSpliterator<E> implements Spliterator<E> {
        static final int BATCH_UNIT = 1 << 10;  // batch array size increment
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final ConcurrentLinkedList<E> list; // null OK unless traversed
        Node<E> current;      // current node; null until initialized
        int est;              // size estimate; -1 until first needed
        int expectedModCount; // initialized when est set
        int batch;            // batch size for splits

        LLSpliterator(final ConcurrentLinkedList<E> list, final int est, final int expectedModCount) {
            this.list = list;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEst() {
            int s; // force initialization
            final ConcurrentLinkedList<E> lst;
            if ((s = this.est) < 0) {
                if ((lst = this.list) == null) {
                    s = this.est = 0;
                } else {
                    this.expectedModCount = lst.modCount;
                    this.current = lst.head.next;
                    s = this.est = lst.atomicSize.get();
                }
            }
            return s;
        }

        @Override
        public long estimateSize() {
            return (long) this.getEst();
        }

        @Override
        public Spliterator<E> trySplit() {
            Node<E> p;
            final int s = this.getEst();
            if (s > 1 && (p = this.current) != null) {
                int n = this.batch + BATCH_UNIT;
                if (n > s) {
                    n = s;
                }
                if (n > MAX_BATCH) {
                    n = MAX_BATCH;
                }
                final Object[] a = new Object[n];
                int j = 0;
                do {
                    a[j++] = p.item;
                } while ((p = p.next) != null && j < n);
                this.current = p;
                this.batch = j;
                this.est = s - j;
                return Spliterators.spliterator(a, 0, j, Spliterator.ORDERED);
            }
            return null;
        }

        @Override
        public void forEachRemaining(final Consumer<? super E> action) {
            Node<E> p;
            int n;
            if (action == null) {
                throw new NullPointerException();
            }
            if ((n = this.getEst()) > 0 && (p = this.current) != null) {
                this.current = null;
                this.est = 0;
                do {
                    final E e = p.item;
                    p = p.next;
                    action.accept(e);
                } while (p != null && --n > 0);
            }
            if (this.list.modCount != this.expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }

        @Override
        public boolean tryAdvance(final Consumer<? super E> action) {
            final Node<E> p;
            if (action == null) {
                throw new NullPointerException();
            }
            if (this.getEst() > 0 && (p = this.current) != null) {
                --this.est;
                final E e = p.item;
                this.current = p.next;
                action.accept(e);
                if (this.list.modCount != this.expectedModCount) {
                    throw new ConcurrentModificationException();
                }
                return true;
            }
            return false;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }
    }

    public static final INodeLinkedList EMPTY_LIST = new EmptyList();

    public static INodeLinkedList emptyList() {
        return EMPTY_LIST;
    }

    private static class EmptyList<E>
            extends AbstractList<E>
            implements RandomAccess, INodeLinkedList<E> {


        @Override
        public Iterator<E> iterator() {
            return emptyIterator();
        }

        @Override
        public ListIterator<E> listIterator() {
            return emptyListIterator();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean contains(final Object obj) {
            return false;
        }

        @Override
        public boolean containsAll(final Collection<?> c) {
            return c.isEmpty();
        }

        @Override
        public Object[] toArray() {
            return new Object[0];
        }

        @Override
        public <T> T[] toArray(final T[] a) {
            if (a.length > 0) {
                a[0] = null;
            }
            return a;
        }

        @Override
        public E get(final int index) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }

        @Override
        public boolean equals(final Object o) {
            return (o instanceof List) && ((List<?>) o).isEmpty();
        }

        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public boolean removeIf(final Predicate<? super E> filter) {
            Objects.requireNonNull(filter);
            return false;
        }

        @Override
        public void replaceAll(final UnaryOperator<E> operator) {
            Objects.requireNonNull(operator);
        }

        @Override
        public void sort(final Comparator<? super E> c) {
        }

        // Override default methods in Collection
        @Override
        public void forEach(final Consumer<? super E> action) {
            Objects.requireNonNull(action);
        }

        @Override
        public Spliterator<E> spliterator() {
            return Spliterators.emptySpliterator();
        }

        // Preserves singleton property
        private Object readResolve() {
            return EMPTY_LIST;
        }

        @Override
        public Node<E> nodeAdd(final E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void nodeAdd(final E e, final Node<E> node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean nodeRemove(final Node<E> node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Node<E> nodeRemove(final E e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void nodeUpdate(final Node<E> node) {
            throw new UnsupportedOperationException();
        }

        @Override
        public INodeLinkedList<E> nodeClone() {
            throw new UnsupportedOperationException();
        }

        @Override
        public INodeLinkedList<E> nodeCloneAndDelete(final Node<E> node) {
            throw new UnsupportedOperationException();
        }
        @Override
        public Node<E> getNode(E e, Comparator<E> comparator) {
            return null;
        }
    }
}

