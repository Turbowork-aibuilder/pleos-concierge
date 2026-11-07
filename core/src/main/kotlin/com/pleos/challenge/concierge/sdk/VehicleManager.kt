package com.pleos.challenge.concierge.sdk

import com.pleos.challenge.concierge.core.*

enum class Access { NONE, READ, WRITE, READ_WRITE }
enum class Control { FAN_SPEED, CABIN_TEMPERATURE, SEAT_HEAT, SEAT_VENTILATION, STEERING_HEAT, WINDOW_POSITION }
data class Bounds(val min: Double, val max: Double) {
    fun contains(value: Double) = min.isFinite() && max.isFinite() && min <= max && value.isFinite() && value in min..max
}
data class Capability(val access: Access, val areas: Map<String, Bounds>, val observedAtMs: Long) {
    fun permits(area: String, value: Double): Boolean =
        access in setOf(Access.WRITE, Access.READ_WRITE) && areas[area]?.contains(value) == true
}
data class ControlRequest(val control: Control, val area: String, val value: Double)
enum class CommandStatus { BLOCKED, PENDING, ACCEPTED, CONFIRMED, FAILED }
data class CommandOutcome(val status: CommandStatus, val detail: String)

/** Native SDK binding must map SDK callback completion to ACCEPTED, never CONFIRMED. */
interface VehiclePort {
    val source: SourceMode
    fun capability(control: Control, callback: (Result<Capability>) -> Unit)
    fun write(request: ControlRequest, callback: (Result<Unit>) -> Unit)
    fun read(request: ControlRequest, callback: (Result<Double>) -> Unit)
}

class VehicleManager(
    private val port: VehiclePort,
    private val current: () -> VehicleSnapshot,
    private val now: () -> Long,
    private val guard: SafetyGuard = SafetyGuard(),
) {
    fun control(request: ControlRequest, userRequested: Boolean, callback: (CommandOutcome) -> Unit) {
        fun blocked(reason: String) = callback(CommandOutcome(CommandStatus.BLOCKED, reason))
        fun safe(): Boolean = current().source == port.source && guard.evaluate(current(), now()).canControl
        if (!userRequested) return blocked("명시적 버튼 요청 필요")
        if (!request.value.isFinite() || request.area.isBlank()) return blocked("잘못된 입력")
        if (!safe()) return blocked("안전 조건 미충족 또는 데이터 출처 불일치")
        callback(CommandOutcome(CommandStatus.PENDING, "지원 범위 확인 중"))
        try {
            port.capability(request.control) { result ->
                val cap = result.getOrElse {
                    callback(CommandOutcome(CommandStatus.FAILED, "Capability 확인 실패")); return@capability
                }
                if (now() < cap.observedAtMs || now() - cap.observedAtMs >= 1_000 || !cap.permits(request.area, request.value)) {
                    blocked("지원 영역·범위·쓰기 권한 또는 최신성 불충족"); return@capability
                }
                // State may have changed while awaiting the IPC capability callback.
                if (!safe()) { blocked("차량 상태 변경으로 요청 취소"); return@capability }
                try {
                    port.write(request) { writeResult ->
                        if (writeResult.isFailure) callback(CommandOutcome(CommandStatus.FAILED, "제어 요청 실패"))
                        else callback(CommandOutcome(CommandStatus.ACCEPTED, "요청 접수됨 · 실제 상태 확인 필요"))
                    }
                } catch (_: Exception) { callback(CommandOutcome(CommandStatus.FAILED, "제어 연결 오류")) }
            }
        } catch (_: Exception) { callback(CommandOutcome(CommandStatus.FAILED, "Capability 연결 오류")) }
    }

    fun verify(request: ControlRequest, callback: (CommandOutcome) -> Unit) {
        if (current().source != port.source) {
            callback(CommandOutcome(CommandStatus.BLOCKED, "데이터 출처 불일치")); return
        }
        try {
            port.read(request) { result ->
                val actual = result.getOrNull()
                val confirmed = actual != null && actual.isFinite() && kotlin.math.abs(actual - request.value) < 0.001
                callback(CommandOutcome(if (confirmed) CommandStatus.CONFIRMED else CommandStatus.FAILED,
                    if (confirmed) "읽기 응답으로 상태 확인" else "상태 미확인 또는 값 불일치"))
            }
        } catch (_: Exception) { callback(CommandOutcome(CommandStatus.FAILED, "상태 조회 실패")) }
    }
}
