import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

import java.util.AbstractCollection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

public class SoftHeap<E> extends AbstractCollection<E> {
  private static interface Linked<T> {
    public T get();

    public Linked<T> next();
  }

  private static <T> Iterator<T>
      linkedIterator(final @Nullable Linked<T> linked) {
    return new Iterator<T>() {
      private Linked<T> current;

      @Override public boolean hasNext() {
        return current != null;
      }

      @Override public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        T cur = current.get();
        current = current.next();
        return cur;
      }

      @Override public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static final class ElemLinkedListNode<E> implements Linked<E> {
    @Nullable private final E elem;
    private ElemLinkedListNode<E> next;

    private ElemLinkedListNode(@Nullable E elem, ElemLinkedListNode<E> next) {
      this.elem = elem;
      this.next = next;
    }

    @Override public E get() {
      return elem;
    }

    @Override public ElemLinkedListNode<E> next() {
      return next;
    }
  }

  private static final class ElemList<E> extends AbstractCollection<E> {
    /**
     * Points to the first node of the linked list, or null if the list is
     * empty.
     */
    @Nullable private ElemLinkedListNode<E> head = null;
    private int size = 0;
    /**
     * Points to the last node of the linked list, or null if the list is empty.
     */
    @Nullable private ElemLinkedListNode<E> tail = null;

    /**
     * Constructs an empty ElemList.
     */
    private ElemList() {
    }

    /**
     * Constructs an ElemList with a single element.
     */
    private ElemList(@Nullable E elem) {
      this.head = this.tail = new ElemLinkedListNode<E>(elem, null);
      this.size = 1;
    }

    public void clear() {
      this.head = this.tail = null;
      this.size = 0;
    }

    public void consume(ElemList<E> list) {
      if (isEmpty()) {
        this.head = list.head;
      } else {
        this.tail.next = list.head;
      }
      this.size += list.size;
      this.tail = list.tail;
      list.clear();
    }

    public boolean isEmpty() {
      return head == null;
    }

    @Override public Iterator<E> iterator() {
      return linkedIterator(head);
    }

    /**
     * Deletes and returns an arbitrary element from the linked list. Throws a
     * {@link NoSuchElementException} if the list is empty.
     */
    public E pick() {
      if (isEmpty()) {
        throw new NoSuchElementException();
      }
      E elem = head.elem;
      head = head.next;
      size--;
      if (size == 0) {
        head = null;
        tail = null;
      }
      return elem;
    }

    @Override public int size() {
      return size;
    }
  }

  private final class Heap extends AbstractCollection<E> {
    @Nullable private Tree first = null;
    private int rank = 0;
    private int size = 0;

    public Heap() {
    }

    public Heap(@Nullable E elem) {
      this.first = new Tree(elem);
      this.size = 1;
    }

    public E extractMin() {
      if (isEmpty()) {
        throw new NoSuchElementException();
      }
      Tree t = first.sufmin();
      Node x = t.root();
      E e = x.list.pick();
      if (x.list.size() * 2 <= x.targetSize()) {
        if (!x.isLeaf()) {
          // System.err.println("Sifting at " + t);
          x.sift();
          t.updateSuffixMin();
        } else if (x.list.isEmpty()) {
          // System.err.println("Removing " + t);
          removeTree(t);
          if (t.hasPrev())
            t.prev.updateSuffixMin();
        }
      }
      size--;
      return e;
    }

    public Heap insert(@Nullable E elem) {
      return meld(new Heap(elem));
    }

    @Override public boolean isEmpty() {
      return first == null;
    }

    @Override public Iterator<E> iterator() {
      List<Iterator<E>> iterators = Lists.newArrayList();
      for (Tree t = first; t != null; t = t.next) {
        iterators.add(t.root.iterator());
      }
      return Iterators.concat(iterators.iterator());
    }

    public Heap meld(Heap q) {
      checkNotNull(q);
      Heap p = this;
      if (p.rank > q.rank) {
        Heap tmp = p;
        p = q;
        q = tmp;
      }
      p.mergeInto(q);
      q.repeatedCombine(p.rank);
      p.first = null;
      p.rank = 0;
      q.size += p.size;
      p.size = 0;
      return q;
    }

    public void mergeInto(Heap q) {
      assert rank <= q.rank;
      Tree t1 = first;
      Tree t2 = q.first;
      while (t1 != null) {
        while (t1.rank() > t2.rank()) {
          t2 = t2.next;
        }
        Tree t1Prime = t1.next;
        q.insertTree(t1, t2);
        t1 = t1Prime;
      }
    }

    public void repeatedCombine(int k) {
      if (isEmpty()) {
        return;
      }
      Tree t = first;
      while (t.hasNext()) {
        if (t.rank() == t.next.rank()) {
          if (!t.next.hasNext() || t.rank() != t.next.next.rank()) {
            t.root = new Node(t.root, t.next.root);
            removeTree(t.next);
            if (!t.hasNext()) {
              break;
            }
          }
        } else if (t.rank() > k) {
          break;
        }
        t = t.next;
      }
      if (t.rank() > rank) {
        this.rank = t.rank();
      }
      t.updateSuffixMin();
    }

    @Override public int size() {
      return size;
    }

    private void insertTree(Tree t1, Tree t2) {
      if (t2.hasPrev()) {
        t2.prev.next = t1;
        t1.prev = t2.prev;
      } else {
        first = t1;
      }
      t1.next = t2;
      t2.prev = t1;
    }

    private void removeTree(Tree t) {
      checkNotNull(t);
      if (!t.hasPrev()) {
        first = t.next;
      } else {
        t.prev.next = t.next;
      }
      if (t.hasNext()) {
        t.next.prev = t.prev;
      }
    }
  }

  private final class Node implements Iterable<E> {
    @Nullable private E ckey = null;
    @Nullable private Node left = null;
    @Nullable private Node right = null;
    private final ElemList<E> list;
    private final int rank;

    /**
     * Constructs a singleton tree.
     */
    private Node(@Nullable E elem) {
      this.ckey = elem;
      this.list = new ElemList<E>(elem);
      this.rank = 0;
    }

    /**
     * Constructs a tree of rank k+1 from two trees of rank k.
     */
    private Node(Node left, Node right) {
      assert left.rank == right.rank;
      this.rank = left.rank + 1;
      this.left = left;
      this.right = right;
      this.list = new ElemList<E>();
      sift();
    }

    private int targetSize() {
      return sizeTable[rank];
    }

    @Override public Iterator<E> iterator() {
      if (hasLeft()) {
        if (hasRight()) {
          return Iterators.concat(list.iterator(), left.iterator(),
              right.iterator());
        } else {
          return Iterators.concat(list.iterator(), left.iterator());
        }
      } else if (hasRight()) {
        return Iterators.concat(list.iterator(), right.iterator());
      } else {
        return list.iterator();
      }
    }

    boolean hasLeft() {
      return left != null;
    }

    boolean hasRight() {
      return right != null;
    }

    void sift() {
      while (list.size() < targetSize() && !isLeaf()) {
        if (!hasLeft() || (hasRight() && compare(left.ckey, right.ckey) > 0)) {
          Node tmp = left;
          left = right;
          right = tmp;
        }
        list.consume(left.list);
        ckey = left.ckey;
        if (left.isLeaf()) {
          left = null;
        } else {
          left.sift();
        }
      }
    }

    private boolean isLeaf() {
      return !(hasLeft() || hasRight());
    }
  }

  private final class Tree implements Linked<Node> {
    @Nullable private Tree next = null;
    @Nullable private Tree prev = null;
    private Node root;
    private Tree sufmin = this;

    private Tree(@Nullable E elem) {
      this.root = new Node(elem);
    }

    public boolean hasNext() {
      return next != null;
    }

    public boolean hasPrev() {
      return prev != null;
    }

    public int rank() {
      return root.rank;
    }

    public Node root() {
      return checkNotNull(root);
    }

    public Tree sufmin() {
      return checkNotNull(sufmin);
    }

    public void updateSuffixMin() {
      if (!hasNext() || compare(root.ckey, next.sufmin.root.ckey) <= 0) {
        sufmin = this;
      } else {
        sufmin = next.sufmin;
      }
      if (hasPrev()) {
        prev.updateSuffixMin();
      }
    }

    @Override public Node get() {
      return root;
    }

    @Override public Tree next() {
      return next;
    }
  }

  private final int[] sizeTable = new int[31];
  private Comparator<? super E> comparator;
  private Heap heap = new Heap();

  SoftHeap(Comparator<? super E> comparator, double epsilon) {
    this.comparator = checkNotNull(comparator);
    checkArgument(epsilon > 0 && epsilon < 1);
    int r = 5 + (int) Math.ceil(-Math.log(epsilon) / Math.log(2));
    for (int i = 0; i <= r; i++) {
      sizeTable[i] = 1;
    }
    for (int i = r + 1; i < sizeTable.length; i++) {
      sizeTable[i] = (3 * sizeTable[i - 1] + 1) / 2;
    }
  }

  public boolean add(E elem) {
    heap = heap.insert(elem);
    return true;
  }

  /**
   * Deletes an element from the heap. The element returned will be the element
   * with the smallest current key, but the current key of an element may be
   * greater than the element. If the heap is empty, throws a
   * {@link NoSuchElementException}.
   */
  public E extractMin() {
    return heap.extractMin();
  }

  public boolean isEmpty() {
    return heap.isEmpty();
  }

  @Override public Iterator<E> iterator() {
    return heap.iterator();
  }

  public Optional<E> peekKey() {
    if (heap.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(heap.first.sufmin().root.ckey);
  }

  public int size() {
    return heap.size();
  }

  private int compare(E a, E b) {
    return comparator.compare(a, b);
  }
}
