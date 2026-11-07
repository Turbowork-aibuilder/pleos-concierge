package com.pleos.challenge.concierge

import com.pleos.challenge.concierge.core.AppMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** Next guidance step of the scripted DEMO route; road names are fictional. */
enum class Maneuver(val arrow: String, val verb: String) {
    RIGHT("↱", "우회전"), LEFT("↰", "좌회전"), STRAIGHT("↑", "직진"), ARRIVED("●", "도착")
}

/** Scripted route for the DEMO flavor; never represents live GPS or road geometry. */
data class DemoRoute(val progress: Float, val arrived: Boolean, val destination: String) {
    private val clamped: Float get() = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val meters: Int get() = ((1f - clamped) * 1200).toInt()
    val maneuver: Maneuver get() = when {
        arrived -> Maneuver.ARRIVED
        clamped < .35f -> Maneuver.RIGHT
        clamped < .6f -> Maneuver.LEFT
        else -> Maneuver.STRAIGHT
    }
    val nextMeters: Int get() = when (maneuver) {
        Maneuver.RIGHT -> (420 - clamped * 1200).toInt().coerceAtLeast(10)
        Maneuver.LEFT -> (720 - clamped * 1200).toInt().coerceAtLeast(10)
        Maneuver.STRAIGHT -> meters
        Maneuver.ARRIVED -> 0
    }
    val nextRoad: String get() = when (maneuver) {
        Maneuver.RIGHT -> "에코대로"
        Maneuver.ARRIVED -> destination
        else -> "픽업길"
    }
    val instruction: String get() = when (maneuver) {
        Maneuver.ARRIVED -> "목적지 도착"
        Maneuver.STRAIGHT -> "↑  목적지까지 직진"
        else -> "${maneuver.arrow}  $nextMeters m · ${maneuver.verb}"
    }
    val minutes: Int get() = if (arrived) 0 else (meters / 300 + 1).coerceAtMost(4)
    val distanceText: String get() = String.format(Locale.US, "%.1f km", ceil(meters / 100f) / 10f)
    fun arrivalClock(now: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.ofEpochMilli(now + minutes * 60_000L))
}

class DemoNavigation(private val now: () -> Long) {
    private var startedAt: Long = now()
    private var destination: String = "픽업 장소"
    fun start(): Unit { startedAt = now() }
    fun setDestination(): Unit { destination = "설정한 목적지"; start() }
    fun state(mode: AppMode): DemoRoute? = when (mode) {
        AppMode.ECO_COACHING -> DemoRoute(((now() - startedAt).coerceAtLeast(0) / 72000f).coerceIn(0f, .98f), false, destination)
        AppMode.SMART_PICKUP -> DemoRoute(1f, true, destination)
        else -> null
    }
}
