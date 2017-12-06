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
    public class FileHash1 : IDisposable
    {

        private static readonly int Len = 4 * 1024 * 1024;

        private readonly ManualResetEvent manualResetEvent;
        private readonly Stream stream;
        private readonly SHA1 hash;
        private readonly byte[] buffer;

        public WaitHandle WaitHandle { get { return manualResetEvent; } }

        public FileHash1(string fileName)
        {
            stream = new FileStream(fileName,
                FileMode.Open, FileAccess.Read,
                FileShare.None, 4 * 1024, useAsync: true);
            hash = SHA1.Create();
            buffer = new byte[Len];
            manualResetEvent = new ManualResetEvent(false);
        }

        public void Start()
        {
            stream.BeginRead(buffer, 0, Len, Callback, null);
        }

        public byte[] Get()
        {
            return hash.Hash;
        }

        private void Callback(IAsyncResult ar)
        {
            var readBytes = stream.EndRead(ar);
            if (readBytes != 0)
            {
                hash.TransformBlock(buffer, 0, readBytes, buffer, 0);
                stream.BeginRead(buffer, 0, Len, Callback, null);
            }
            else
            {
                hash.TransformFinalBlock(buffer, 0, 0);
                manualResetEvent.Set();
            }
        }

        public void Dispose()
        {
            stream.Dispose();
            hash.Dispose();
            WaitHandle.Dispose();
        }
    }
}
