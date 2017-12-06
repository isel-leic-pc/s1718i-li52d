/**
 *
 * ISEL, LEIC, Concurrent Programming
 *
 * A generic IAsyncResult implementation
 *
 * Carlos Martins, January 2017
 *
 **/

using System;
using System.Threading;

// Generic IAsyncResult implementation 

public class GenericAsyncResult<R> : IAsyncResult {
#pragma warning disable 420
	
	private const int STATE_BITS = (1 << 0) | (1 << 1), ONGOING = 0, COMPLETING = 1, COMPLETED = 2;
	private const int COMPLETED_SYNCHRONOUSLY = 1 << 2; 
	private const int RESULT_CALLED = 1 << 3; 
	private volatile int state;
	private volatile EventWaitHandle waitEvent;
	private readonly AsyncCallback userCallback;
	private readonly object userState;
	private R result;
	private Exception error;

	public GenericAsyncResult(AsyncCallback ucallback, object ustate, bool synchCompletion) {
		userCallback = ucallback;
		userState = ustate;
		state = ONGOING | (synchCompletion ? COMPLETED_SYNCHRONOUSLY : 0);
	}

	public GenericAsyncResult() : this(null, null, false) {}
	
	//
	// Returns a completed instance of GenericAsyncResult<R> with the specified result.
	//
	
	public static IAsyncResult FromResult(AsyncCallback ucallback, object ustate, R result, Exception error,
										  bool synchCompletion) {
		GenericAsyncResult<R> gar = new GenericAsyncResult<R>(ucallback, ustate, synchCompletion);
		gar.TrySet(result, error);
		return gar;
	}
		
	//
	// Try to complete the underlying asynchronous operation.
	//
		
	private bool TrySet(R result, Exception error) {
		int s;
		do {
			s = state;
			if ((s & STATE_BITS) != ONGOING)
				return false;
		} while (Interlocked.CompareExchange(ref state, s | COMPLETING, s) != s);
		// copy results
		this.result = result;
		this.error = error;
		// set the state to completed with a atomic instruction which is also a full-fence
		// we need to use CAS because of the RESULT_CALLED bit set when Result is called.
		do {
			s = state;
		} while (Interlocked.CompareExchange(ref state, (s & ~STATE_BITS) | COMPLETED, s) != s);
		
		// Set the asynchronous wait handle, if any
		if (waitEvent != null) {
			try {
				waitEvent.Set();
				// We can get ObjectDisposedExcption due a benign race, so ignore it!
			} catch (ObjectDisposedException) {}
		}
		// Calls the user callback if one is defined
		if (userCallback != null)
			userCallback(this);
		return true;				
	}

	// Try to complete the underlying asynchronous operation successfully
	public bool TrySetResult(R result) { return TrySet(result, null); }
	
	public void SetResult(R result) { TrySet(result, null);	}

	// Try to complete the underlying asynchronous operation with exception
	public bool TrySetException(Exception error) { return TrySet(default(R), error); }

	public void SetException(Exception error) { TrySet(default(R), error); }

	//
	// Complete the underlying asynchronous operation for backward compatibility.
	//
	/*
	public void OnComplete(R result, Exception error) {
		TrySet(result, error);
	}
	*/
	//---
	// The IAsyncResult interface's implementation.
	//---
		
	public bool IsCompleted { get { return (state & STATE_BITS) == COMPLETED; } }
		
	public bool CompletedSynchronously { get { return (state & COMPLETED_SYNCHRONOUSLY) != 0; } }
		
	public Object AsyncState { get { return userState; } }
		
	public WaitHandle AsyncWaitHandle {
		get {
			if (waitEvent == null) {
				bool completed = IsCompleted;
				EventWaitHandle done = new ManualResetEvent(completed);
				if (Interlocked.CompareExchange(ref waitEvent, done, null) == null) {
					if (completed != IsCompleted)
						done.Set();
				} else {
					done.Close();		// someone else already the event; so dispose this one!
				}
			}
			return waitEvent;
		}
	}

	//
	// Return the asynchronous operation's result or exception (called once by EndXxx())
	//

	public R Result {
		get {
			// EndXxx can only be called once!
			int s;
			do {
				if (((s = state) & RESULT_CALLED) != 0)
					throw new InvalidOperationException("EndXxx already called");					
			} while (Interlocked.CompareExchange(ref state, s | RESULT_CALLED, s) != s);
			if (!IsCompleted)
				AsyncWaitHandle.WaitOne();
			if (waitEvent != null)
				waitEvent.Close();
			if (error != null)
				throw error;
			return result;
		}
	}

#pragma warning restore 420
}
