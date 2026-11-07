package com.pleos.challenge.concierge

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs inside the isolated AVD. No host keyboard/mouse or screen focus is used. */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun capture(activity: Activity, name: String) {
        instrumentation.runOnMainSync {
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(activity.filesDir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun launchAndExerciseRealAndroidViews() {
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        Thread.sleep(1500)
        capture(activity,if (BuildConfig.DEMO_ONLY) "native_type_a_park" else "native_type_a_live")
        if (BuildConfig.DEMO_ONLY) {
            fun click(label: String) { instrumentation.runOnMainSync { nodes(activity.window.decorView).filterIsInstance<Button>().single { it.text == label }.performClick() }; Thread.sleep(650); instrumentation.waitForIdleSync() }
            click("주행")
            instrumentation.runOnMainSync { assertFalse(nodes(activity.window.decorView).filterIsInstance<TextView>().any { it.text == "캐빈 케어" }) }
            capture(activity,"native_type_a_drive")
            click("신호 없음")
            instrumentation.runOnMainSync { assertFalse(nodes(activity.window.decorView).any { it.contentDescription == "실내 온도 높이기" }) }
            capture(activity,"native_type_a_unavailable")
            click("정차")
        }
        instrumentation.runOnMainSync { activity.finish() }
    }
}
