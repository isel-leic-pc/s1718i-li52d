using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ApmExamples
{
    public class AsyncBarrier
    {
        private readonly int limit;
        private volatile int counter;
  
        public AsyncBarrier(int limit)
        {
            this.limit = limit;
            counter = 0;
        }

        public bool Signal()
        {
            return Interlocked.Increment(ref counter) == limit;
        }

        public void Reset()
        {
            counter = 0;
        }
    }

    public class LectureFileHash3 : IDisposable
    {
        private static readonly int BlockSize = 4 * 1024 * 1024;

        private readonly ManualResetEvent manualResetEvent;
        private readonly Stream stream;
        private readonly SHA1 hash;
        private readonly byte[][] buffers;
        private static ThreadLocal<int> rcounter = new ThreadLocal<int>();
        private int readBufferIx = 0;
        private readonly AsyncBarrier barrier = new AsyncBarrier(2);

        private byte[] ReadBuffer
        {
            get { return buffers[readBufferIx]; }
        }

        private byte[] HashBuffer
        {
            get { return buffers[(readBufferIx + 1) % 2]; }
        }

        private void ToggleBuffers()
        {
            readBufferIx = (readBufferIx + 1) % 2;
        }

        private static readonly int ReentrancyLimit = 10;

        public LectureFileHash3(string fileName)
        {
            manualResetEvent = new ManualResetEvent(false);
            hash = SHA1.Create();
            buffers = new byte[][] {
                new byte[BlockSize],
                new byte[BlockSize]
            };
            stream = new FileStream(fileName,
                FileMode.Open, FileAccess.Read,
                FileShare.None, 4 * 1024, useAsync: true);
        }

        public void Start()
        {
            barrier.Signal();
            stream.BeginRead(ReadBuffer, 0, BlockSize, Callback, null);
        }

        private void Callback(IAsyncResult ar)
        {
            Thread.Sleep(5);
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
                if (rcounter.Value + 1 > ReentrancyLimit)
                {
                    ThreadPool.QueueUserWorkItem(_ =>
                        Process(ar));
                }
                else
                {
                    rcounter.Value += 1;
                    Process(ar);
                    rcounter.Value -= 1;
                }
            }
            else
            {
                Process(ar);
            }            
        }

        private void Process(IAsyncResult ar)
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
                ar = stream.BeginRead(ReadBuffer, 0, BlockSize, Callback, null);
                hash.TransformBlock(HashBuffer, 0, len, HashBuffer, 0);
                if (!barrier.Signal())
                {
                    return;
                }
                // Process
            } while (true);
        }

        public WaitHandle WaitHandle { get { return manualResetEvent; } }

        public byte[] Get()
        {
            manualResetEvent.WaitOne();
            return hash.Hash;
        }

        public void Dispose()
        {
            stream.Dispose();
            hash.Dispose();
            manualResetEvent.Dispose();
        }
    }
}
