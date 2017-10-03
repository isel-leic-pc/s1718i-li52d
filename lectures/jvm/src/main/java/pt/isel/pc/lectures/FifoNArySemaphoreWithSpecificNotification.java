package pt.isel.pc.lectures;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FifoNArySemaphoreWithSpecificNotification {

    private static class RequestState {
        public final int units;
        public final Condition condition;
        public boolean isDone;
        public RequestState(int units, Condition condition) {
            this.units = units;
            this.condition = condition;
            isDone = false;
        }
    }

    private int counter;
    private final NodeLinkedList<RequestState> queue = new NodeLinkedList<>();
    private final Lock lock = new ReentrantLock();

    public FifoNArySemaphoreWithSpecificNotification(int initial) {
        counter = initial;
    }

    public boolean acquire(int units, long timeout) throws InterruptedException {

        if(units <= 0) {
            throw new IllegalArgumentException("units must be stricky positive");
        }
        lock.lock();
        try {

            if (queue.isEmpty() && counter >= units) {
                counter -= units;
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            Condition condition = lock.newCondition();
            NodeLinkedList.Node<RequestState> node = queue.push(new RequestState(
                    units,
                    condition
            ));
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            while(true){
                try {
                    condition.await(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if(node.value.isDone) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    queue.remove(node);
                    notifyIfNeeded();
                    throw e;
                }

                if(node.value.isDone) {
                    return true;
                }

                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
                    notifyIfNeeded();
                    return false;
                }
            }
        }finally{
            lock.unlock();
        }
    }

    public void release(int units) {

        lock.lock();
        try {
            counter += units;
            notifyIfNeeded();
        } finally {
          lock.unlock();
        }
    }

    private void notifyIfNeeded() {
        while(!queue.isEmpty() && counter >= queue.getHeadValue().units) {
            NodeLinkedList.Node<RequestState> node = queue.pull();
            counter -= node.value.units;
            node.value.isDone = true;

            node.value.condition.signal();
            // node.value.condition.signalAll();
            // node.value.condition.notify(); // WRONG
        }
    }
}
