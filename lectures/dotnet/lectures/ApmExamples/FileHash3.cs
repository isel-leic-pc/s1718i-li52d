using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ApmExamples
{
    public class Barrier
    {
        private readonly int _needed;
        private volatile int _current;
        private volatile int _ref = Environment.TickCount;
         
        public Barrier(int needed)
        {
            _needed = needed;
            _current = 0;
        }

        public bool Signal()
        {
            //Console.WriteLine(Environment.TickCount - _ref);
            return Interlocked.Increment(ref _current) == _needed;
        }

        public void Reset()
        {
            //_ref = Environment.TickCount;
            _current = 0;
        }
    }

    public class FileHash3 : IDisposable
    {

        private static readonly int Len = 4 * 1024 * 1024;

        private readonly ManualResetEvent manualResetEvent;
        private readonly Stream stream;
        private readonly SHA1 hash;
        private readonly byte[][] buffers;
        private readonly Barrier barrier = new Barrier(2);

        private void ToggleBuffers()
        {
            ReadBufferIx = (ReadBufferIx + 1) % 2;
        }

        private int ReadBufferIx = 0;

        public byte[] ReadBuffer { get { return buffers[ReadBufferIx]; } }
        public byte[] HashBuffer { get { return buffers[(ReadBufferIx + 1) % 2]; } }

        private static ThreadLocal<int> rcounter = new ThreadLocal<int>();

        public WaitHandle WaitHandle { get { return manualResetEvent; } }

        public FileHash3(string fileName)
        {
            stream = new FileStream(fileName,
                FileMode.Open, FileAccess.Read,
                FileShare.None, 4 * 1024, useAsync: true);
            hash = SHA1.Create();
            buffers = new byte[][]
            {
                new byte[Len], new byte[Len]
            };        
            manualResetEvent = new ManualResetEvent(false);
        }

        public void Start()
        {
            barrier.Signal();
            stream.BeginRead(ReadBuffer, 0, Len, Callback, null);
        }

        public byte[] Get()
        {
            return hash.Hash;
        }

        private void Callback(IAsyncResult ar)
        {
            Thread.Sleep(10);
            Callback2(ar);
        }

        private void Callback2(IAsyncResult ar)
        {
            if (!barrier.Signal())
            {
                return;
            }

            if (ar.CompletedSynchronously)
            {
                if (rcounter.Value == 3)
                {
                    ThreadPool.QueueUserWorkItem(_ => HandleCompletion(ar));
                }
                else
                {
                    rcounter.Value += 1;
                    HandleCompletion(ar);
                    rcounter.Value -= 1;
                }
            }
            else
            {
                HandleCompletion(ar);
            }            
        }

        private void HandleCompletion(IAsyncResult ar)
        {
            do
            {
                int len = stream.EndRead(ar);
                if (len == 0)
                {
                    hash.TransformFinalBlock(ReadBuffer, 0, 0);
                    manualResetEvent.Set();
                    return;
                }
                ToggleBuffers();
                barrier.Reset();
                ar = stream.BeginRead(ReadBuffer, 0, Len, Callback, null);
                hash.TransformBlock(HashBuffer, 0, len, HashBuffer, 0);
                //ar = stream.BeginRead(ReadBuffer, 0, Len, Callback, null);
                if (!barrier.Signal())
                {
                    return;
                }
            } while (true);          
        }

        public void Dispose()
        {
            stream.Dispose();
            hash.Dispose();
            WaitHandle.Dispose();
        }
    }
}
