package com.pleos.challenge.concierge

import android.os.SystemClock
import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.platform.*
import com.pleos.challenge.concierge.sdk.*
import org.robolectric.RuntimeEnvironment

internal class FakeLiveServices(val near: (Boolean) -> Unit) : LiveServices,
    VehiclePort, NavigationPort, LocationPort, VoicePort {
    override val source = SourceMode.LIVE
    override val vehicle: VehiclePort = this
    override val navigation: NavigationPort = this
    override val location: LocationPort = this
    override val voice: VoicePort = this
    var closeCount = 0
    var startFailure = false
    var writes = 0
    var actual: Double? = 23.0
    var holdCapability = false
    var writeFailure = false
    var holdRead = false
    val capabilities = mutableListOf<(Result<Capability>) -> Unit>()
    val reads = mutableListOf<(Result<Double>) -> Unit>()
    val soc = mutableListOf<(Result<Int>) -> Unit>()
    val brakes = mutableListOf<(Result<Boolean>) -> Unit>()
    override fun start() { check(!startFailure) }
    override fun close() { closeCount++ }
    override fun readSoc(callback: (Result<Int>) -> Unit) { soc.add(callback) }
    override fun readBrake(callback: (Result<Boolean>) -> Unit) { brakes.add(callback) }
    fun capabilityValue(): Capability = Capability(Access.READ_WRITE,
        mapOf("DRIVER" to Bounds(0.0, 100.0)), SystemClock.elapsedRealtime())
    override fun capability(control: Control, callback: (Result<Capability>) -> Unit) {
        if (holdCapability) capabilities.add(callback) else callback(Result.success(capabilityValue()))
    }
    override fun write(request: ControlRequest, callback: (Result<Unit>) -> Unit) {
        writes++
        callback(if (writeFailure) Result.failure(IllegalStateException("write failure")) else Result.success(Unit))
    }
    override fun read(request: ControlRequest, callback: (Result<Double>) -> Unit) {
        if (holdRead) reads.add(callback)
        else callback(actual?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no read")))
    }
    override fun requestRoute(request: RouteRequest, callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
    override fun isSupported(): Boolean = true
    override fun addAlert(alert: PickupAlert, callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
    override fun removeAlert(id: String, callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
    override fun speakOnDevice(text: String, callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
}

internal class LiveSessionFixture(private val failStart: Boolean = false) : AutoCloseable {
    lateinit var publish: (VehicleSnapshot) -> Unit
    val services = mutableListOf<FakeLiveServices>()
    var signalsClosed = 0
    val app = AppController(RuntimeEnvironment.getApplication(), false, {},
        { _, _, near -> FakeLiveServices(near).also { it.startFailure = failStart; services.add(it) } },
        { _, callback ->
            publish = callback
            object : SignalSource {
                override fun start() = Unit
                override fun close() { signalsClosed++ }
            }
        })
    init { app.start() }
    fun connect() {
        val t = app.now()
        publish(VehicleSnapshot(SourceMode.LIVE, true, true, Sample(0.0, t, SourceMode.LIVE),
            Sample(Gear.PARK, t, SourceMode.LIVE), Sample(UxState(false, 0), t, SourceMode.LIVE)))
    }
    fun disconnect() = publish(VehicleSnapshot(SourceMode.LIVE))
    override fun close() = app.close()
}
