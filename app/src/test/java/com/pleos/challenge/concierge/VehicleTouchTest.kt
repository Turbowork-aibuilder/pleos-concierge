package com.pleos.challenge.concierge

import android.view.MotionEvent
import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.MessageDigest

/** v12 restores the approved still; actual rotation remains unimplemented. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleTouchTest {
    @Test fun approvedArtworkHasExactOriginalBytes() {
        val bytes = RuntimeEnvironment.getApplication().assets
            .open("artwork/titanium-ev.webp").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals("c168b6a36f6e6e5668341c870d649d847bcbcb215b12d2a822124ee5ab9c2e1a", hash)
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1536, image.width)
        assertEquals(1024, image.height)
    }
    @Test fun imageFitsWithoutStretchingAndDoesNotAdvertiseRotation() {
        val car = TelemetryVehicleView(RuntimeEnvironment.getApplication())
        listOf(400 to 300, 280 to 132, 500 to 230).forEach { (w, h) ->
            car.layout(0, 0, w, h)
            val box = car.artworkBounds()
            assertEquals(1.5f, box.width() / box.height(), .0001f)
            assertTrue(box.left >= 0 && box.top >= 0 && box.right <= w && box.bottom <= h)
        }
        assertEquals("차량 외관", car.contentDescription)
        assertFalse(car.isClickable)
        val event = MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        assertFalse(car.dispatchTouchEvent(event))
        event.recycle()
    }
}
