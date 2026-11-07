package com.pleos.challenge.concierge.sdk

import com.pleos.challenge.concierge.core.SourceMode

data class Coordinate(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}
data class RouteRequest(val destination: Coordinate, val waypoints: List<Coordinate>) {
    init { require(waypoints.size <= 2) { "NaviHelper supports at most FIRST/SECOND waypoints" } }
}
enum class Transition { ENTER, EXIT }
data class PickupAlert(val id: String, val center: Coordinate, val radiusMeters: Double, val transitions: Set<Transition>) {
    init { require(id.isNotBlank() && radiusMeters.isFinite() && radiusMeters > 0 && transitions.isNotEmpty()) }
}
interface NavigationPort {
    val source: SourceMode
    fun requestRoute(request: RouteRequest, callback: (Result<Unit>) -> Unit)
}
class NavigationManager(private val port: NavigationPort, private val source: () -> SourceMode) {
    fun route(request: RouteRequest, callback: (Result<Unit>) -> Unit) {
        if (source() != port.source) { callback(Result.failure(IllegalStateException("Source mismatch"))); return }
        try { port.requestRoute(request, callback) } catch (e: Exception) { callback(Result.failure(e)) }
    }
}
interface LocationPort {
    val source: SourceMode
    fun isSupported(): Boolean
    fun addAlert(alert: PickupAlert, callback: (Result<Unit>) -> Unit)
    fun removeAlert(id: String, callback: (Result<Unit>) -> Unit)
}
class LocationAlertManager(private val port: LocationPort, private val source: () -> SourceMode) {
    fun register(alert: PickupAlert, permission: Boolean, callback: (Result<Unit>) -> Unit) {
        try {
            if (source() != port.source || !permission || !port.isSupported()) {
                callback(Result.failure(IllegalStateException("Location unavailable or permission denied"))); return
            }
            port.addAlert(alert, callback)
        } catch (e: Exception) { callback(Result.failure(e)) }
    }
    fun remove(id: String, callback: (Result<Unit>) -> Unit) {
        if (source() != port.source || id.isBlank()) { callback(Result.failure(IllegalStateException("Invalid alert removal"))); return }
        try { port.removeAlert(id, callback) } catch (e: Exception) { callback(Result.failure(e)) }
    }
}
interface VoicePort {
    val source: SourceMode
    /** Adapter must await service connection and audio focus. No secret/server registration here. */
    fun speakOnDevice(text: String, callback: (Result<Unit>) -> Unit)
}
class VoiceCoachManager(private val port: VoicePort, private val source: () -> SourceMode, private val now: () -> Long) {
    private var lastAt: Long? = null
    fun alert(message: String, callback: (Result<Unit>) -> Unit) {
        if (source() != port.source || message.isBlank()) { callback(Result.failure(IllegalArgumentException("Invalid voice request"))); return }
        val time = now()
        if (lastAt != null && time - lastAt!! < 30_000) { callback(Result.failure(IllegalStateException("Voice cooldown"))); return }
        lastAt = time
        try { port.speakOnDevice(message, callback) } catch (e: Exception) { callback(Result.failure(e)) }
    }
}
