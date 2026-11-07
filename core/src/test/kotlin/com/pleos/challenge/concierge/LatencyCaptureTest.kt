package com.pleos.challenge.concierge

import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.demo.DemoDataProvider
import java.io.File
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertFalse

class LatencyCaptureTest {
    @Test fun measurePureDecisionOnly() {
        val guard = SafetyGuard()
        val state = DemoDataProvider { 10000L }.snapshot(Gear.PARK, 0.0, 72, false)
            .copy(speed = Sample(Double.NaN, 10000L, SourceMode.DEMO))
        repeat(2000) { guard.evaluate(state, 10000) }
        val times = DoubleArray(20000) {
            val start = System.nanoTime()
            val result = guard.evaluate(state, 10000)
            val ms = (System.nanoTime() - start) / 1_000_000.0
            assertFalse(result.canControl)
            ms
        }
        val mean = times.average()
        val sd = sqrt(times.map { (it - mean) * (it - mean) }.average())
        val pass = mean < 50 && sd <= 5 && times.max() < 10
        File("../hardening/v6_2/evidence/kotlin_latency.json").writeText(
            "{\"scope\":\"JVM pure decision, no SDK IPC or UI\",\"n\":20000,\"mean_ms\":$mean,\"std_ms\":$sd,\"max_ms\":${times.max()},\"target_met\":$pass}")
    }
}
