using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace Isel.Pc.Lectures.Common
{
    public class FifoSemaphore
    {
        class Request
        {
            public readonly int units;
            public readonly object condition;
            public bool ready;
            public Request(int units)
            {
                this.units = units;
                condition = new object();
                ready = false;
            }                    
        }

        private int counter;
        private readonly LinkedList<Request> q = new LinkedList<Request>();
        private readonly object mon = new object();

        public bool Acquire(int units, int timeout)
        {
            Monitor.Enter(mon);
            try
            {
                if (q.Count == 0 && counter >= units)
                {
                    counter -= units;
                    return true;
                }
                if (!TimeoutInstant.ShouldWait(timeout))
                {
                    return false;
                }
                var node = q.AddLast(new Request(units));
                
                var toi = new TimeoutInstant(timeout);
                while (true)
                {
                    try
                    {
                        SyncUtils.Wait(mon, node.Value.condition, toi.Remaining);
                    }
                    catch (ThreadInterruptedException)
                    {
                        if (node.Value.ready)
                        {
                            Thread.CurrentThread.Interrupt();
                            return true;
                        }
                        q.Remove(node);
                        grantAllAvailable();
                        throw;
                    }
                    if (node.Value.ready)
                    {
                        return true;
                    }
                    if(toi.IsTimeout)
                    {
                        q.Remove(node);
                        grantAllAvailable();
                        return false;
                    }
                }
            }
            finally
            {
                Monitor.Exit(mon);
            }
        }

        public void Release(int units)
        {
            bool wasInterrupted;
            SyncUtils.EnterUninterruptibly(mon, out wasInterrupted);
            try
            {
                counter += units;
                grantAllAvailable();
            }
            finally
            {
                Monitor.Exit(mon);
                if (wasInterrupted)
                {
                    Thread.CurrentThread.Interrupt();
                }
            }
        }

        private void grantAllAvailable()
        {
            while(q.Count != 0 && counter >= q.First.Value.units)
            {
                SyncUtils.Pulse(mon, q.First.Value.condition);
                counter -= q.First.Value.units;
                q.First.Value.ready = true;
                q.RemoveFirst();                
            }
        }
    }
}
