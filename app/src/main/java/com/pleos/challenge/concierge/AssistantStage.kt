package com.pleos.challenge.concierge

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** DEMO park screen: the vehicle artwork, or the scripted assistant conversation when the chip is tapped. */
class AssistantStage(context: Context, private val vehicle: View, private val font: Typeface) : FrameLayout(context) {
    private val ink = Color.rgb(238, 245, 251)
    private val muted = Color.rgb(168, 187, 207)
    private val mint = Color.rgb(163, 247, 223)
    private val bubbles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(6)) }
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(bubbles) }
    private val phase = text("", 13f, mint)
    private val panel = chatPanel()
    private val chip = text("● 어시스턴트", 15f, mint)
    private val texts = mutableListOf<TextView>()
    private var clock: ValueAnimator? = null

    init {
        tag = "assistant-stage"
        addView(vehicle, LayoutParams(-1, -1))
        addView(panel, LayoutParams(-1, -1).apply { setMargins(dp(8), dp(56), dp(8), dp(4)) })
        panel.visibility = GONE
        chip.apply {
            contentDescription = "어시스턴트 대화"; gravity = Gravity.CENTER; minHeight = dp(44)
            setPadding(dp(18), 0, dp(18), 0); background = round(Color.argb(200, 20, 48, 66), 22, Color.argb(140, 163, 247, 223))
            setOnClickListener { if (panel.visibility == VISIBLE) close() else open() }
        }
        addView(chip, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(4), dp(8), 0) })
    }

    private fun chatPanel() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; tag = "assistant-panel"
        setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(238, 30, 62, 78), Color.argb(236, 14, 36, 52))).apply {
            cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.argb(90, 183, 222, 229)) }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("PLEOS ASSISTANT", 12f, muted).apply { letterSpacing = .14f }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(phase)
        addView(header)
        addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
    }

    private fun open() {
        vehicle.visibility = INVISIBLE; panel.visibility = VISIBLE; chip.text = "닫기"
        bubbles.removeAllViews(); texts.clear(); show(0)
        clock = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DemoAssistant.totalMs + 400; interpolator = LinearInterpolator()
            addUpdateListener { show(it.currentPlayTime) }
            start()
        }
    }

    private fun close() {
        clock?.cancel(); clock = null
        panel.visibility = GONE; vehicle.visibility = VISIBLE; chip.text = "● 어시스턴트"
    }

    private fun show(elapsedMs: Long) {
        val frame = DemoAssistant.frameAt(elapsedMs)
        phase.text = frame.phase.label
        val added = frame.lines.size > texts.size
        frame.lines.drop(texts.size).forEach { addBubble(it.line.speaker) }
        frame.lines.forEachIndexed { index, shown -> texts[index].text = shown.text }
        // Chat-style: the newest line stays in view, older ones slide up.
        if (added) scroll.post { scroll.smoothScrollTo(0, bubbles.height) }
    }

    private fun addBubble(speaker: Speaker) {
        val driver = speaker == Speaker.DRIVER
        val body = text("", 17f, ink).apply {
            setLineSpacing(dp(3).toFloat(), 1f); maxWidth = (bubbles.width.coerceAtLeast(dp(300)) * .78f).toInt()
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = if (driver) round(Color.rgb(62, 98, 111), 18, Color.TRANSPARENT)
            else round(Color.argb(210, 12, 30, 42), 18, Color.argb(150, 163, 247, 223))
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = if (driver) Gravity.END else Gravity.START
            addView(text(if (driver) "운전자" else "어시스턴트", 12f, if (driver) muted else mint).apply { setPadding(dp(6), 0, dp(6), dp(4)) })
            addView(body)
        }
        bubbles.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        texts.add(body)
        if (ValueAnimator.areAnimatorsEnabled()) {
            row.alpha = 0f; row.translationY = dp(12).toFloat()
            row.animate().alpha(1f).translationY(0f).setDuration(260).start()
        }
    }

    override fun onDetachedFromWindow() { clock?.cancel(); clock = null; super.onDetachedFromWindow() }

    private fun text(value: String, size: Float, color: Int) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); typeface = font; includeFontPadding = false
    }

    private fun round(color: Int, radius: Int, stroke: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
