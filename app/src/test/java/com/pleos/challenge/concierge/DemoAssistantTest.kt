package com.pleos.challenge.concierge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAssistantTest {
    @Test fun nothingShownBeforeTheFirstLine() {
        val frame = DemoAssistant.frameAt(0)
        assertTrue(frame.lines.isEmpty())
        assertEquals(AssistantPhase.LISTENING, frame.phase)
    }

    @Test fun driverLineIsRevealedLikeDictation() {
        val first = DemoAssistant.script[0]
        val half = DemoAssistant.frameAt(first.startMs + first.durationMs / 2).lines.single()
        assertEquals(first.text.length / 2, half.visibleChars)
        assertEquals(first.text, DemoAssistant.frameAt(first.endMs).lines.single().text)
    }

    @Test fun assistantLineAppearsWholeAndSwitchesPhase() {
        val answer = DemoAssistant.script[1]
        val frame = DemoAssistant.frameAt(answer.startMs)
        assertEquals(2, frame.lines.size)
        assertEquals(answer.text, frame.lines[1].text)
        assertEquals(AssistantPhase.ANSWERING, frame.phase)
    }

    @Test fun linesAreOrderedWithoutOverlap() {
        DemoAssistant.script.zipWithNext().forEach { (a, b) -> assertTrue(b.startMs >= a.endMs) }
        assertEquals(listOf(Speaker.DRIVER, Speaker.ASSISTANT, Speaker.DRIVER, Speaker.ASSISTANT), DemoAssistant.script.map { it.speaker })
    }

    @Test fun finalFrameKeepsAllLinesAndIsDone() {
        val frame = DemoAssistant.frameAt(DemoAssistant.totalMs + 60_000)
        assertEquals(4, frame.lines.size)
        assertTrue(frame.lines.all { it.text == it.line.text })
        assertEquals(AssistantPhase.DONE, frame.phase)
    }
}
