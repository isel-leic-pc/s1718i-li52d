using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ApmExamples
{
    class Program
    {
        public static void Main(string[] args)
        {
            var sw = new Stopwatch();
            using (var h = new LectureFileHash4(@"c:\home\toremove.bin"))
            {
                sw.Start();
                var t = h.ComputeAsync();
                t.ContinueWith(task =>
                {
                    Console.WriteLine("Result {0}", BitConverter.ToString(task.Result));
                }).Wait(); 
                //sw.Stop();
                //Console.WriteLine("Took {0} to compute {1}", sw.Elapsed, BitConverter.ToString(hashValue));
            }
        }
    }
}
