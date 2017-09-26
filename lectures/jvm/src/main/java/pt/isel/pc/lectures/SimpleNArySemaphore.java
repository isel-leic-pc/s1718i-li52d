package pt.isel.pc.lectures;

import java.sql.Time;

public class SimpleNArySemaphore {

    private long counter;
    private final Object mon = new Object();

    public SimpleNArySemaphore(long initial){
        this.counter = initial;
    }

    public boolean acquire(long units, long timeout) throws InterruptedException {
        synchronized (mon){
            if(counter >= units){
                counter -= units;
                return true;
            }
            if(Timeouts.noWait(timeout)){
                return false;
            }
            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);
            while(true){
                mon.wait(remaining);

                if(counter >= units) {
                    counter -= units;
                    return true;
                }

                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)){
                    return false;
                }
            }
        }
    }

    public void release(long units){
        synchronized (mon){
            counter += units;
            mon.notifyAll();
        }
    }
}
