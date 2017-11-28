using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace TaskExamples
{
    class Program
    {

        static void LogContinuation()
        {
            Console.WriteLine("{0}:Continuation", Thread.CurrentThread.ManagedThreadId);
        }

        static void LogCompletion(object o)
        {
            Console.WriteLine("{0}: completed with {1}", Thread.CurrentThread.ManagedThreadId, o);
        }


        static void Main(string[] args)
        {
            int reps = 10;

            //var t = Task.Factory.StartNew(() =>
            //{
            //    Thread.Sleep(500);
            //    LogCompletion(1);
            //    return 1;
            //});

            var tcs = new TaskCompletionSource<int>();
            var t = tcs.Task;

            for(int i = 0; i < 10; ++i)
            {
                t = t.ContinueWith(antecendent =>
                {
                    LogContinuation();
                    Thread.Sleep(2000);
                    var res = antecendent.Result + 1;
                    LogCompletion(res);
                    return res;
                }, TaskContinuationOptions.ExecuteSynchronously);
                Console.WriteLine("-- end iteration --");
            }

            tcs.SetResult(1);
            Console.WriteLine("Before wait for result");
            Console.WriteLine("Result is {0}", t.Result);
        }

        static void Main_ContinueWith(string[] args)
        {
            Func<int, int> inc = i =>
            {
                Thread.Sleep(3000);
                LogCompletion(i + 1);
                return i + 1;
            };

            var t1 = Task.Factory.StartNew(() => inc(1));
            var t2 = Task.Factory.StartNew(() => inc(2));
            var t3 = Task.Factory.StartNew(() => inc(3));
            var t4 = Task.Factory.StartNew(() => inc(4));

            var t12 = Task.Factory.ContinueWhenAll(new[] { t1, t2 }, ts =>
            {
                LogContinuation();
                var dt = Task.Delay(3000);
                Console.WriteLine(Thread.CurrentThread.ManagedThreadId);
                return dt.ContinueWith(_ => {
                    Console.WriteLine(Thread.CurrentThread.ManagedThreadId);
                    LogCompletion(ts[0].Result + ts[1].Result);
                    return ts[0].Result + ts[1].Result;
                });
            }).Unwrap();

            var t34 = Task.Factory.ContinueWhenAll(new[] { t3, t4 }, ts =>
            {
                LogContinuation();
                Thread.Sleep(3000);
                LogCompletion(ts[0].Result + ts[1].Result);
                return ts[0].Result + ts[1].Result;
            });

            var t = Task.Factory.ContinueWhenAll(new[] { t12, t34 }, ts =>
            {
                LogContinuation();
                Thread.Sleep(3000);
                LogCompletion(ts[0].Result + ts[1].Result);
                return ts[0].Result + ts[1].Result;
            });

            Console.WriteLine("Before getting Result");
            var before = DateTimeOffset.Now;
            var res = t.Result;
            var span = DateTimeOffset.Now - before;
            Console.WriteLine("Result is {0}, obtained after {1}", res, span);
        }

        static void Main_TaskOfT(string[] args)
        {
            int nOfTasks = 8;
            var ts = new Task<string>[nOfTasks];
            for(int i = 0; i<nOfTasks; ++i)
            {
                int ix = i;
                Func<string> f = () =>
                {
                    Console.WriteLine("Computing the function");
                    Thread.Sleep(4000);
                    if(ix % 2 == 0)
                    {
                        throw new Exception();
                    }
                    return "done " + ix;
                };
                ts[ix] = Task.Factory.StartNew(f);
            }

            try
            {
                Task.WaitAll(ts);
            }
            catch (AggregateException e)
            {
                // ignoring exceptions because the status will be observed later
            }
            
            for(int i = 0; i<nOfTasks; ++i)
            {
                var t = ts[i];
                if(t.Status == TaskStatus.RanToCompletion)
                {
                    Console.WriteLine("{0} completed with {1}", i, t.Result);
                }
                else
                {
                    Console.WriteLine("{0} completed without result and status {1}", i, t.Status);
                }
            }
        }


        static void Main_Cancellation(string[] args)
        {
            CancellationTokenSource cts = new CancellationTokenSource();
            CancellationToken ct = cts.Token;

            Action a = () =>
            {
                while (true)
                {
                    Console.WriteLine("still doing my stuff");
                    if (ct.IsCancellationRequested)
                    {
                        throw new OperationCanceledException(ct);
                    }
                    var dt = Task.Delay(10000, ct);
                    Console.WriteLine("After calling Delay");
                    try
                    {
                        dt.Wait();
                    }catch(AggregateException e)
                    {
                        Console.WriteLine(dt.Status);
                        if(dt.Status == TaskStatus.Canceled)
                        {
                            throw new OperationCanceledException(ct);
                        }
                    }
                };
            };

            //var t = new Task(a, ct);
            var t = Task.Factory.StartNew(a, ct);

            Thread.Sleep(4000);
            Console.WriteLine(t.Status);
            Console.WriteLine("Requesting cancellation");
            cts.Cancel();
            while(t.Status != TaskStatus.Faulted 
                && t.Status != TaskStatus.Canceled
                && t.Status != TaskStatus.RanToCompletion)
            {
                // spinning
                Console.WriteLine(t.Status);
            }
            Console.WriteLine(t.Status);

            
        }

        static void Main_Exceptions(string[] args)
        {
            int nOfTasks = 8;
            var ts = new Task[nOfTasks];
            for (int i = 0; i < nOfTasks; ++i)
            {
                var ix = i;
                ts[i] = Task.Factory.StartNew(() =>
                {
                    Console.WriteLine("{0}: Task {1} started",
                        Thread.CurrentThread.ManagedThreadId, ix);
                    if (ix % 2 == 0)
                    {
                        throw new Exception("exception inside task");
                    }
                    Thread.Sleep(1000 * ix);
                    Console.WriteLine("{0}: Task {1} about to end",
                        Thread.CurrentThread.ManagedThreadId, ix);
                });
                Console.WriteLine("Task {0} is in state {1}", ix, ts[ix].Status);
            }
            try { 
                Task.WaitAll(ts);
            }catch(AggregateException ae)
            {
                foreach(var e in ae.InnerExceptions) {
                    Console.WriteLine("## {0} ##", e.Message);
                    Console.WriteLine(e.StackTrace);
                }
                Console.WriteLine("## AE ##");
                Console.WriteLine(ae.StackTrace);
            }
            Console.WriteLine("All tasks are terminated");
            for(var i = 0; i<nOfTasks; ++i) 
            {
                Console.WriteLine("Task {0} status is {1}", i, ts[i].Status);
                if(ts[i].Status == TaskStatus.Faulted)
                {
                    Console.WriteLine("Task {0} Exception is {1}", i, ts[i].Exception);
                }
            }

        }
    }
}
