package pt.isel.pc.lectures;

public class SimpleSemaphore {

    private long counter;
    private final Object mon = new Object();

    public SimpleSemaphore(long initial) {
        counter = initial;
    }

    public boolean acquire(long timeout) throws InterruptedException {
        synchronized (mon) {

            if(counter > 0){
                counter -= 1;
                return true;
            }
            if(Timeouts.noWait(timeout)){
                return false;
            }

            long t = Timeouts.start(timeout);
            long remaining = Timeouts.remaining(t);

            while(true){
                try {
                    mon.wait(remaining);
                }catch (InterruptedException e){
                    if(counter > 0){
                        mon.notify();
                    }
                    throw e;
                }

                if(counter > 0){
                    counter -= 1;
                    return true;
                }

                remaining = Timeouts.remaining(t);
                if(Timeouts.isTimeout(remaining)){
                    return false;
                }
            }
        }
    }

    public void release() {
        synchronized (mon) {
            counter += 1;
            mon.notify();
        }
    }

}
