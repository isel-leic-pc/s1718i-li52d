package pt.isel.pc.lectures;

public class FifoNArySemaphore {

    private int counter;
    private final NodeLinkedList<Integer> queue = new NodeLinkedList<>();
    private final Object mon = new Object();

    public FifoNArySemaphore(int initial) {
        counter = initial;
    }

    public boolean acquire(int units, long timeout) throws InterruptedException {

        synchronized (mon) {

            if (queue.isEmpty() && counter >= units) {
                counter -= units;
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            NodeLinkedList.Node<Integer> node = queue.push(units);
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            while(true){
                try {
                    mon.wait(remaining);
                } catch (InterruptedException e) {
                    queue.remove(node);
                    notifyIfNeeded();
                    throw e;
                }

                if(queue.isHeadNode(node) && counter >= queue.getHeadValue()) {
                    queue.remove(node);
                    counter -= units;
                    notifyIfNeeded();
                    return true;
                }

                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
                    notifyIfNeeded();
                    return false;
                }
            }
        }
    }

    public void release(int units) {

        synchronized (mon) {
            counter += units;
            notifyIfNeeded();
        }
    }

    private void notifyIfNeeded() {
        if(!queue.isEmpty() && counter >= queue.getHeadValue()) {
            mon.notifyAll();
        }
    }

}
