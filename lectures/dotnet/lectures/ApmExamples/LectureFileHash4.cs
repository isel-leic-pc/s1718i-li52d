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
    public class LectureFileHash4 : IDisposable
    {
        private static readonly int BlockSize = 4 * 1024 * 1024;
        private TaskCompletionSource<byte[]> tcs;
        private readonly Stream stream;
        private readonly SHA1 hash;
        private readonly byte[] buffer;

        public LectureFileHash4(string fileName)
        {
            tcs = new TaskCompletionSource<byte[]>();
            hash = SHA1.Create();
            buffer = new byte[BlockSize];
            stream = new FileStream(fileName,
                FileMode.Open, FileAccess.Read,
                FileShare.None, 4 * 1024, useAsync: true);
        }

        public Task<byte[]> ComputeAsync()
        {
            stream.BeginRead(buffer, 0, BlockSize, Callback, null);
            return tcs.Task;
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
                tcs.SetResult(hash.Hash);
            }

        }   

        public void Dispose()
        {
            stream.Dispose();
            hash.Dispose();    
        }
    }
}
