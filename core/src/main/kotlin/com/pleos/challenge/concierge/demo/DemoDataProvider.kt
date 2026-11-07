package com.pleos.challenge.concierge.demo

import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.sdk.*

/** Explicit simulation. Cannot be constructed as a LIVE vehicle port. */
class DemoDataProvider(private val now: () -> Long) : VehiclePort {
    override val source = SourceMode.DEMO
    private val values = mutableMapOf<Pair<Control, String>, Double>()
    override fun capability(control: Control, callback: (Result<Capability>) -> Unit) {
        val bounds = when (control) {
            Control.FAN_SPEED -> Bounds(0.0, 7.0)
            Control.CABIN_TEMPERATURE -> Bounds(18.0, 28.0)
            Control.SEAT_HEAT, Control.SEAT_VENTILATION -> Bounds(0.0, 3.0)
            Control.STEERING_HEAT -> Bounds(0.0, 1.0)
            Control.WINDOW_POSITION -> Bounds(0.0, 100.0)
        }
        callback(Result.success(Capability(Access.READ_WRITE, mapOf("DRIVER" to bounds), now())))
    }
    override fun write(request: ControlRequest, callback: (Result<Unit>) -> Unit) {
        values[request.control to request.area] = request.value
        callback(Result.success(Unit))
    }
    override fun read(request: ControlRequest, callback: (Result<Double>) -> Unit) {
        val value = values[request.control to request.area]
        callback(if (value == null) Result.failure(IllegalStateException("No demo reading")) else Result.success(value))
    }
    fun snapshot(gear: Gear, speed: Double, soc: Int, restricted: Boolean, pickup: Boolean = false): VehicleSnapshot {
        require(speed.isFinite() && soc in 0..100)
        val time = now()
        return VehicleSnapshot(source, true, true, Sample(speed, time, source), Sample(gear, time, source),
            Sample(UxState(restricted, if (restricted) 1 else 0), time, source), Sample(soc, time, source), pickup)
    }
}
