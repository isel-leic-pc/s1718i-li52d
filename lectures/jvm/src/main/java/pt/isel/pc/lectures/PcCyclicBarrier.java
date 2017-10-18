package pt.isel.pc.lectures;

import java.sql.Time;
import java.util.concurrent.BrokenBarrierException;

// INCOMPLETE
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

    public void await(int timeout) throws InterruptedException, BrokenBarrierException {
        synchronized (mon) {
            if(isBroken) {
                throw new BrokenBarrierException();
            }
            counter -= 1;
            if(counter == 0){
                mon.notifyAll();
                counter = parties;
                q.getCurrent().state = Request.OPEN;
                q.newBatch(new Request());
                return;
            }
            if(Timeouts.noWait(timeout)){
                breakBarrier();
                throw new BrokenBarrierException();
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            Request r = q.getCurrent();
            while(true) {
                try{
                    mon.wait(remaining);
                }catch(InterruptedException e){
                    if(r.state == Request.OPEN){
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if(r.state == Request.)
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
        q.getCurrent().state = Request.BROKEN;
        isBroken = true;
        mon.notifyAll();
    }

    public void reset() {
        synchronized (mon) {
            counter = parties;
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
