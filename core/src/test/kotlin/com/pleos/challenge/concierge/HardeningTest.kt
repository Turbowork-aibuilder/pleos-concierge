package com.pleos.challenge.concierge

import com.pleos.challenge.concierge.core.*
import kotlin.test.*

class HardeningTest {
    @Test fun pendingRegistrationClosesAfterRemoval() {
        val calls = mutableListOf<String>()
        val life = AlertLifetime({ calls.add("remove:$it") }, { calls.add("release") }, { calls.add(it) })
        val token = life.reserve("pickup")!!
        life.close()
        assertEquals(emptyList(), calls)
        assertFalse(life.registered(token, "remote"))
        assertEquals(listOf("remove:remote", "release"), calls)
        life.close()
        assertEquals(2, calls.size)
    }
    @Test fun timeoutAndRetryKeepTokensIndependent() {
        val calls = mutableListOf<String>()
        val life = AlertLifetime({ calls.add(it) }, {}, {})
        val old = life.reserve("a")!!
        life.cancel("a")
        val fresh = life.reserve("a")!!
        assertFalse(life.registered(old, "old"))
        assertTrue(life.registered(fresh, "new"))
        assertEquals(listOf("old"), calls)
        assertTrue(life.matches(fresh, "new"))
    }
    @Test fun errorSettlesPendingClose() {
        var released = 0
        val life = AlertLifetime({}, { released++ }, {})
        val token = life.reserve("a")!!
        life.close(); life.failed(token)
        assertEquals(1, released)
    }
    @Test fun deadlineIsReportedAndLateCallbackDoesNotUseReleasedSdk() {
        val errors = mutableListOf<String>()
        var removes = 0
        val life = AlertLifetime({ removes++ }, {}, { errors.add(it) })
        val token = life.reserve("a")!!
        life.close(); life.expire(); life.registered(token, "late")
        assertEquals(0, removes)
        assertEquals(2, errors.size)
    }
    @Test fun failedCleanupIsVisible() {
        val errors = mutableListOf<String>()
        val life = AlertLifetime({ error("offline") }, {}, { errors.add(it) })
        life.registered(life.reserve("a")!!, "remote")
        assertTrue(life.cancel("a").isFailure)
        assertEquals(1, errors.size)
    }
    @Test fun pendingQueueIsBounded() {
        val life = AlertLifetime({}, {}, {})
        repeat(8) { assertNotNull(life.reserve("$it")); life.cancel("$it") }
        assertNull(life.reserve("overflow"))
        life.close(); assertNull(life.reserve("closed"))
    }
    @Test fun speedConversionCannotEmitInfinity() {
        assertNull(speedKmH(Double.MAX_VALUE))
        assertEquals(36.0, speedKmH(10.0))
    }
    @Test fun timingBlocksBurstAndPollForOneSecond() {
        val first = inspectSignal(SignalTiming(), 1000, 1000)
        val burst = inspectSignal(first, 1001, 1001)
        assertFalse(burst.permits(1002))
        assertFalse(burst.permits(2000))
        assertTrue(burst.permits(2001))
        assertEquals(1000.0, burst.hz)
    }
    @Test fun timingRejectsFutureOldAndNegativeTime() {
        for (time in listOf(-1L, 0L, 1001L)) {
            assertFalse(inspectSignal(SignalTiming(), time, 1000).permits(1000))
        }
    }
    @Test fun timingReportsHzAndJitterWithoutSmoothing() {
        val first = inspectSignal(SignalTiming(), 1000, 1000)
        val next = inspectSignal(first, 1200, 1200)
        assertEquals(5.0, next.hz)
        assertEquals(0L, next.jitterMs)
        assertTrue(next.permits(1200))
    }
}
