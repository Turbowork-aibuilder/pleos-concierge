package com.pleos.challenge.concierge.core

/** Gate bursts without replacing a moving sample with an earlier stationary value. */
data class SignalTiming(
    val lastMs: Long? = null, val blockedUntil: Long = 0,
    val hz: Double? = null, val jitterMs: Long? = null,
) {
    fun permits(now: Long): Boolean = now >= blockedUntil
}

private fun validTime(timestamp: Long, now: Long): Boolean =
    timestamp >= 0 && now >= timestamp && now - timestamp < 1000

private fun blocked(previous: SignalTiming, now: Long): SignalTiming =
    previous.copy(blockedUntil = now.coerceIn(0, Long.MAX_VALUE - 1000) + 1000)

fun inspectSignal(previous: SignalTiming, timestamp: Long, now: Long): SignalTiming {
    if (!validTime(timestamp, now)) return blocked(previous, now)
    val last = previous.lastMs ?: return previous.copy(lastMs = timestamp)
    val dt = timestamp - last
    if (dt <= 0) return blocked(previous, now)
    val measured = previous.copy(hz = 1000.0 / dt, jitterMs = kotlin.math.abs(dt - 200))
    if (dt < 5) return blocked(measured, now)
    return measured.copy(lastMs = timestamp)
}
