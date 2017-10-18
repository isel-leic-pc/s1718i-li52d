package pt.isel.pc.lectures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestThreadRunner {

    private final ConcurrentLinkedQueue<Throwable> q = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean end = new AtomicBoolean(false);
    private final List<Thread> ths = new ArrayList<>();

    public Thread start(String name, ThrowableRunnable r){
        Thread th = new Thread(() -> {
            try{
                r.run(()->end.get());
            }catch(Throwable t){
                q.add(t);
            }
        });
        th.setName(name);
        th.start();
        ths.add(th);
        return th;
    }

    public void waitForTestEnd(int testTime, int joinTimeout) throws Throwable {
        Thread.sleep(testTime);
        end.set(true);
        long t = Timeouts.start(joinTimeout);
        long remaining = Timeouts.remaining(t);
        for(Thread th : ths){
            th.join(remaining);
            if(th.isAlive()){
                throw new TimeoutException("test timeout");
            }
            remaining = Timeouts.remaining(t);
            if(Timeouts.isTimeout(remaining)){
                throw new TimeoutException("test timeout");
            }
        }
        Throwable throwable = q.poll();
        if(throwable != null){
            throw throwable;
        }
    }
}
