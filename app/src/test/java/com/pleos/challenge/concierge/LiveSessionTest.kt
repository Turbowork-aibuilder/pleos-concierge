package com.pleos.challenge.concierge

import android.os.Looper
import com.pleos.challenge.concierge.sdk.Control
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveSessionTest {
    @Test fun startupFailureClosesPartialSessionWithoutRepeatedAllocation() = LiveSessionFixture(true).use { f ->
        repeat(4) { f.connect() }
        assertEquals(1, f.services.size); assertEquals(1, f.services.single().closeCount)
        assertFalse(f.app.decision().canControl); assertFalse(f.app.snapshot.connected)
        f.disconnect(); f.connect(); assertEquals(2, f.services.size)
    }
    @Test fun noLiveSdkBeforeCarConnection() = LiveSessionFixture().use { f ->
        assertTrue(f.services.isEmpty()); assertFalse(f.app.decision().canControl)
    }
    @Test fun disconnectClosesSdkAndClearsProximity() = LiveSessionFixture().use { f ->
        f.connect(); val old = f.services.single(); old.near(true)
        assertTrue(f.app.snapshot.nearPickup)
        f.disconnect(); assertEquals(1, old.closeCount)
        assertFalse(f.app.snapshot.nearPickup); assertFalse(f.app.decision().canControl)
    }
    @Test fun lateLocationCannotReviveDisconnectedSession() = LiveSessionFixture().use { f ->
        f.connect(); val old = f.services.single(); f.disconnect(); old.near(true)
        assertFalse(f.app.snapshot.nearPickup)
    }
    @Test fun reconnectCreatesFreshSdkAndIgnoresOldLocation() = LiveSessionFixture().use { f ->
        f.connect(); val old = f.services.single(); f.disconnect(); f.connect()
        assertEquals(2, f.services.size); old.near(true); assertFalse(f.app.snapshot.nearPickup)
        f.services.last().near(true); assertTrue(f.app.snapshot.nearPickup)
    }
    @Test fun repeatedConnectionEventsDoNotRecreateSdk() = LiveSessionFixture().use { f ->
        repeat(4) { f.connect() }; assertEquals(1, f.services.size)
        repeat(4) { f.disconnect() }; assertEquals(1, f.services.single().closeCount)
    }
    @Test fun oldTelemetryCannotContaminateReconnect() = LiveSessionFixture().use { f ->
        f.connect(); shadowOf(Looper.getMainLooper()).idle(); val old = f.services.single()
        assertEquals(1, old.soc.size); f.disconnect(); f.connect()
        old.soc.single()(Result.success(5)); old.brakes.single()(Result.success(true))
        assertNull(f.app.snapshot.soc); assertNull(f.app.brake)
    }
    @Test fun oldCapabilityCannotWriteAfterReconnect() = LiveSessionFixture().use { f ->
        f.connect(); val old = f.services.single(); old.holdCapability = true
        f.app.control(Control.CABIN_TEMPERATURE, 23.0); f.disconnect(); f.connect()
        old.capabilities.single()(Result.success(old.capabilityValue()))
        assertEquals(0, old.writes)
        f.app.control(Control.CABIN_TEMPERATURE, 23.0)
        assertEquals(1, f.services.last().writes); assertTrue(f.app.message.contains("CONFIRMED"))
    }
    @Test fun staleReadCannotReportConfirmedAfterDisconnect() = LiveSessionFixture().use { f ->
        f.connect(); val old = f.services.single(); old.holdRead = true
        f.app.control(Control.CABIN_TEMPERATURE, 23.0); assertTrue(f.app.message.contains("ACCEPTED"))
        f.disconnect(); val disconnected = f.app.message
        old.reads.single()(Result.success(23.0)); assertEquals(disconnected, f.app.message)
    }
    @Test fun mismatchReportsFailedNotConfirmed() = LiveSessionFixture().use { f ->
        f.connect(); f.services.single().actual = 19.0
        f.app.control(Control.CABIN_TEMPERATURE, 23.0)
        assertTrue(f.app.message.contains("FAILED")); assertFalse(f.app.message.contains("CONFIRMED"))
    }
    @Test fun writeFailureReportsFailed() = LiveSessionFixture().use { f ->
        f.connect(); f.services.single().writeFailure = true
        f.app.control(Control.CABIN_TEMPERATURE, 23.0); assertTrue(f.app.message.contains("FAILED"))
    }
    @Test fun acceptedDoesNotMeanReadConfirmed() = LiveSessionFixture().use { f ->
        f.connect(); f.services.single().holdRead = true
        f.app.control(Control.CABIN_TEMPERATURE, 23.0)
        assertTrue(f.app.message.contains("ACCEPTED")); assertFalse(f.app.message.contains("CONFIRMED"))
    }
    @Test fun closeIsIdempotentAndLateSignalsStayClosed() = LiveSessionFixture().use { f ->
        f.connect(); f.app.close(); f.app.close(); f.connect(); f.services.single().near(true)
        assertEquals(1, f.signalsClosed); assertEquals(1, f.services.single().closeCount)
        assertFalse(f.app.snapshot.connected); assertFalse(f.app.snapshot.nearPickup)
    }
}
