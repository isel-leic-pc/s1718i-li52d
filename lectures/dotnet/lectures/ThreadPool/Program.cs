/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * Program to monitor worker thread injection in .NET ThreadPool.
 *
 * Carlos Martins, May 20016
 *
 **/

// comment the next line in order to monitor pool with an I/O bound load
//#define CPU_BOUND_LOAD

using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

class ThreadPoolMonitor
{

    // Class that reports worker thread creation, reuse, and retirement.

    internal class WorkerThreadReport
    {

        // Static fields whose access is protected by the _lock's lock.

        private static readonly object _lock = new object();
        private static int lastCreationTime = Environment.TickCount;
        private static int createdThreads;
        private static readonly List<WorkerThreadReport> reports = new List<WorkerThreadReport>();

        // Instance fields used by each worker thread.

        private readonly Thread theThread;
        private readonly int theThreadId;
        private int timeOfLastUse;
        private int exitTime;

        internal WorkerThreadReport()
        {
            theThread = Thread.CurrentThread;
            theThreadId = theThread.ManagedThreadId;
            int order, injectionDelay, now;
            lock (_lock)
            {
                timeOfLastUse = now = Environment.TickCount;
                injectionDelay = now - lastCreationTime;
                lastCreationTime = now;
                order = ++createdThreads;
                reports.Add(this);
            }
            Console.WriteLine("--> injected the {0}-th worker #{1}, after {2} ms",
                               order, theThreadId, injectionDelay);
        }

        // Thread local that holds the report for each worker thread.

        internal static ThreadLocal<WorkerThreadReport> report =
                        new ThreadLocal<WorkerThreadReport>(() => new WorkerThreadReport());

        // Register or update a report for the current thread.
        internal static void RegisterWorker()
        {
            report.Value.timeOfLastUse = Environment.TickCount;
        }

        // Returns the number of created threads
        internal static int CreatedThreads
        {
            get { lock (_lock) return createdThreads; }
        }

        // Returns the number of active threads
        internal static int ActiveThreads
        {
            get { lock (_lock) return reports.Count; }
        }

        // Displays the alive worker threads
        internal static void ShowThreads()
        {
            lock (_lock)
            {
                if (reports.Count == 0)
                    Console.WriteLine("-- no worker are threads alive");
                else
                    Console.Write("-- {0} worker threads are alive:", reports.Count);
                foreach (WorkerThreadReport r in reports)
                {
                    Console.Write(" #{0}", r.theThreadId);
                }
                Console.WriteLine();
            }
        }

        // Thread that monitors the worker thread's exit.
        private static void ExitMonitorThreadBody()
        {
            int rcount;
            do
            {
                List<WorkerThreadReport> exited = null;
                lock (_lock)
                {
                    rcount = reports.Count;
                    for (int i = 0; i < reports.Count;)
                    {
                        WorkerThreadReport r = reports[i];
                        if (!r.theThread.IsAlive)
                        {
                            reports.RemoveAt(i);
                            if (exited == null)
                            {
                                exited = new List<WorkerThreadReport>();
                            }
                            r.exitTime = Environment.TickCount;
                            exited.Add(r);
                        }
                        else
                            i++;
                    }
                }
                if (exited != null)
                {
                    foreach (WorkerThreadReport r in exited)
                    {
                        Console.WriteLine("--worker #{0} exited after {1} s of inactivity",
                            r.theThreadId, (r.exitTime - r.timeOfLastUse) / 1000);
                    }
                }

                // sleep for a while.
                try
                {
                    Thread.Sleep(50);
                }
                catch (ThreadInterruptedException)
                {
                    return;
                }
            } while (true);
        }

        // The exit thread
        private static Thread exitThread;

        // Static constructor: start the exit monitor thread.
        static WorkerThreadReport()
        {
            exitThread = new Thread(ExitMonitorThreadBody);
            exitThread.Start();
        }

        // shutdown thread report
        internal static void ShutdownWorkerThreadReport()
        {
            exitThread.Interrupt();
            exitThread.Join();
        }
    }

    private static int ACTION_COUNT = 50;
    private static int REPEAT_FOR = 500;

    static void Main()
    {
        int minWorker, minIocp, maxWorker, maxIocp;

#if CPU_BOUND_LOAD
		Console.WriteLine("\n-- Monitor .NET Thread Pool using a CPU-bound load\n");	
#else
        Console.WriteLine("\n-- Monitor .NET Thread Pool using a I/O-bound load\n");
#endif

        ThreadPool.GetMinThreads(out minWorker, out minIocp);
        ThreadPool.GetMaxThreads(out maxWorker, out maxIocp);

        //ThreadPool.SetMinThreads(2 * Environment.ProcessorCount, minIocp);
        Console.WriteLine("-- processors: {0}; min/max workers: {1}/{2}; min/max iocps: {3}/{4}\n",
                           Environment.ProcessorCount, minWorker, maxWorker, minIocp, maxIocp);

        Console.Write("--Hit <enter> to start, and then <enter> again to terminate...");
        Console.ReadLine();

        for (int i = 0; i < ACTION_COUNT; i++)
        {

            ThreadPool.QueueUserWorkItem((targ) => {
                WorkerThreadReport.RegisterWorker();
                int tid = Thread.CurrentThread.ManagedThreadId;
                Console.WriteLine("-->Action({0}, #{1:00})", targ, tid);
                for (int n = 0; n < REPEAT_FOR; n++)
                {
                    WorkerThreadReport.RegisterWorker();
#if CPU_BOUND_LOAD
					Thread.SpinWait(5000000);		// CPU-bound load
#else
                    Thread.Sleep(10);               // I/O-bound load
#endif
                }
                Console.WriteLine("<--Action({0}, #{1:00})", targ, tid);
            }, i);

        }
        int delay = 50;
        do
        {
            int till = Environment.TickCount + delay;
            do
            {
                if (Console.KeyAvailable)
                {
                    goto Exit;
                }
                Thread.Sleep(15);
            } while (Environment.TickCount < till);
            delay += 50;

            //
            // Comment the next statement to allow worker thread retirement!
            //
            /*
			ThreadPool.QueueUserWorkItem(_ => {
				WorkerThreadReport.RegisterWorker();
				Console.WriteLine("ExtraAction() --><-- on worker thread #{0}", Thread.CurrentThread.ManagedThreadId);
			});
			*/

        } while (true);
        Exit:
        Console.WriteLine("-- {0} worker threads were injected", WorkerThreadReport.CreatedThreads);
        WorkerThreadReport.ShowThreads();
        WorkerThreadReport.ShutdownWorkerThreadReport();
    }
}
