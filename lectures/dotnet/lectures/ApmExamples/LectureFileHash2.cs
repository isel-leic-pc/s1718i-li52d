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
    public class LectureFileHash2 : IDisposable
    {
        private static readonly int BlockSize = 4 * 1024 * 1024;

        private readonly ManualResetEvent manualResetEvent;
        private readonly Stream stream;
        private readonly SHA1 hash;
        private readonly byte[] buffer;

        public LectureFileHash2(string fileName)
        {
            manualResetEvent = new ManualResetEvent(false);
            hash = SHA1.Create();
            buffer = new byte[BlockSize];
            stream = new FileStream(fileName,
                FileMode.Open, FileAccess.Read,
                FileShare.None, 4 * 1024, useAsync: true);
        }

        public void Start()
        {
            stream.BeginRead(buffer, 0, BlockSize, Callback, null);
        }

        private void Callback(IAsyncResult ar)
        {
            int len = stream.EndRead(ar);
            if (len != 0)
            {
                hash.TransformBlock(buffer, 0, len, buffer, 0);
                stream.BeginRead(buffer, 0, BlockSize, Callback, null);
            }
            else
            {
                hash.TransformFinalBlock(buffer, 0, 0);
                manualResetEvent.Set();
            }

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
