package com.pleos.challenge.concierge

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.view.animation.LinearInterpolator

class NavigationMapView(context: Context) : View(context) {
    private val painter: NavigationMapPainter = NavigationMapPainter()
    private var progress: Float = 0f
    private var target: Float = -1f
    private var motion: ValueAnimator? = null
    init { tag = "navigation-map"; isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun update(value: Float): Unit {
        val next = if (value.isFinite()) value.coerceIn(0f,1f) else 0f
        if (target == next) return
        motion?.cancel()
        val previous = target
        target = next
        if (!canAnimate(previous)) {
            progress = next; invalidate(); return
        }
        motion = ValueAnimator.ofFloat(progress,next).apply {
            duration = 500; interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }
    private fun canAnimate(previous: Float): Boolean = previous >= 0 && isAttachedToWindow && ValueAnimator.areAnimatorsEnabled()
    fun stop(): Unit { motion?.cancel(); motion = null }
    override fun onDetachedFromWindow(): Unit { stop(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas): Unit { super.onDraw(canvas); painter.draw(canvas,width,height,progress) }
}
