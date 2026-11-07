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
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class PickupNoticeTest {
    private fun all(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    private fun scenario(root: View, index: Int) {
        all(root).single { it.contentDescription == "시연 설정 열기" }.performLongClick()
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(index)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun pickupRevealsReadyNoticeAndResetsAfterDriving() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.get().window.decorView
        scenario(root, 2)
        val button = all(root).filterIsInstance<TextView>().single { it.text == "픽업 안내 확인" }
        val notice = all(root).single { it.tag == "pickup-notice" }
        assertEquals(View.GONE, notice.visibility)
        button.performClick()
        assertEquals(View.VISIBLE, notice.visibility)
        val texts = all(notice).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts.contains("A-12번"))
        assertTrue(texts.contains("주문하신 음식이 준비되었습니다.\n수령해 주세요."))
        scenario(root, 1)
        assertFalse(all(root).any { it.tag == "pickup-notice" })
        scenario(root, 2)
        assertEquals(View.GONE, all(root).single { it.tag == "pickup-notice" }.visibility)
        activity.pause().stop().destroy()
    }

    @Test fun staleButtonCannotRevealNoticeAfterSignalLossOrStop() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.get().window.decorView
        scenario(root, 2)
        val button = all(root).filterIsInstance<TextView>().single { it.text == "픽업 안내 확인" }
        val notice = all(root).single { it.tag == "pickup-notice" }
        scenario(root, 3)
        button.performClick()
        assertEquals(View.GONE, notice.visibility)
        assertFalse(all(root).any { it.tag == "pickup-notice" })
        activity.pause().stop().destroy()
        button.performClick()
        assertEquals(View.GONE, notice.visibility)
    }
}
