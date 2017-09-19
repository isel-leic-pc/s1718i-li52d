package pt.isel.pc.lectures;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;

public class CounterTests {

    public static class SimpleCounter {

        private long counter;

        public SimpleCounter(long initial) {

            this.counter = initial;
        }

        public void incr() {
            counter += 1;
        }

        public long get() {
            return counter;
        }
    }

    public static final int nOfThreads = 100;
    public static final int nOfReps = 1000;

    @Test
    public void simpleCounterTest() throws InterruptedException {
        final SimpleCounter counter = new SimpleCounter(0);
        List<Thread> ths = new ArrayList<>(nOfThreads);
        for(int i = 0 ; i<nOfThreads ; ++i) {
            Thread th = new Thread(()->{
                for(int j=0 ; j<nOfReps ; ++j){
                    // simulates a request
                    counter.incr();
                }
            });
            th.start();
            ths.add(th);
        }

        // wait for termination
        for(Thread th : ths) {
            th.join();
        }

        assertEquals(nOfThreads * nOfReps, counter.get());

    }

    public static class SafeCounter {

        private long counter;
        private final Object mon = new Object();

        public SafeCounter(long initial) {

            this.counter = initial;
        }

        public void incr() {
            synchronized (this.mon) {
                this.counter += 1;
            }
        }

        public long get() {
            synchronized (this.mon) {
                return this.counter;
            }
        }
    }

    @Test
    public void safeCounterTest() throws InterruptedException {
        final SafeCounter counter = new SafeCounter(0);
        List<Thread> ths = new ArrayList<>(nOfThreads);
        for(int i = 0 ; i<nOfThreads ; ++i) {
            Thread th = new Thread(()->{
                for(int j=0 ; j<nOfReps ; ++j){
                    // simulates a request
                    counter.incr();
                }
            });
            th.start();
            ths.add(th);
        }

        // wait for termination
        for(Thread th : ths) {
            th.join();
        }

        assertEquals(nOfThreads * nOfReps, counter.get());

    }

    public static class SimpleIndexedCounter {

        private final Map<String, SafeCounter> map = new ConcurrentHashMap<>();
        private final Object mon = new Object();

        public void incr2(String key) {

            SafeCounter counter;

            synchronized (mon) {
                counter = map.get(key);
                if (counter == null) {
                    counter = new SafeCounter(1);
                    map.put(key, counter);
                    return;
                }
            }
            counter.incr();

        }

        public void incr(String key) {

            SafeCounter counter = map.computeIfAbsent(
                    key,
                    k -> new SafeCounter(0)
            );
            counter.incr();

        }

        public long get(String key){
            SafeCounter counter = map.get(key);
            return counter != null ? counter.get() : 0;
        }

    }

    @Test
    public void simpleIndexedCounterTest() throws InterruptedException {
        final SimpleIndexedCounter counter = new SimpleIndexedCounter();
        List<Thread> ths = new ArrayList<>(nOfThreads);
        for(int i = 0 ; i<nOfThreads ; ++i) {
            Thread th = new Thread(()->{
                for(int j=0 ; j<nOfReps ; ++j){
                    String key = Integer.toString(j%1000);
                    // simulates a request
                    counter.incr(key);
                }
            });
            th.start();
            ths.add(th);
        }

        // wait for termination
        for(Thread th : ths) {
            th.join();
        }

        for(int i = 0 ; i<1000 ; ++i) {
            assertEquals(
                    nOfThreads * nOfReps/1000,
                    counter.get(Integer.toString(i)));
        }

    }
}
