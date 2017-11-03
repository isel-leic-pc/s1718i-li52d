package pt.isel.pc.lectures;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<E> {

    private static class Node<E> {
        public final E elem;
        public Node<E> next;

        public Node(E elem) {
            this.elem = elem;
        }
    }

    private AtomicReference<Node<E>> head =
            new AtomicReference<>(null);

    public void push(E e) {
        Node<E> node = new Node<>(e);
        while(true) {
            Node<E> observedHead = head.get();
            node.next = observedHead;
            if(head.compareAndSet(observedHead, node)) {
                break;
            }
        }
    }

    public Optional<E> pop() {
        while(true) {
            Node<E> observedHead = head.get();
            if (observedHead == null) {
                return Optional.empty();
            }
            if (head.compareAndSet(observedHead, observedHead.next)) {
                return Optional.of(observedHead.elem);
            }
        }
    }

}
