package com.pleos.challenge.concierge

/** Scripted DEMO conversation (presenter scenario). No speech recognition, AI model or real order is involved. */
enum class Speaker { DRIVER, ASSISTANT }

enum class AssistantPhase(val label: String) { LISTENING("듣는 중…"), ANSWERING("답변 중"), DONE("요청 완료") }

data class AssistantLine(val speaker: Speaker, val text: String, val startMs: Long, val durationMs: Long) {
    val endMs: Long get() = startMs + durationMs
}

/** One line as it is shown: driver lines are revealed like live dictation, assistant lines appear whole. */
data class ShownLine(val line: AssistantLine, val visibleChars: Int) {
    val text: String get() = line.text.take(visibleChars)
}

data class AssistantFrame(val lines: List<ShownLine>, val phase: AssistantPhase)

object DemoAssistant {
    /** Start times follow the Typecast takes in media/assistant/v31/timing.json (2.2/4.25/1.65/4.7 s, 0.45 s gaps). */
    val script = listOf(
        AssistantLine(Speaker.DRIVER, "출근길에 커피 한 잔 미리 주문해 줘.", 600, 2200),
        AssistantLine(Speaker.ASSISTANT, "경로에 있는 픽업 카페에서 아이스 아메리카노 한 잔, 주문할까요?", 3250, 4250),
        AssistantLine(Speaker.DRIVER, "응, 도착할 때 맞춰서.", 7950, 1650),
        AssistantLine(Speaker.ASSISTANT, "좋아요. 도착에 맞춰 준비되도록 요청했어요. 운전에만 집중하세요.", 10050, 4700),
    )
    val totalMs: Long get() = script.last().endMs

    fun frameAt(elapsedMs: Long): AssistantFrame {
        val shown = script.filter { elapsedMs >= it.startMs }.map { ShownLine(it, visibleChars(it, elapsedMs)) }
        return AssistantFrame(shown, phaseAt(elapsedMs))
    }

    private fun visibleChars(line: AssistantLine, elapsedMs: Long): Int {
        if (line.speaker == Speaker.ASSISTANT) return line.text.length
        val progress = (elapsedMs - line.startMs).toDouble() / line.durationMs
        return (progress.coerceIn(0.0, 1.0) * line.text.length).toInt().coerceAtLeast(1)
    }

    private fun phaseAt(elapsedMs: Long): AssistantPhase {
        if (elapsedMs >= totalMs) return AssistantPhase.DONE
        val current = script.lastOrNull { elapsedMs >= it.startMs } ?: return AssistantPhase.LISTENING
        return if (current.speaker == Speaker.ASSISTANT) AssistantPhase.ANSWERING else AssistantPhase.LISTENING
    }
}
