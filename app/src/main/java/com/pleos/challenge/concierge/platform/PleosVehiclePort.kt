package com.pleos.challenge.concierge.platform

import android.content.Context
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.HvacTemperatureConfigArray
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.sdk.*

/** Real SDK calls only. DRIVER currently maps explicitly to front-left, shown as 앞좌석 왼쪽. */
class PleosVehiclePort(context: Context, private val safe: () -> Boolean) : VehiclePort {
    override val source = SourceMode.LIVE
    private val sdk = Vehicle(context.applicationContext)
    private val gate = CallbackGate()
    private val seat = SeatArea(SeatValue.ROW_1_LEFT)
    private val window = WindowArea(WindowValue.ROW_1_LEFT)
    private var initialized = false
    private var temperatureConfig: HvacTemperatureConfigArray? = null
    fun start() { initialized = runCatching { sdk.initialize() }.isSuccess }
    private fun access(config: CapabilityConfig) = Access.valueOf(config.accessType.name)
    private fun <T : Number> mapped(config: CapabilityConfig, bounds: AreaBounds<T>?) =
        Capability(access(config), if (bounds == null) emptyMap() else mapOf("DRIVER" to Bounds(bounds.minValue.toDouble(), bounds.maxValue.toDouble())), SystemClock.elapsedRealtime())
    override fun capability(control: Control, callback: (Result<Capability>) -> Unit) = gate.request(callback = callback) { done ->
        check(initialized)
        val fail: (Exception) -> Unit = { done(Result.failure(it)) }
        val seats: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit = { c, areas -> done(Result.success(mapped(c, areas[seat]))) }
        when (control) {
            Control.FAN_SPEED -> sdk.getHVAC().checkHvacFanSpeedCapabilities(seats, fail)
            Control.CABIN_TEMPERATURE -> sdk.getHVAC().checkHvacTemperatureCapabilities({ c, areas, table ->
                temperatureConfig = table
                val raw = mapped(c, areas[seat])
                val bound = raw.areas["DRIVER"]
                val valid = if (bound == null || table == null) emptyMap() else mapOf("DRIVER" to Bounds(
                    maxOf(bound.min, table.minCelsius.toDouble()), minOf(bound.max, table.maxCelsius.toDouble())))
                done(Result.success(raw.copy(areas = valid)))
            }, fail)
            Control.SEAT_HEAT -> sdk.getSeat().checkSeatTemperatureCapabilities(seats, fail)
            Control.SEAT_VENTILATION -> sdk.getSeat().checkSeatVentilationCapabilities(seats, fail)
            Control.STEERING_HEAT -> sdk.getSteeringWheel().checkHvacSteeringWheelHeatCapabilities({ c, bounds -> done(Result.success(mapped(c, bounds))) }, fail)
            Control.WINDOW_POSITION -> sdk.getWindow().checkWindowPositionCapabilities({ c: CapabilityConfig, areas: Map<WindowArea, AreaBounds<Int>> -> done(Result.success(mapped(c, areas[window]))) }, fail)
        }
    }
    override fun write(request: ControlRequest, callback: (Result<Unit>) -> Unit) = gate.request(callback = callback) { done ->
        check(initialized && safe() && request.area == "DRIVER")
        require(request.value.isFinite())
        if (request.control != Control.CABIN_TEMPERATURE) require(request.value % 1.0 == 0.0)
        else {
            val table = checkNotNull(temperatureConfig)
            require(table.incrementCelsius.isFinite() && table.incrementCelsius > 0)
            val steps = (request.value-table.minCelsius) / table.incrementCelsius
            require(kotlin.math.abs(steps-kotlin.math.round(steps)) < 0.001)
        }
        val fail: (Exception) -> Unit = { done(Result.failure(it)) }
        val seatDone: (SeatArea, Int?) -> Unit = { _, value -> done(if (value != null) Result.success(Unit) else Result.failure(IllegalStateException("No value"))) }
        when (request.control) {
            Control.FAN_SPEED -> sdk.getHVAC().setHvacFanSpeed(seat, request.value.toInt(), seatDone, fail)
            Control.CABIN_TEMPERATURE -> sdk.getHVAC().setHvacTemperature(seat, request.value.toFloat(), TemperatureUnit.CELSIUS, { _, _ -> done(Result.success(Unit)) }, fail)
            Control.SEAT_HEAT -> sdk.getSeat().setHvacSeatTemperature(seat, request.value.toInt(), seatDone, fail)
            Control.SEAT_VENTILATION -> sdk.getSeat().setHvacSeatVentilation(seat, request.value.toInt(), seatDone, fail)
            Control.STEERING_HEAT -> sdk.getSteeringWheel().setHvacSteeringWheelHeat(request.value.toInt(), { done(Result.success(Unit)) }, fail)
            Control.WINDOW_POSITION -> sdk.getWindow().setWindowPosition(window, request.value.toInt(), { _, _ -> done(Result.success(Unit)) }, fail)
        }
    }
    override fun read(request: ControlRequest, callback: (Result<Double>) -> Unit) = gate.request(callback = callback) { done ->
        check(initialized && request.area == "DRIVER")
        val fail: (Exception) -> Unit = { done(Result.failure(it)) }
        val intDone: (Int?) -> Unit = { done(if (it != null) Result.success(it.toDouble()) else Result.failure(IllegalStateException("No value"))) }
        when (request.control) {
            Control.FAN_SPEED -> sdk.getHVAC().getHvacFanSpeed(seat, intDone, fail)
            Control.CABIN_TEMPERATURE -> sdk.getHVAC().getHvacTemperature(seat, TemperatureUnit.CELSIUS, { done(if (it != null) Result.success(it.toDouble()) else Result.failure(IllegalStateException("No value"))) }, fail)
            Control.SEAT_HEAT -> sdk.getSeat().getHvacSeatTemperature(seat, intDone, fail)
            Control.SEAT_VENTILATION -> sdk.getSeat().getHvacSeatVentilationState(seat, intDone, fail)
            Control.STEERING_HEAT -> sdk.getSteeringWheel().getHvacSteeringWheelHeatState(intDone, fail)
            Control.WINDOW_POSITION -> sdk.getWindow().getCurrentWindowPosition(window, intDone, fail)
        }
    }
    fun readSoc(callback: (Result<Int>) -> Unit) = gate.request(callback = callback) { done ->
        check(initialized)
        sdk.getEvBattery().getEvBatteryStateOfCharge({ value ->
            done(if (value != null && value in 0..100) Result.success(value) else Result.failure(IllegalStateException("Invalid SOC")))
        }, { done(Result.failure(it)) })
    }
    fun readBrake(callback: (Result<Boolean>) -> Unit) = gate.request(callback = callback) { done ->
        check(initialized)
        sdk.getBrake().getCurrentBrakePedalStatus({ done(if (it != null) Result.success(it) else Result.failure(IllegalStateException("No value"))) }, { done(Result.failure(it)) })
    }
    fun close() { initialized = false; gate.close(); runCatching { sdk.release() } }
}
