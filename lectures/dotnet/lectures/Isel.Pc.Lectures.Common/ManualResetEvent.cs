using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Isel.Pc.Lectures.Common
{
    public class ManualResetEvent
    {
        private readonly object mon = new object();
        private volatile bool signaled;
        private volatile int waiters;
        private int setVersion;

        public ManualResetEvent()
        {
            signaled = false;
            waiters = 0;
            setVersion = 0;
        }

        public bool Wait(int timeout)
        {
            if (signaled)
            {
                return true;
            }
            if (!TimeoutInstant.ShouldWait(timeout))
            {
                return false;
            }
            lock (mon)
            {
                waiters += 1;
                Interlocked.MemoryBarrier();
                if(signaled == true)
                {
                    waiters -= 1;
                    return true;
                }
                TimeoutInstant ti = new TimeoutInstant(timeout);
                int myVersion = setVersion;
                while(true)
                {
                    try
                    {
                        Monitor.Wait(mon, ti.Remaining);
                    }
                    catch (Exception)
                    {
                        waiters -= 1;
                        throw;
                    }
                    if (signaled || myVersion != setVersion)
                    {
                        waiters -= 1;
                        return true;
                    }
                    if (ti.IsTimeout)
                    {
                        waiters -= 1;
                        return false;
                    }
                }
            }
        }

        public void Set()
        {
            signaled = true;
            Interlocked.MemoryBarrier();
            if(waiters != 0)
            {
                SyncUtils.EnterUninterruptibly(mon, out bool wasInterrupted);
                setVersion += 1;
                try
                {
                    Monitor.PulseAll(mon);
                }
                finally
                {
                    Monitor.Exit(mon);
                    if(wasInterrupted)
                    {
                        Thread.CurrentThread.Interrupt();
                    }
                }
            }
        }

        public void Reset()
        {
            signaled = false;
        }

    }
}
