package com.pleos.challenge.concierge.platform

import android.content.Context
import android.util.Log
import com.pleos.challenge.concierge.sdk.*

interface SignalSource {
    fun start()
    fun close()
}

/** A connection owns its SDK instances; the Car connection monitor outlives them. */
interface LiveServices {
    val vehicle: VehiclePort
    val navigation: NavigationPort
    val location: LocationPort
    val voice: VoicePort
    fun start()
    fun readSoc(callback: (Result<Int>) -> Unit)
    fun readBrake(callback: (Result<Boolean>) -> Unit)
    fun close()
}

class PleosLiveServices(context: Context, safe: () -> Boolean, near: (Boolean) -> Unit) : LiveServices {
    override val vehicle = PleosVehiclePort(context, safe)
    override val navigation = PleosNavigationPort(context)
    override val location = PleosLocationPort(context, near)
    override val voice = PleosVoicePort(context)
    private var closed = false
    override fun start() {
        check(!closed)
        vehicle.start(); navigation.start(); location.start(); voice.start()
    }
    override fun readSoc(callback: (Result<Int>) -> Unit) = vehicle.readSoc(callback)
    override fun readBrake(callback: (Result<Boolean>) -> Unit) = vehicle.readBrake(callback)
    override fun close() {
        if (closed) return
        closed = true
        listOf(location::close, vehicle::close, navigation::close, voice::close).forEach { release ->
            runCatching(release).onFailure { Log.e("PleosLifecycle", "SDK release failed", it) }
        }
    }
}
