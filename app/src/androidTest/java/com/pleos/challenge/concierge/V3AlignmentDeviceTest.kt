package com.pleos.challenge.concierge

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** On-device views only; never sends host keyboard/mouse input. */
@RunWith(AndroidJUnit4::class)
class V3AlignmentDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()

    private fun capture(activity: Activity, name: String): Unit {
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(activity.filesDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun checkDemo(activity: Activity): Unit {
        val all = nodes(activity.window.decorView)
        all.single { it.contentDescription == "실내 온도 높이기" }.performClick()
        val labels = all.filterIsInstance<TextView>().filter { it.isShown }
        assertTrue(labels.any { it.text.contains("요청값") })
        val result = labels.single { it.text.contains("CONFIRMED") }
        assertTrue(result.bottom <= (result.parent as View).height)
    }
    @Test fun controlResultIsVisibleInRealAndroidLayout(): Unit {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            Thread.sleep(1200)
            instrumentation.runOnMainSync { if (BuildConfig.DEMO_ONLY) checkDemo(activity) }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                capture(activity, if (BuildConfig.DEMO_ONLY) "v3_control_confirmed" else "v3_live")
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
