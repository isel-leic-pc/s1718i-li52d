package pt.isel.pc.lectures;

public class ManualResetEvent {

    private boolean flag = false;
    private final Object mon = new Object();
    private final NodeLinkedList<Boolean> queue = new NodeLinkedList<>();

    public boolean ewait(long timeout) throws InterruptedException {
        synchronized (mon) {
            if(flag) {
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            NodeLinkedList.Node<Boolean> node = queue.push(false);
            while(true) {
                try {
                    mon.wait(remaining);
                }catch(InterruptedException e) {
                    if(node.value != true) {
                        queue.remove(node);
                        throw e;
                    }
                    Thread.currentThread().interrupt();
                    return true;
                }
                if(node.value == true) {
                    return true;
                }
                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)) {
                    queue.remove(node);
                    return false;
                }
            }
        }
    }

    public void set() {
        synchronized (mon) {
            flag = true;
            while(!queue.isEmpty()) {
                NodeLinkedList.Node<Boolean> node = queue.pull();
                node.value = true;
            }
            mon.notifyAll();
        }
    }

    public void clear() {
        synchronized (mon) {
            flag = false;
        }
    }
}
