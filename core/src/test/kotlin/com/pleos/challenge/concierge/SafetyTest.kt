package com.pleos.challenge.concierge

import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.demo.DemoDataProvider
import com.pleos.challenge.concierge.sdk.*
import kotlin.test.*

class SafetyTest {
    private var time = 10_000L
    private val demo = DemoDataProvider { time }
    private val guard = SafetyGuard()
    private fun parked() = demo.snapshot(Gear.PARK, 0.0, 72, false)

    @Test fun parkingIsAnAndCondition() {
        assertTrue(guard.evaluate(parked(), time).canControl)
        assertFalse(guard.evaluate(demo.snapshot(Gear.DRIVE, 0.0, 72, false), time).canControl)
        assertFalse(guard.evaluate(demo.snapshot(Gear.PARK, 1.0, 72, false), time).canControl)
    }
    @Test fun unknownStaleFutureAndNonfiniteSignalsFailClosed() {
        val base = parked()
        val invalid = listOf(base.copy(speed = null), base.copy(gear = null), base.copy(ux = null),
            base.copy(speedPermission = false), base.copy(connected = false),
            base.copy(speed = Sample(Double.NaN, time, SourceMode.DEMO)),
            base.copy(speed = Sample(Double.POSITIVE_INFINITY, time, SourceMode.DEMO)),
            base.copy(speed = Sample(0.0, time - 1_000, SourceMode.DEMO)),
            base.copy(speed = Sample(0.0, time + 1, SourceMode.DEMO)),
            base.copy(gear = Sample(Gear.PARK, time - 1_000, SourceMode.DEMO)),
            base.copy(ux = Sample(UxState(false, 0), time - 1_000, SourceMode.DEMO)))
        invalid.forEach { assertFalse(guard.evaluate(it, time).canControl, it.toString()) }
        assertTrue(guard.evaluate(base, time + 999).canControl)
    }
    @Test fun pickupCannotOverrideUxOrMovement() {
        val state = demo.snapshot(Gear.DRIVE, 15.0, 18, true, true)
        assertEquals(AppMode.ECO_COACHING, guard.evaluate(state, time).mode)
        assertEquals(AppMode.SMART_PICKUP, guard.evaluate(parked().copy(nearPickup = true), time).mode)
        assertFalse(guard.evaluate(parked().copy(ux = Sample(UxState(false, 8), time, SourceMode.DEMO)), time).canControl)
    }
    @Test fun sourceSwitchClearsDataAndMixedSamplesAreRejected() {
        val session = SessionState(SourceMode.DEMO)
        session.update(parked())
        session.switchMode(SourceMode.LIVE)
        assertNull(session.snapshot.speed)
        assertFalse(guard.evaluate(parked().copy(speed = Sample(0.0, time, SourceMode.LIVE)), time).canControl)
    }
    @Test fun reverseSpeedDisplayDoesNotGrantParkedControl() {
        assertEquals(36.0, speedKmH(-10.0))
        assertNull(speedKmH(Double.NaN))
        assertFalse(guard.evaluate(demo.snapshot(Gear.REVERSE, -1.0, 72, false), time).canControl)
    }
    @Test fun writeReadConfirmationAndCapabilityBoundsAreSeparate() {
        val manager = VehicleManager(demo, { parked() }, { time })
        val outcomes = mutableListOf<CommandOutcome>()
        val req = ControlRequest(Control.FAN_SPEED, "DRIVER", 3.0)
        manager.control(req, true, outcomes::add)
        assertEquals(CommandStatus.ACCEPTED, outcomes.last().status)
        assertFalse(outcomes.any { it.status == CommandStatus.CONFIRMED })
        manager.verify(req, outcomes::add)
        assertEquals(CommandStatus.CONFIRMED, outcomes.last().status)
        manager.control(req.copy(value = 8.0), true, outcomes::add)
        assertEquals(CommandStatus.BLOCKED, outcomes.last().status)
        manager.control(req, false, outcomes::add)
        assertEquals(CommandStatus.BLOCKED, outcomes.last().status)
    }
    @Test fun lateCapabilityCallbackRechecksSafety() {
        var state = parked()
        var pending: ((Result<Capability>) -> Unit)? = null
        var writes = 0
        val port = object : VehiclePort {
            override val source = SourceMode.DEMO
            override fun capability(control: Control, callback: (Result<Capability>) -> Unit) { pending = callback }
            override fun write(request: ControlRequest, callback: (Result<Unit>) -> Unit) { writes++; callback(Result.success(Unit)) }
            override fun read(request: ControlRequest, callback: (Result<Double>) -> Unit) { callback(Result.success(3.0)) }
        }
        val results = mutableListOf<CommandOutcome>()
        VehicleManager(port, { state }, { time }).control(ControlRequest(Control.FAN_SPEED, "DRIVER", 3.0), true, results::add)
        state = demo.snapshot(Gear.DRIVE, 0.0, 72, true)
        pending!!(Result.success(Capability(Access.READ_WRITE, mapOf("DRIVER" to Bounds(0.0, 7.0)), time)))
        assertEquals(0, writes)
        assertEquals(CommandStatus.BLOCKED, results.last().status)
    }
    @Test fun readonlyAbsentAreaAndBadBoundsCannotWrite() {
        assertFalse(Capability(Access.READ, mapOf("DRIVER" to Bounds(0.0, 7.0)), time).permits("DRIVER", 3.0))
        assertFalse(Capability(Access.WRITE, emptyMap(), time).permits("DRIVER", 3.0))
        assertFalse(Bounds(7.0, 0.0).contains(3.0))
        assertFalse(Bounds(0.0, 7.0).contains(Double.NaN))
    }
    @Test fun routeAndAlertContractsRejectInvalidValues() {
        val c = Coordinate(37.5, 127.0)
        assertEquals(2, RouteRequest(c, listOf(c, c)).waypoints.size)
        assertFailsWith<IllegalArgumentException> { RouteRequest(c, listOf(c, c, c)) }
        assertFailsWith<IllegalArgumentException> { Coordinate(Double.NaN, 127.0) }
        assertFailsWith<IllegalArgumentException> { Coordinate(127.0, 37.5) }
        assertFailsWith<IllegalArgumentException> { PickupAlert("shop", c, 0.0, setOf(Transition.ENTER)) }
        assertFailsWith<IllegalArgumentException> { PickupAlert("shop", c, 100.0, emptySet()) }
    }
    @Test fun locationFailureNeverBecomesDemoSuccess() {
        var called = false
        val port = object : LocationPort {
            override val source = SourceMode.LIVE
            override fun isSupported() = true
            override fun addAlert(alert: PickupAlert, callback: (Result<Unit>) -> Unit) { called = true; callback(Result.failure(SecurityException())) }
            override fun removeAlert(id: String, callback: (Result<Unit>) -> Unit) { callback(Result.success(Unit)) }
        }
        val manager = LocationAlertManager(port) { SourceMode.LIVE }
        val alert = PickupAlert("shop", Coordinate(37.5, 127.0), 150.0, setOf(Transition.ENTER))
        manager.register(alert, false) { assertTrue(it.isFailure) }
        assertFalse(called)
        manager.register(alert, true) { assertTrue(it.isFailure) }
        assertTrue(called)
    }
    @Test fun voiceCooldownPreventsRepeatedAlerts() {
        var calls = 0
        val port = object : VoicePort {
            override val source = SourceMode.DEMO
            override fun speakOnDevice(text: String, callback: (Result<Unit>) -> Unit) { calls++; callback(Result.success(Unit)) }
        }
        val manager = VoiceCoachManager(port, { SourceMode.DEMO }, { time })
        manager.alert("배터리를 확인하세요") { assertTrue(it.isSuccess) }
        manager.alert("배터리를 확인하세요") { assertTrue(it.isFailure) }
        time += 30_000
        manager.alert("배터리를 확인하세요") { assertTrue(it.isSuccess) }
        assertEquals(2, calls)
    }
}
