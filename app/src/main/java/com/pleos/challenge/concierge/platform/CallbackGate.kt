package com.pleos.challenge.concierge.platform

import android.os.Handler
import android.os.Looper

/** Serializes every SDK callback, terminates once, and invalidates an entire service session. */
class CallbackGate(private val handler: Handler = Handler(Looper.getMainLooper())) {
    var closed = false
        private set
    private val pending = mutableSetOf<() -> Unit>()
    fun <T> request(timeoutMs: Long = 5_000, callback: (Result<T>) -> Unit,
                    invoke: ((Result<T>) -> Unit) -> Unit) {
        check(Looper.myLooper() == handler.looper)
        if (closed) { callback(Result.failure(IllegalStateException("Session closed"))); return }
        var complete = false
        lateinit var cancel: () -> Unit
        lateinit var timeout: Runnable
        fun finish(result: Result<T>) {
            if (complete) return
            complete = true
            handler.removeCallbacks(timeout)
            pending.remove(cancel)
            callback(result)
        }
        cancel = { finish(Result.failure(IllegalStateException("Session closed"))) }
        timeout = Runnable { finish(Result.failure(IllegalStateException("Service timeout"))) }
        pending.add(cancel)
        handler.postDelayed(timeout, timeoutMs)
        try { invoke { result -> handler.post { if (!closed) finish(result) } } }
        catch (error: Exception) { finish(Result.failure(error)) }
    }
    fun dispatch(action: () -> Unit) { handler.post { if (!closed) action() } }
    fun close() {
        closed = true
        pending.toList().forEach { it() }
        pending.clear()
    }
}
