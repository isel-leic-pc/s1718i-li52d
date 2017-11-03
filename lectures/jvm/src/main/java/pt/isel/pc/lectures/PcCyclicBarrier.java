package pt.isel.pc.lectures;

import java.util.concurrent.BrokenBarrierException;

public class PcCyclicBarrier {

    private final int parties;
    private final Object mon = new Object();
    private final BatchQueue<Request> q = new BatchQueue<>();

    private int counter;
    private boolean isBroken;

    public PcCyclicBarrier(int parties) {
        this.parties = parties;
        this.counter = parties;
        q.newBatch(new Request());
        isBroken = false;
    }

    public void await(int timeout)
            throws InterruptedException, BrokenBarrierException {
        synchronized (mon) {
            if(isBroken) {
                throw new BrokenBarrierException();
            }
            counter -= 1;
            if(counter == 0){
                mon.notifyAll();
                counter = parties;
                q.getCurrentBatch().state = Request.OPEN;
                q.newBatch(new Request());
                return;
            }
            if(Timeouts.noWait(timeout)){
                breakBarrier();
                throw new BrokenBarrierException();
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            Request r = q.getCurrentBatch();
            while(true) {
                try{
                    mon.wait(remaining);
                }catch(InterruptedException e){
                    if(r.state == Request.OPEN){
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if(r.state == Request.BROKEN){
                        Thread.currentThread().interrupt();
                        throw new BrokenBarrierException();
                    }
                    breakBarrier();
                    throw e;
                }
                if(r.state == Request.OPEN){
                    return;
                }
                if(r.state == Request.BROKEN){
                    throw new BrokenBarrierException();
                }
                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)){
                    breakBarrier();
                    throw new BrokenBarrierException();
                }
            }
        }
    }

    private void breakBarrier(){
        q.getCurrentBatch().state = Request.BROKEN;
        isBroken = true;
        counter = 0;
        mon.notifyAll();
    }

    public void reset() {
        synchronized (mon) {
            breakBarrier();
            counter = parties;
            isBroken = false;
            q.newBatch(new Request());
        }
    }

    private static class Request {
        public static final int CLOSED = 0;
        public static final int OPEN = 1;
        public static final int BROKEN = 2;
        public int state;

        public Request() {
            state = CLOSED;
        }
    }

}
