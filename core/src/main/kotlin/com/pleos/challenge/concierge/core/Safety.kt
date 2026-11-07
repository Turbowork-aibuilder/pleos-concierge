package com.pleos.challenge.concierge.core

import kotlin.math.abs

enum class SourceMode { LIVE, DEMO }
enum class Gear { PARK, DRIVE, REVERSE, NEUTRAL, UNKNOWN }
enum class AppMode { CABIN_CARE, ECO_COACHING, SMART_PICKUP, UNAVAILABLE }

/** All timestamps use one monotonic clock, never wall-clock time. */
data class Sample<T>(val value: T, val observedAtMs: Long, val source: SourceMode)
data class UxState(val requiresOptimization: Boolean, val activeRestrictions: Int)
data class VehicleSnapshot(
    val source: SourceMode,
    val connected: Boolean = false,
    val speedPermission: Boolean = false,
    val speed: Sample<Double>? = null,
    val gear: Sample<Gear>? = null,
    val ux: Sample<UxState>? = null,
    val soc: Sample<Int>? = null,
    val nearPickup: Boolean = false,
)

data class SafetyDecision(val canControl: Boolean, val reason: String, val mode: AppMode)

class SafetyGuard(private val freshnessMs: Long = 1_000L) {
    init { require(freshnessMs > 0) }

    private fun <T> fresh(sample: Sample<T>?, state: VehicleSnapshot, nowMs: Long): Boolean =
        sample != null && sample.source == state.source && sample.observedAtMs >= 0 &&
            nowMs >= sample.observedAtMs && nowMs - sample.observedAtMs < freshnessMs

    fun evaluate(state: VehicleSnapshot, nowMs: Long): SafetyDecision {
        return connection(state) ?: ux(state, nowMs) ?: speed(state, nowMs) ?: gear(state, nowMs)
            ?: ready(state)
    }
    private fun ready(state: VehicleSnapshot): SafetyDecision =
        SafetyDecision(true, "P단 · 정차 · 제한 해제 확인", if (state.nearPickup) AppMode.SMART_PICKUP else AppMode.CABIN_CARE)
    private fun deny(reason: String, mode: AppMode = AppMode.UNAVAILABLE): SafetyDecision = SafetyDecision(false, reason, mode)
    private fun connection(state: VehicleSnapshot): SafetyDecision? {
        if (!state.connected) return deny("차량 서비스 연결 없음")
        if (!state.speedPermission) return deny("차량 속도 권한 없음")
        return null
    }
    private fun ux(state: VehicleSnapshot, nowMs: Long): SafetyDecision? {
        if (!fresh(state.ux, state, nowMs)) return deny("주행 제한 상태 확인 필요")
        val ux = state.ux!!.value
        if (ux.requiresOptimization || ux.activeRestrictions != 0) return deny("주행 제한 적용", AppMode.ECO_COACHING)
        return null
    }
    private fun speed(state: VehicleSnapshot, nowMs: Long): SafetyDecision? {
        if (!fresh(state.speed, state, nowMs)) return deny("차량 속도 신호 만료")
        val speed = state.speed!!.value
        if (!speed.isFinite()) return deny("유효하지 않은 속도")
        if (speed != 0.0) return deny("차량 이동 중", AppMode.ECO_COACHING)
        return null
    }
    private fun gear(state: VehicleSnapshot, nowMs: Long): SafetyDecision? {
        if (!fresh(state.gear, state, nowMs)) return deny("기어 상태 확인 필요")
        if (state.gear!!.value != Gear.PARK) return deny("P단에서만 조작 가능", AppMode.ECO_COACHING)
        return null
    }
}

fun speedKmH(metersPerSecond: Double): Double? =
    (abs(metersPerSecond) * 3.6).takeIf { it.isFinite() }

/** Source switches discard the old vehicle state; they never carry demo readings into LIVE. */
class SessionState(initial: SourceMode = SourceMode.LIVE) {
    var snapshot = VehicleSnapshot(initial)
        private set

    fun switchMode(mode: SourceMode) { snapshot = VehicleSnapshot(mode) }
    fun update(next: VehicleSnapshot) {
        require(next.source == snapshot.source) { "Source changed without explicit mode switch" }
        snapshot = next
    }
    fun disconnect() { snapshot = VehicleSnapshot(snapshot.source) }
}
