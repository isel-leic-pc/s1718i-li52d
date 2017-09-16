package pt.isel.pc.lectures;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadCreationTests {

    private static Logger log = LoggerFactory.getLogger(ThreadCreationTests.class);

    @Test
    public void first() throws InterruptedException {

        //final Logger log = LoggerFactory.getLogger(ThreadCreationTests.class);

        log.info("Before new thread");



        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                 log.info("I'm running");
            }
        });
        th.start();
        //th.run();
        Thread.sleep(2000);
        log.info("thread state is {}", th.getState());
        log.info("After new thread");
        log.info("thread state is {}", th.getState());


    }

    static class MyThread extends Thread {
        @Override
        public void run() {
            log.info("I'm running");
        }
    }

    @Test
    public void second() {

        Thread th = new MyThread();
        log.info("Before new thread");
        th.start();
        log.info("After new thread");

    }

    @Test
    public void interruption() throws InterruptedException {
        Thread th = new Thread(new Runnable(){

            @Override
            public void run() {
                try {
                    log.info("Going to sleep");
                    Thread.sleep(60*1000);
                } catch (InterruptedException e) {
                    log.info("Interruped");
                }
            }
        });
        th.start();
        Thread.sleep(1000);
        th.interrupt();
    }

}
