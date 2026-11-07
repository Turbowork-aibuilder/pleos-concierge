package com.pleos.challenge.concierge

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.pleos.challenge.concierge.platform.CallbackGate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class PlatformTest {
    @Test fun callbacksAreOneShotAndExpire() {
        val gate = CallbackGate()
        var delivered = 0
        lateinit var late: (Result<Int>) -> Unit
        gate.request<Int>(50, { delivered++; assertTrue(it.isFailure) }) { late = it }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(51))
        late(Result.success(1)); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, delivered)
    }
    @Test fun closingRejectsLateCallbacksAndNewRequests() {
        val gate = CallbackGate()
        var delivered = 0
        lateinit var late: (Result<Int>) -> Unit
        gate.request<Int>(callback = { delivered++; assertTrue(it.isFailure) }) { late = it }
        gate.close(); late(Result.success(1)); shadowOf(Looper.getMainLooper()).idle()
        gate.request<Int>(callback = { delivered++; assertTrue(it.isFailure) }) { fail("closed session invoked SDK") }
        assertEquals(2, delivered)
    }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun scenario(root: View, index: Int) {
        all(root).single { it.contentDescription?.contains("시연 설정") == true }.performLongClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        shadowOf(dialog).clickOnItem(index)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun demoDrivingRemovesCabinControlsAndReturnsToPark() {
        assertTrue(BuildConfig.DEMO_ONLY)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.get().window.decorView
        fun texts() = all(root).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(texts().any { it.contains("캐빈 케어") })
        assertFalse(texts().any { it in listOf("정차 · P단", "주행 · 60 km/h", "도착 · 목적지 접근 + 정차", "신호 없음") })
        all(root).single { it.contentDescription?.contains("시연 설정") == true }.performLongClick()
        val items = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().listView.adapter
        assertEquals("도착 · 목적지 접근 + 정차", items.getItem(2).toString())   // the arrival input names its conditions
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().dismiss()
        scenario(root, 1)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(texts().any { it.contains("캐빈 케어") })
        assertTrue(texts().contains("60"))
        scenario(root, 0)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(texts().any { it.contains("캐빈 케어") })
        activity.pause().stop().destroy()
    }
    @Test fun missingSignalsNeverShowControlButtons() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.get().window.decorView
        scenario(root, 3)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(all(root).any { it.contentDescription?.contains("온도 높이기") == true })
        activity.pause().stop().destroy()
    }
    @Test fun stoppingDismissesPresenterSettings() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        all(activity.get().window.decorView).single { it.contentDescription?.contains("시연 설정") == true }.performLongClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        activity.pause().stop().destroy()
        assertFalse(dialog.isShowing)
    }
    @Test fun stoppingWithDestinationDialogDismissesWindow() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        all(activity.get().window.decorView).single { it.contentDescription == "도착 탭" }.performClick()
        all(activity.get().window.decorView).filterIsInstance<Button>().single { it.text == "목적지 · 경유지 설정" }.performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        activity.pause().stop().destroy()
        assertFalse(dialog.isShowing)
    }
    @Test fun telemetryRetainsRequestsAcrossTabsAndDriving() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.get().window.decorView
        fun click(description: String) = all(root).single { it.contentDescription == description }.performClick()
        click("실내 온도 높이기")
        click("시트 탭"); click("시트 온열 높이기"); click("캐빈 탭")
        assertEquals("23 °C", all(root).filterIsInstance<TextView>().single { it.tag == "request-CABIN_TEMPERATURE" }.text)
        val oldVisual = all(root).filterIsInstance<TelemetryVehicleView>().single()
        scenario(root,1)
        assertFalse(oldVisual.isPulsing)
        assertFalse(all(root).any { it is TelemetryStage || it is TelemetryVehicleView })
        assertFalse(all(root).any { it.contentDescription == "캐빈 탭" })
        scenario(root,0)
        assertEquals("23 °C",all(root).filterIsInstance<TextView>().single { it.tag == "request-CABIN_TEMPERATURE" }.text)
        click("시트 탭")
        assertEquals("1 단계",all(root).filterIsInstance<TextView>().single { it.tag == "request-SEAT_HEAT" }.text)
        activity.pause().stop().destroy()
    }
    @Test fun telemetryRestoresRequestedValuesAfterActivityRecreation() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        fun root() = activity.get().window.decorView
        repeat(20) { all(root()).single { it.contentDescription == "실내 온도 높이기" }.performClick() }
        assertEquals("28 °C",all(root()).filterIsInstance<TextView>().single { it.tag == "request-CABIN_TEMPERATURE" }.text)
        all(root()).single { it.contentDescription == "시트 탭" }.performClick()
        all(root()).single { it.contentDescription == "시트 통풍 높이기" }.performClick()
        activity.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(all(root()).filterIsInstance<Button>().single { it.contentDescription == "시트 탭" }.isSelected)
        assertEquals("1 단계",all(root()).filterIsInstance<TextView>().single { it.tag == "request-SEAT_VENTILATION" }.text)
        all(root()).single { it.contentDescription == "캐빈 탭" }.performClick()
        assertEquals("28 °C",all(root()).filterIsInstance<TextView>().single { it.tag == "request-CABIN_TEMPERATURE" }.text)
        activity.pause().stop().destroy()
    }
    @Test fun telemetryDestinationDismissesOnSignalLoss() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.get().window.decorView
        all(root).single { it.contentDescription == "도착 탭" }.performClick()
        all(root).filterIsInstance<Button>().single { it.text == "목적지 · 경유지 설정" }.performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        scenario(root,3)
        assertFalse(dialog.isShowing)
        assertFalse(all(root).any { it is TelemetryStage || it is TelemetryVehicleView })
        activity.pause().stop().destroy()
    }
    @Test fun telemetryFitsMeasuredVehicleWindowAndCompactSizes() {
        val activity=Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val stage=all(activity.get().window.decorView).filterIsInstance<TelemetryStage>().single()
        for ((width,height) in listOf(798 to 398, 664 to 318, 1280 to 600, 360 to 500)) {
            stage.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
            stage.layout(0,0,width,height)
            for (child in listOf(stage.visual,stage.panel,stage.energy,stage.heading)) {
                assertTrue("child outside $width x $height",child.left>=0&&child.top>=0&&child.right<=width&&child.bottom<=height)
            }
            val artwork=android.graphics.Rect(stage.visual.left,stage.visual.top,stage.visual.right,stage.visual.bottom)
            val controls=android.graphics.Rect(stage.panel.left,stage.panel.top,stage.panel.right,stage.panel.bottom)
            // A floats the readable control card over the decorative artwork, never vice versa.
            assertSame(stage.panel,stage.getChildAt(stage.childCount-1))
            assertFalse(stage.visual.isClickable)
            val metrics=android.graphics.Rect(stage.energy.left,stage.energy.top,stage.energy.right,stage.energy.bottom)
            assertFalse(android.graphics.Rect.intersects(metrics,controls))
            assertTrue(all(stage.panel).filterIsInstance<Button>().filter { it.contentDescription?.endsWith("탭")==true }.all { it.measuredHeight>=44 })
        }
        activity.pause().stop().destroy()
    }
    @Test fun telemetryRespectsDisabledAnimationScale() {
        val scale = android.animation.ValueAnimator::class.java.getDeclaredMethod("setDurationScale",Float::class.javaPrimitiveType)
        scale.invoke(null,0f)
        try {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
            shadowOf(Looper.getMainLooper()).idle()
            val root = activity.get().window.decorView
            assertFalse(all(root).filterIsInstance<TelemetryVehicleView>().single().isPulsing)
            scenario(root,1); scenario(root,0)
            assertFalse(all(root).filterIsInstance<TelemetryVehicleView>().single().isPulsing)
            activity.pause().stop().destroy()
        } finally { scale.invoke(null,1f) }
    }
    @Test fun closedControllerCannotResurrectDemoSamples() {
        val controller = AppController(org.robolectric.RuntimeEnvironment.getApplication(),true) { }
        controller.start(); shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controller.snapshot.connected)
        controller.close()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertFalse(controller.snapshot.connected)
    }
}
