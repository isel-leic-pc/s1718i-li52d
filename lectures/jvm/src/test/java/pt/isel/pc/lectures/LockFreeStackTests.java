package pt.isel.pc.lectures;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class LockFreeStackTests {

    @Test
    public void no_elems_are_lost() throws Throwable {
        final int nOfThreads = 100;
        final TestThreadRunner runner = new TestThreadRunner();
        final LockFreeStack<Integer> stack = new LockFreeStack<>();
        final AtomicInteger sum = new AtomicInteger(0);
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicInteger inserts = new AtomicInteger(0);
        for(int i = 0 ; i<nOfThreads ; ++i) {
            runner.start("runner"+i, end -> {
               int v = 0;
               while(end.get() == false) {
                   stack.push(v);
                   inserts.incrementAndGet();
                   sum.addAndGet(v);
                   count.incrementAndGet();
                   stack.pop().ifPresent( value -> {
                       sum.addAndGet(-value);
                       count.decrementAndGet();
                   });
                   v += 1;
               }
            });
        }
        runner.waitForTestEnd(5000, 1000);
        while(true){
            Optional<Integer> ov = stack.pop();
            if(!ov.isPresent()) break;
            count.decrementAndGet();
            sum.addAndGet(-ov.get());
        }
        assertEquals(0, count.get());
        assertEquals(0, sum.get());
        System.out.println(inserts.get());
    }

}
