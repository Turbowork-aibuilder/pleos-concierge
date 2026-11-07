package com.pleos.challenge.concierge.platform

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.pleos.challenge.concierge.core.*
import java.util.concurrent.Executors

/** Car callbacks + bounded polling. Never executes a blocking getProperty on the UI thread. */
class CarSignals(private val context: Context, private val onState: (VehicleSnapshot) -> Unit) : SignalSource {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var closed = false
    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var restrictions: CarUxRestrictionsManager? = null
    private var state = VehicleSnapshot(SourceMode.LIVE)
    private var polling = false
    private var generation = 0
    private val timing = mutableMapOf<Int, SignalTiming>()
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { value ->
        main.post { if (!closed && state.connected) { state = state.copy(ux = uxSample(value)); publish() } }
    }
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            main.post { if (!closed && state.connected) { update(value, false); publish() } }
        }
        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            main.post {
                if (!closed) {
                    state = when (propertyId) {
                        VehiclePropertyIds.PERF_VEHICLE_SPEED -> state.copy(speed = null)
                        VehiclePropertyIds.GEAR_SELECTION -> state.copy(gear = null)
                        else -> state
                    }; publish()
                }
            }
        }
    }
    override fun start() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) { publish(); return }
        try {
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
                main.post {
                    if (closed) return@post
                    generation++
                    timing.clear()
                    if (!ready) { state = VehicleSnapshot(SourceMode.LIVE); properties = null; restrictions = null; publish() }
                    else try {
                        properties = service.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                        restrictions = service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        state = state.copy(connected = true, speedPermission = speedGranted())
                        restrictions?.registerListener(uxListener)
                        if (speedGranted()) runCatching { properties?.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, 5f) }
                        runCatching { properties?.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, 0f) }
                        publish()
                    } catch (_: Exception) { state = VehicleSnapshot(SourceMode.LIVE); publish() }
                }
            }
            main.post(tick)
        } catch (_: Exception) { state = VehicleSnapshot(SourceMode.LIVE); publish() }
    }
    private fun speedGranted() = context.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED
    private fun uxSample(value: CarUxRestrictions) = Sample(UxState(value.isRequiresDistractionOptimization, value.activeRestrictions), SystemClock.elapsedRealtime(), SourceMode.LIVE)
    private fun update(value: CarPropertyValue<*>, queried: Boolean) {
        val valid = value.status == CarPropertyValue.STATUS_AVAILABLE && timingPermits(value, queried)
        when (value.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> state = state.copy(speed = speedSample(value, valid))
            VehiclePropertyIds.GEAR_SELECTION -> state = state.copy(gear = gearSample(value, valid, queried))
        }
    }
    private fun speedSample(value: CarPropertyValue<*>, valid: Boolean): Sample<Double>? {
        if (!valid) return null
        return (value.value as? Float)?.takeIf { it.isFinite() }?.let { Sample(it.toDouble(), value.timestamp / 1_000_000, SourceMode.LIVE) }
    }
    private fun gearSample(value: CarPropertyValue<*>, valid: Boolean, queried: Boolean): Sample<Gear>? {
        if (!valid) return null
        val raw = value.value as? Int ?: return null
        // Successful fresh IPC confirms on-change gear even when its VHAL timestamp is old.
        return Sample(gearValue(raw), if (queried) SystemClock.elapsedRealtime() else value.timestamp / 1_000_000, SourceMode.LIVE)
    }
    private fun gearValue(raw: Int): Gear = when (raw) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_DRIVE -> Gear.DRIVE
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        else -> Gear.UNKNOWN
    }
    private fun timingPermits(value: CarPropertyValue<*>, queried: Boolean): Boolean {
        if (value.propertyId !in setOf(VehiclePropertyIds.PERF_VEHICLE_SPEED, VehiclePropertyIds.GEAR_SELECTION)) return false
        val now = SystemClock.elapsedRealtime()
        val previous = timing[value.propertyId] ?: SignalTiming()
        val next = if (queried) previous else inspectSignal(previous, value.timestamp / 1_000_000, now)
        timing[value.propertyId] = next
        return next.permits(now)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            state = state.copy(speedPermission = speedGranted())
            if (!state.speedPermission) state = state.copy(speed = null, gear = null)
            val pm = properties
            val ux = restrictions
            if (!polling && pm != null && ux != null) {
                polling = true
                val session = generation
                val readSpeed = state.speedPermission
                worker.execute {
                    val result = runCatching { Triple(if (readSpeed) pm.getProperty<Float>(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0) else null,
                        if (readSpeed) pm.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0) else null, ux.currentCarUxRestrictions) }
                    main.post {
                        polling = false
                        if (!closed && generation == session) {
                            result.onSuccess { (speed, gear, value) ->
                                if (speed != null) update(speed, true) else state = state.copy(speed = null)
                                if (gear != null) update(gear, true) else state = state.copy(gear = null)
                                state = state.copy(ux = uxSample(value))
                            }
                                .onFailure { state = state.copy(speed = null, gear = null, ux = null) }
                            publish()
                        }
                    }
                }
            }
            publish() // UI guard independently expires signals even if a getter stalls.
            main.postDelayed(this, 250)
        }
    }
    private fun publish() { if (!closed) onState(state) }
    override fun close() {
        closed = true; generation++
        main.removeCallbacksAndMessages(null)
        runCatching { properties?.unregisterCallback(callback) }
        runCatching { restrictions?.unregisterListener() }
        runCatching { car?.disconnect() }
        worker.shutdownNow()
    }
}
