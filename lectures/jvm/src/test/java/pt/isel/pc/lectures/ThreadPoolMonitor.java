package pt.isel.pc.lectures; /**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Program to monitor worker thread injection in Java's ThreadPoolExecutor.
 *
 * Carlos Martins, May 2016
 *
 **/

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

//
// Monitores the thread pool worker thread injection and retirement.
//

public class ThreadPoolMonitor {

    //
    // Class that reports worker thread creation, reuse, and termination.
    //

    static class WorkerThreadReport {

        // Static fields whose access is protected by lock.

        private static final Lock lock = new ReentrantLock();
        private static long lastCreationTime = System.currentTimeMillis();
        private static int createdThreads;
        private static final List<WorkerThreadReport> reports = new ArrayList<WorkerThreadReport>();

        private static volatile boolean shutingDown = false;

        //
        // Instance fields used by each worker thread.
        //

        private final Thread theThread;
        private final long theThreadId;
        private long timeOfLastUse;
        private long exitTime;

        WorkerThreadReport() {
            // get the new thread identity
            theThread = Thread.currentThread();
            theThreadId = theThread.getId();
            lock.lock();
            long now, injectionDelay; int order;
            try {
                timeOfLastUse = now = System.currentTimeMillis();
                injectionDelay = now - lastCreationTime;
                lastCreationTime = now;
                order = ++createdThreads;
                reports.add(this);
            } finally {
                lock.unlock();
            }
            System.out.printf("-> injected %d-th worker #%d, after %d ms%n",
                    order, theThreadId, injectionDelay);
        }

        // Thread local that holds the report object for each worker thread.
        static ThreadLocal<WorkerThreadReport> report =
                new ThreadLocal<WorkerThreadReport>() {
                    public WorkerThreadReport initialValue() {
                        return new WorkerThreadReport();
                    }
                };

        // Register or update a report for the current thread.
        static void registerWorker() {
            report.get().timeOfLastUse = System.currentTimeMillis();
        }

        // Returns the number of created threads
        static int createdThreads() {
            lock.lock();
            try {
                return createdThreads;
            } finally {
                lock.unlock();
            }
        }

        // Returns the currently active threads
        static int activeThreads() {
            lock.lock();
            try {
                return reports.size();
            } finally {
                lock.unlock();
            }
        }

        static void showThreads() {
            lock.lock();
            try {
                if (reports.size() == 0)
                    System.out.println("-- no worker threads alive");
                else {
                    System.out.printf("-- %d worker threads are still alive:", reports.size());
                    for (WorkerThreadReport r : reports) {
                        System.out.printf(" #%02d", r.theThreadId);
                    }
                    System.out.println();
                }
            } finally {
                lock.unlock();
            }
        }

        // The thread that monitors the worker thread's exit.
        static final Runnable exitMonitorThreadBody = new Runnable() {
            public void run() {
                int rsize;
                do {
                    List<WorkerThreadReport> exited = null;
                    lock.lock();
                    rsize = reports.size();
                    try {
                        for (int i = 0; i < reports.size(); ) {
                            WorkerThreadReport r = reports.get(i);
                            if (!r.theThread.isAlive()) {
                                reports.remove(i);
                                if (exited == null) {
                                    exited = new ArrayList<WorkerThreadReport>();
                                }
                                r.exitTime = System.currentTimeMillis();
                                exited.add(r);
                            } else {
                                i++;
                            }
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (exited != null) {
                        for(WorkerThreadReport r : exited) {
                            System.out.printf("--worker #%02d exited after shutdonw or %d s of inactivity%n",
                                    r.theThreadId, (r.exitTime - r.timeOfLastUse) / 1000);
                        }
                    }

                    // Sleep for a while.
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {}
                } while (!(shutingDown && rsize == 0));
            }
        };

        // Static constructor: start the exit monitor thread.

        private static final Thread exitThread;
        static {
            exitThread = new Thread(exitMonitorThreadBody);
            //exitThread.setDaemon(true);
            exitThread.start();
        }

        // shutdown thread report
        static void shutdownWorkerThreadReport() {
            shutingDown = true;
            try {
                exitThread.join();
            } catch (InterruptedException ie) {}
        }
    }

    //
    // Auxiliary methods
    //

    private static int getKey() throws IOException {
        int key = System.in.read();
        do {
            System.in.read();
        } while (System.in.available() != 0);
        return key;
    }

    private static void readln() {
        try {
            do {
                System.in.read();
            } while (System.in.available() != 0);
        } catch (IOException ioex) {}
    }

    private static int availableKeys() {
        do {
            try {
                return System.in.available();
            } catch (IOException ioex) {}
        } while (true);
    }

    private static void sleepUninterruptibly(long milliseconds) {
        long expiresAt = System.currentTimeMillis() + milliseconds;
        do {
            try {
                Thread.sleep(milliseconds);
                break;
            } catch (InterruptedException ie) {}
            milliseconds = expiresAt - System.currentTimeMillis();
        } while (milliseconds > 0);
    }

    private static boolean joinUninterruptibly(Thread toJoin, long millis) {
        do {
            try {
                toJoin.join(millis);
                return !toJoin.isAlive();
            } catch (InterruptedException ie) {}
        } while (true);
    }

    private static final AtomicInteger toSpinOn = new AtomicInteger();

    private static void spinWait(int times) {
        for (int i = 0; i < times; i++) {
            toSpinOn.incrementAndGet();
        }
    }

    /* Loop control's constants */
    private static final int ACTION_COUNT = 50;
    private static final int REPEAT_FOR = 10;

    // Thread Pool Executor's configuration
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int MAX_POOL_SIZE = /* Integer.MAX_VALUE  */ 4 * CORE_POOL_SIZE;

    /**
     *  If QUEUE_SIZE e greater or equal the number os tasks, only core pool size
     *  worker threads are injected.
     *  In order to inject up to maximum pool size threads the capacity of working queue
     *  must be ACTION_COUNT - MAX_POOL_SIZE.
     */
    //private static final int TP_QUEUE_SIZE = ACTION_COUNT;	// creates only CORE_POOL_SIZE workers
    private static final int TP_QUEUE_SIZE = ACTION_COUNT - MAX_POOL_SIZE; // creates only MAX_POOL_SIZE workers

    private static final int KEEP_ALIVE_SECONDS = 20;

    private static final ThreadPoolExecutor theThreadPool = new ThreadPoolExecutor(
            CORE_POOL_SIZE									/* int corePoolSize */,
            MAX_POOL_SIZE									/* int maximumPoolSize */,
            KEEP_ALIVE_SECONDS, TimeUnit.SECONDS			/* long keepAliveTime, TimeUnit unit */,
            new ArrayBlockingQueue<Runnable>(TP_QUEUE_SIZE)	/* BlockingQueue<Runnable> workQueue */,
            (runnable) -> new Thread(runnable) 				/* ThreadFactory threadFactory */,
            (runnable, executor) -> System.out.println("***runnable rejected") 	/* RejectedExecutionHandler handler */
    );


    private static void shutdownPoolAndWaitTerminationUninterruptibly() {
        theThreadPool.shutdown();
        do {
            try {
                theThreadPool.awaitTermination(1 * 6, TimeUnit.SECONDS);
                return;
            } catch (InterruptedException ie) {}
        } while (true);
    }

    public static void main(String[] args) {

        System.out.printf("%n--processors: %d; core pool size: %d; maximum pool size: %d, keep alive time: %d s%n",
                Runtime.getRuntime().availableProcessors(), theThreadPool.getCorePoolSize(),
                theThreadPool.getMaximumPoolSize(), KEEP_ALIVE_SECONDS);

        System.out.print("--hit <enter> to start test and <enter> again to terminate...");
        readln();

        // Allows timeout on core pool threads! Comment for do not allow!
        theThreadPool.allowCoreThreadTimeOut(true);

        for (int i = 0; i < ACTION_COUNT; i++) {
            final int targ = i;
            theThreadPool.execute(() -> {
                WorkerThreadReport.registerWorker();
                long tid = Thread.currentThread().getId();
                System.out.printf("-->Action(%02d, #%02d)%n", targ, tid);
                for (int n = 0; n < REPEAT_FOR; n++) {
                    WorkerThreadReport.registerWorker();
                    /**
                     * Uncomment one one the following lines in order to select the type of load
                     *
                     * Warning: The thread injection dynamics does not depend on the type of load!
                     */
                    spinWait(1000000);					// CPU-bound load
                    //sleepUninterruptibly(100);		// I/O-bound load
                }
                System.out.printf("<--Action(%02d, #%02d)%n", targ, tid);
            });
        }
        long delay = 50;
        outerLoop:
        do {
            long till = System.currentTimeMillis() + delay;
            do {
                if (availableKeys() > 0) {
                    break outerLoop;
                }
                sleepUninterruptibly(15);
            } while (System.currentTimeMillis() < till);
            delay += 100;

            //
            // Comment the next statement to allow worker thread retirement!
            //
			/*
			theThreadPool.execute(() -> {
				WorkerThreadReport.registerWorker();
				System.out.printf("ExtraAction() --><-- on worker thread #%02d%n", Thread.currentThread().getId());
			});
			*/

        } while (true);

        // Initiate an ordely pool shutdown, and waits until all already submitted tasks to complete
        // The tasks submitted to the pool after it initiates the shutdown are rejected!
        shutdownPoolAndWaitTerminationUninterruptibly();

        // Show the worker thread usage
        System.out.printf("%n-- %d worker threads were injected%n", WorkerThreadReport.createdThreads());
        WorkerThreadReport.showThreads();

        // Shutdown workwer thread report
        WorkerThreadReport.shutdownWorkerThreadReport();
    }
}
