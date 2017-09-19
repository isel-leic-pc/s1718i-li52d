package pt.isel.pc.lectures;

public class SimpleSemaphore {

    private long counter;
    private final Object mon = new Object();

    public SimpleSemaphore(long initial) {
        counter = initial;
    }

    public void acquire() throws InterruptedException {
        synchronized (mon) {
            while(counter == 0){
                try {
                    mon.wait();
                }catch (InterruptedException e){
                    if(counter > 0){
                        mon.notify();
                    }
                    throw e;
                }
            }
            counter -= 1;
        }
    }

    public void release() {
        synchronized (mon) {
            counter += 1;
            mon.notify();
        }
    }

}
