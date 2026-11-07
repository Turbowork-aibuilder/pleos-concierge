package com.pleos.challenge.concierge

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w1280dp-h800dp-land-mdpi")
class NavigationUiTest {
    private fun all(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun scenario(root: View, index: Int) {
        all(root).single { it.contentDescription == "시연 설정 열기" }.performLongClick()
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(index)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun drivingShowsReadOnlyRouteAndArrivalRetainsPickup() {
        val activity=Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root=activity.get().window.decorView
        scenario(root,1)
        val map=all(root).single { it.tag=="navigation-map" }
        assertFalse(map.isClickable)
        assertFalse(all(root).any { it.contentDescription=="실내 온도 높이기" })
        assertTrue(all(root).filterIsInstance<TextView>().any { it.text=="60" })
        assertTrue(all(root).filterIsInstance<TextView>().any { it.text=="72%" })
        scenario(root,2)
        assertEquals(1,all(root).count { it.tag=="navigation-map" })
        assertTrue(all(root).filterIsInstance<TextView>().any { it.text=="목적지 도착" })
        all(root).filterIsInstance<TextView>().single { it.text=="픽업 안내 확인" }.performClick()
        assertEquals(View.VISIBLE,all(root).single { it.tag=="pickup-notice" }.visibility)
        assertEquals(0,all(root).filterIsInstance<TelemetryVehicleView>().size)   // arrival: map replaces the vehicle
        scenario(root,3)
        assertFalse(all(root).any { it.tag=="navigation-map" })
        activity.pause().stop().destroy()
    }
    @Test fun drivingShowsTurnCardAndArrivalBar() {
        val activity=Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root=activity.get().window.decorView
        val texts={ tag: String -> all(all(root).single { it.tag==tag }).filterIsInstance<TextView>().joinToString(" ") { it.text } }
        scenario(root,1)
        assertTrue(texts("nav-turn-card").contains("에코대로"))
        assertTrue(texts("nav-turn-card").contains(" m"))
        val bar=texts("nav-arrival-bar")
        assertTrue(Regex("\\d{2}:\\d{2}").containsMatchIn(bar))
        assertTrue(bar.contains("분") && bar.contains("km"))
        scenario(root,2)
        assertTrue(texts("nav-turn-card").contains("목적지 도착"))
        activity.pause().stop().destroy()
    }
}
