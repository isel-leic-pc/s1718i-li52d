package pt.isel.pc.lectures;

public class ReaderWriterLock {

    private boolean isWriting;
    private int readersCount;
    private Object mon = new Object();

    NodeLinkedList<Boolean> wrq = new NodeLinkedList<>();
    NodeLinkedList<Boolean> rdq = new NodeLinkedList<>();

    public boolean enterRead(long timeout) throws InterruptedException {

        synchronized (mon) {

            if(!isWriting && wrq.isEmpty()) {
                readersCount += 1;
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            NodeLinkedList.Node<Boolean> node = rdq.push(false);
            while(true) {
                try {
                    mon.wait(remaining);
                }catch(InterruptedException e){
                    if(node.value == true) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    rdq.remove(node);
                    throw e;
                }

                if(node.value == true) {
                    return true;
                }
                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)) {
                    rdq.remove(node);
                    return false;
                }
            }
        }
    }

    public boolean enterWrite(long timeout) throws InterruptedException {

        synchronized (mon) {

            if(!isWriting && readersCount == 0) {
                isWriting = true;
                return true;
            }
            if(Timeouts.noWait(timeout)) {
                return false;
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            NodeLinkedList.Node<Boolean> node = wrq.push(false);
            while(true) {
                try {
                    mon.wait(remaining);
                }catch(InterruptedException e){
                    if(node.value == true) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                    wrq.remove(node);
                    if(!isWriting && wrq.isEmpty()) {
                        insertAllWaitingReaders();
                    }
                    throw e;
                }

                if(node.value == true) {
                    return true;
                }
                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)) {
                    wrq.remove(node);
                    if(!isWriting && wrq.isEmpty()) {
                        insertAllWaitingReaders();
                    }
                    return false;
                }
            }
        }
    }

    public void leaveRead()  {
        synchronized (mon) {
            readersCount -= 1;
            if(readersCount == 0 && !wrq.isEmpty()) {
                insertWaitingWriter();
            }
        }

    }

    public void leaveWrite() {
        synchronized (mon) {
            isWriting = false;
            if(!rdq.isEmpty()) {
               insertAllWaitingReaders();
            } else if(!wrq.isEmpty()) {
                insertWaitingWriter();
            }
        }
    }

    private void insertAllWaitingReaders() {
        if(!rdq.isEmpty()) {
            do {
                NodeLinkedList.Node<Boolean> reader = rdq.pull();
                reader.value = true;
                readersCount += 1;
            } while(!rdq.isEmpty());
            mon.notifyAll();
        }
    }

    private void insertWaitingWriter() {
        NodeLinkedList.Node<Boolean> node = wrq.pull();
        node.value = true;
        isWriting = true;
        mon.notifyAll();
    }
}
