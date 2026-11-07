package com.pleos.challenge.concierge

import com.pleos.challenge.concierge.core.*
import com.pleos.challenge.concierge.demo.DemoDataProvider
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class GoldenCaptureTest {
    private val demo = DemoDataProvider { 10000L }
    private fun cases(): List<VehicleSnapshot> {
        val p = demo.snapshot(Gear.PARK, 0.0, 72, false)
        return listOf(p, p.copy(nearPickup = true),
            demo.snapshot(Gear.DRIVE, 5.0, 72, false),
            demo.snapshot(Gear.REVERSE, -1.0, 72, false),
            demo.snapshot(Gear.PARK, 0.0, 72, true),
            p.copy(speed = Sample(Double.NaN, 10000, SourceMode.DEMO)),
            p.copy(speed = Sample(Double.POSITIVE_INFINITY, 10000, SourceMode.DEMO)),
            p.copy(speed = Sample(0.0, 9000, SourceMode.DEMO)))
    }
    @Test fun captureLegacyOutputsAndCompare() {
        val lines = cases().mapIndexed { index, state ->
            val d = SafetyGuard().evaluate(state, 10000L)
            "{\"id\":$index,\"can_control\":${d.canControl},\"mode\":\"${d.mode}\"}"
        }
        val result = "[" + lines.joinToString(",") + "]"
        val file = File("../hardening/v6_2/evidence/kotlin_golden.json")
        if (file.exists()) assertEquals(file.readText(), result)
        else file.writeText(result)
    }
}
