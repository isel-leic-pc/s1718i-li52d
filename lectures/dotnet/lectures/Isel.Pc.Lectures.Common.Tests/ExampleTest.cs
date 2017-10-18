using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.ExceptionServices;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Xunit;

namespace Isel.Pc.Lectures.Common.Tests
{
    public class ExampleTest
    {
        [Fact]
        public void everything_is_ok()
        {
            Assert.Equal(1, 1);
        }

        [Fact]
        public void no_it_is_not()
        {
            Exception exception = null;
            var th = new Thread(() =>
            {
                try
                {
                    Assert.Equal(2, 1);
                }catch(Exception e)
                {
                    exception = e;
                }
            });
            th.Start();
            th.Join();
            if(exception != null)
            {
                ExceptionDispatchInfo.Capture(exception).Throw();
            }
        }
    }
}
