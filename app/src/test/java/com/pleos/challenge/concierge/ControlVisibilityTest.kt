package com.pleos.challenge.concierge

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.pleos.challenge.concierge.sdk.Control
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class ControlVisibilityTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    @Test fun requestedValueAndVerifiedResultAreVisibleSeparately() {
        assumeTrue(BuildConfig.DEMO_ONLY)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val views = all(activity.get().window.decorView)
            views.single { it.contentDescription == "실내 온도 높이기" }.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val texts = views.filterIsInstance<TextView>().filter { it.isShown }.map { it.text.toString() }
            assertTrue("Requested numeric value must be identified", texts.any { it.contains("요청값") })
            assertTrue("Read confirmation must be visible", texts.any { it.contains("CONFIRMED") })
        } finally { activity.pause().stop().destroy() }
    }
    @Test fun blockedRequestRemainsVisibleAsBlocked() {
        assumeTrue(BuildConfig.DEMO_ONLY)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            shadowOf(Looper.getMainLooper()).idle()
            val field = MainActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
            val app = field.get(activity.get()) as AppController
            app.control(Control.CABIN_TEMPERATURE, 100.0)
            val texts = all(activity.get().window.decorView).filterIsInstance<TextView>()
                .filter { it.isShown }.map { it.text.toString() }
            assertTrue(texts.any { it.contains("BLOCKED") })
            assertFalse(texts.any { it.contains("CONFIRMED") })
        } finally { activity.pause().stop().destroy() }
    }
}
