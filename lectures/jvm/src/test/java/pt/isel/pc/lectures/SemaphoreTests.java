package pt.isel.pc.lectures;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SemaphoreTests {

    private static Logger log = LoggerFactory.getLogger(SemaphoreTests.class);

    @Test
    public void invariant_load_test() throws Throwable {

        final int totalUnits = 10;
        final int totalThreads = 2*totalUnits+1;
        final int testTime = 5_000;

        final FifoNArySemaphore sem = new FifoNArySemaphore(totalUnits);

        final ConcurrentLinkedQueue<Throwable> q = new ConcurrentLinkedQueue<>();
        final AtomicInteger counter = new AtomicInteger(0);
        final List<Thread> ths = new ArrayList<>(totalThreads);
        final AtomicBoolean end = new AtomicBoolean(false);

        log.info("start with {} available units", totalUnits);
        for(int i = 0 ; i<totalThreads ; ++i){
            Thread th = new Thread(() -> {
                try {
                    Random rg = new Random();
                    while(end.get() != true) {
                        //int units = rg.nextInt(totalUnits / 2 - 1) + 1;
                        int units = 1;
                        sem.acquire(units, Integer.MAX_VALUE);
                        int observedCounter = counter.addAndGet(units);
                        assertTrue(observedCounter <= totalUnits);
                        log.info("acquired {} units, observed {} units", units, observedCounter);
                        Thread.sleep(rg.nextInt(100));
                        observedCounter = counter.addAndGet(-units);
                        assertTrue(observedCounter >= 0);
                        sem.release(units);
                    }
                }catch(Throwable e){
                    q.add(e);
                }
            });
            ths.add(th);
            th.start();
        }
        Thread.sleep(testTime);
        end.set(true);
        for(Thread th : ths) {
            th.join();
        }
        Throwable e = q.poll();
        if(e != null) {
            throw e;
        }
        log.info("end");
    }

    @Test
    public void invariant_load_test_2() throws Throwable {

        final int totalUnits = 10;
        final int totalThreads = 2*totalUnits+1;
        final int testTime = 5_000;
        final int joinTimeout = 1_000;

        final FifoNArySemaphore sem = new FifoNArySemaphore(totalUnits);

        final TestThreadRunner runner = new TestThreadRunner();
        final AtomicInteger counter = new AtomicInteger(0);

        log.info("start with {} available units", totalUnits);
        for(int i = 0 ; i<totalThreads ; ++i){
            Thread th = runner.start(Integer.toString(i), end -> {
                    Random rg = new Random();
                    while(end.get() != true) {
                        int units = rg.nextInt(totalUnits / 2 - 1) + 1;
                        sem.acquire(units, Integer.MAX_VALUE);
                        int observedCounter = counter.addAndGet(units);
                        assertTrue(observedCounter <= totalUnits);
                        log.info("acquired {} units, observed {} units", units, observedCounter);
                        Thread.sleep(rg.nextInt(100));
                        observedCounter = counter.addAndGet(-units);
                        assertTrue(observedCounter >= 0);
                        sem.release(units);
                    }

            });
        }
        runner.waitForTestEnd(testTime, joinTimeout);
        log.info("end");
    }

    @Test
    public void acquire_follows_fifo_order() throws Throwable {

        final int totalUnits = 2;
        final int totalThreads = 6;
        final int testTime = 5_000;
        final int joinTimeout = 1_000;

        final FifoNArySemaphore sem = new FifoNArySemaphore(0);

        final TestThreadRunner runner = new TestThreadRunner();
        final AtomicInteger counter = new AtomicInteger(0);

        log.info("start with {} available units", totalUnits);
        for(int i = 0 ; i<totalThreads ; ++i){
            final int ix = i;
            Thread th = runner.start(Integer.toString(i), end -> {
                Random rg = new Random();
                boolean isMultipleOf3 = ix % 3 == 0;
                int units = isMultipleOf3 ? 2 : 1;
                while(end.get() != true) {
                    sem.acquire(units, Integer.MAX_VALUE);
                    try {
                        log.info("acquired {} units", units);
                        int observedCounter = counter.getAndIncrement();
                        boolean mustBeMultipleOf3 = observedCounter % 3 == 0;
                        assertEquals(mustBeMultipleOf3, isMultipleOf3);
                        Thread.sleep(rg.nextInt(100));
                    }finally{
                        log.info("releasing {} units", units);
                        sem.release(units);
                    }
                }
            });
            Thread.sleep(200);
        }
        sem.release(totalUnits);
        runner.waitForTestEnd(testTime, joinTimeout);
        log.info("end");
    }
}

