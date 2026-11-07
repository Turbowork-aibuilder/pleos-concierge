package com.pleos.challenge.concierge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.demo.DemoDataProvider
import com.pleos.challenge.concierge.platform.*
import com.pleos.challenge.concierge.sdk.*

/** One foreground session owns every SDK instance. DEMO flavor cannot construct LIVE adapters. */
class AppController(private val context: Context, val demoOnly: Boolean,
                    private val changed: () -> Unit,
                    private val servicesFactory: (Context, () -> Boolean, (Boolean) -> Unit) -> LiveServices,
                    private val signalsFactory: (Context, (VehicleSnapshot) -> Unit) -> SignalSource) {
    constructor(context: Context, demoOnly: Boolean, changed: () -> Unit) :
        this(context, demoOnly, changed, ::PleosLiveServices, ::CarSignals)
    val guard = SafetyGuard()
    var snapshot = VehicleSnapshot(if (demoOnly) SourceMode.DEMO else SourceMode.LIVE)
        private set
    var message = "차량 정보를 확인하고 있어요."
        private set
    var brake: Boolean? = null
        private set
    var closed = false
        private set
    private val main = Handler(Looper.getMainLooper())
    private var epoch = 0
    private var scenario = 0
    private val demoNavigation: DemoNavigation = DemoNavigation(::now)
    fun navigationState(): DemoRoute? = if (demoOnly && !closed) demoNavigation.state(decision().mode) else null
    private val simulation = if (demoOnly) DemoDataProvider(::now) else null
    private var services: LiveServices? = null
    private var signals: SignalSource? = null
    private var started = false
    private var carConnected = false
    private var coach: VoiceCoachManager? = null
    private var port: VehiclePort? = null
    private var telemetryPending = false
    private var pollToken = 0L
    private var lastSpeed: Sample<Double>? = null
    private val busy = mutableSetOf<Control>()
    fun now(): Long = SystemClock.elapsedRealtime()
    fun decision(): SafetyDecision = guard.evaluate(snapshot, now())
    fun canShowLocalPickup(): Boolean {
        val current = decision()
        return demoOnly && !closed && current.mode == AppMode.SMART_PICKUP && current.canControl
    }
    fun start(): Unit {
        if (started || closed) return
        started = true
        if (demoOnly) { port = simulation; message = "DEMO · 차량과 연결되지 않는 가상 체험" }
        else signals = signalsFactory(context, ::onVehicleState).also { it.start() }
        main.post(tick)
    }
    private fun active(session: Int): Boolean = !closed && session == epoch
    private fun polling(session: Int, token: Long): Boolean = active(session) && pollToken == token
    private fun resetConnection(): Unit {
        epoch++; pollToken++; telemetryPending = false; lastSpeed = null; brake = null; busy.clear()
        val previous = services
        services = null; port = null; coach = null
        snapshot = VehicleSnapshot(snapshot.source)
        previous?.close()
    }
    private fun connectServices(): Unit {
        val session = epoch
        val safe = { active(session) && decision().canControl }
        try {
            services = servicesFactory(context, safe) { near ->
                if (active(session) && snapshot.connected) { snapshot = snapshot.copy(nearPickup = near); changed() }
            }
            services!!.start(); port = services!!.vehicle
            coach = VoiceCoachManager(services!!.voice, { snapshot.source }, ::now)
            message = AppCopy.CONNECTED
        } catch (_: Exception) { resetConnection(); message = "차량 SDK 연결 실패 · 제어 불가" }
    }
    private fun onVehicleState(state: VehicleSnapshot): Unit {
        if (closed) return
        val transition = state.connected != carConnected
        carConnected = state.connected
        if (transition) {
            resetConnection(); snapshot = state
            if (state.connected) connectServices()
        } else snapshot = mergeSnapshot(state)
        if (!state.connected) message = "차량 연결 해제 · 이전 요청 및 위치 알림 종료"
        eco(snapshot.speed); changed()
    }
    private fun mergeSnapshot(state: VehicleSnapshot): VehicleSnapshot =
        if (services == null) VehicleSnapshot(state.source)
        else state.copy(soc = snapshot.soc, nearPickup = snapshot.nearPickup)
    private fun snapshotFor(session: Int): VehicleSnapshot =
        if (active(session)) snapshot else VehicleSnapshot(snapshot.source)
    private fun eco(speed: Sample<Double>?) {
        val previous = lastSpeed
        if (speed != null && previous != null && speed.observedAtMs > previous.observedAtMs && now() - speed.observedAtMs in 0..999) {
            val seconds = (speed.observedAtMs - previous.observedAtMs) / 1000.0
            if (seconds in 0.1..2.0 && (kotlin.math.abs(speed.value) - kotlin.math.abs(previous.value)) / seconds > 3.0)
                coach?.alert("부드럽게 가속해 주세요.") { }
        }
        lastSpeed = speed
    }
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            if (demoOnly) snapshot = when (scenario) {
                1 -> simulation!!.snapshot(Gear.DRIVE, 16.67, 72, true)
                2 -> simulation!!.snapshot(Gear.PARK, 0.0, 72, false, true)
                3 -> VehicleSnapshot(SourceMode.DEMO)
                else -> simulation!!.snapshot(Gear.PARK, 0.0, 72, false)
            }
            else if (!telemetryPending && snapshot.connected) {
                telemetryPending = true
                val session = epoch
                val token = ++pollToken
                val watchdog = Runnable {
                    if (polling(session, token)) {
                        telemetryPending = false; pollToken++
                        snapshot = snapshot.copy(soc = null); brake = null; changed()
                    }
                }
                main.postDelayed(watchdog,6_000)
                runCatching {
                    val service = checkNotNull(services)
                    service.readSoc { result ->
                        if (polling(session, token)) {
                            main.removeCallbacks(watchdog)
                            telemetryPending = false
                            snapshot = snapshot.copy(soc = result.getOrNull()?.let { Sample(it, now(), SourceMode.LIVE) })
                            if ((result.getOrNull() ?: 100) < 20) coach?.alert(AppCopy.LOW_BATTERY) { }
                            changed()
                        }
                    }
                    service.readBrake { result -> if (polling(session, token)) { brake = result.getOrNull(); changed() } }
                }.onFailure {
                    main.removeCallbacks(watchdog); telemetryPending = false; pollToken++
                    snapshot = snapshot.copy(soc = null); brake = null
                }
            }
            if (snapshot.soc?.let { now() - it.observedAtMs > 5_000 } == true) snapshot = snapshot.copy(soc = null)
            changed(); main.postDelayed(this, 500)
        }
    }
    fun selectScenario(index: Int) {
        if (!demoOnly || closed) return
        if (index == 1) demoNavigation.start()
        scenario = index; message = "DEMO · 가상 시나리오 변경"; main.removeCallbacks(tick); main.post(tick)
    }
    fun control(control: Control, value: Double) {
        if (closed || control in busy) return
        val service = port ?: return
        val session = epoch
        val request = ControlRequest(control, "DRIVER", value)
        busy.add(control)
        val manager = VehicleManager(service, { snapshotFor(session) }, ::now)
        manager.control(request, true) { outcome ->
            if (active(session)) applyOutcome(manager, request, outcome, session)
        }
    }
    private fun showOutcome(control: Control, outcome: CommandOutcome): Unit {
        message = "${control.name} · ${outcome.status} · ${outcome.detail}"
        changed()
    }
    private fun applyOutcome(manager: VehicleManager, request: ControlRequest, outcome: CommandOutcome, session: Int): Unit {
        showOutcome(request.control, outcome)
        if (outcome.status == CommandStatus.ACCEPTED) manager.verify(request) { verified ->
            if (active(session)) { busy.remove(request.control); showOutcome(request.control, verified) }
        }
        else if (outcome.status != CommandStatus.PENDING) busy.remove(request.control)
    }
    fun route(destination: Coordinate, waypoints: List<Coordinate>) {
        if (!decision().canControl || closed) return
        if (demoOnly) { RouteRequest(destination, waypoints); demoNavigation.setDestination(); message = "DEMO · 경로 요청 시연 (내비게이션 전송 없음)"; changed(); return }
        val session = epoch
        message = "경로 계산 중 · 완료 응답 대기"
        NavigationManager(services?.navigation ?: return, { snapshot.source }).route(RouteRequest(destination, waypoints)) {
            if (active(session)) { message = if (it.isSuccess) "경로 계산 완료" else "경로 미확인 · 다시 연결해 주세요"; changed() }
        }
        val alerts = LocationAlertManager(services?.location ?: return, { snapshot.source })
        alerts.remove("destination") {
            if (active(session)) alerts.register(
                PickupAlert("destination", destination, 100.0, setOf(Transition.ENTER, Transition.EXIT)),
                context.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (active(session) && it.isFailure) { message = "접근 알림 등록 실패 · 내비 경로 결과는 별도 확인"; changed() }
            }
        }
        changed()
    }
    fun close(): Unit {
        if (closed) return
        closed = true; main.removeCallbacksAndMessages(null)
        resetConnection(); signals?.close(); signals = null
    }
}
