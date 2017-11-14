using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Samples
{
    class Program
    {
        static void Main(string[] args)
        {
            for (int i = 0; i < 10; ++i)
            {
                ThreadPool.QueueUserWorkItem(obj =>
                {
                    var v = (int)obj;
                    Console.WriteLine("Index {0} run on thread {1}", v, Thread.CurrentThread.ManagedThreadId);
                }, i);
            }
            Console.WriteLine("after");
            Console.ReadKey();
        }
    }
}
