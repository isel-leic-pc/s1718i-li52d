package pt.isel.pc.lectures;

import java.util.concurrent.atomic.AtomicInteger;

public class OptimizedSemaphore {

    private AtomicInteger counter;
    private Object mon = new Object();
    private volatile int waiters;

    public OptimizedSemaphore(int units) {
        this.counter = new AtomicInteger(units);
        this.waiters = 0;
    }

    private boolean tryAcquire() {
        while(true) {
            int observed = counter.get();
            if (observed == 0) {
                return false;
            }
            if (counter.compareAndSet(observed, observed - 1)) {
                return true;
            }
        }
    }

    public boolean acquire(int timeout) throws InterruptedException {

        if(tryAcquire()){
            return true;
        }

        if(Timeouts.noWait(timeout)){
            return false;
        }

        synchronized (mon) {

            waiters += 1;

            if(tryAcquire()) {
                waiters -= 1;
                return true;
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);

            while(true) {
                try {
                    mon.wait(remaining);
                }catch(InterruptedException e) {
                    if(counter.get() > 0) {
                        mon.notify();
                    }
                    waiters -= 1;
                    throw e;
                }
                if (tryAcquire()) {
                    waiters -= 1;
                    return true;
                }
                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)) {
                    waiters -= 1;
                    return false;
                }
            }
        }
    }

    public void release(){

        counter.incrementAndGet();

        if(waiters == 0) {
            return;
        }
        synchronized (mon) {
            mon.notify();
        }
    }
}
