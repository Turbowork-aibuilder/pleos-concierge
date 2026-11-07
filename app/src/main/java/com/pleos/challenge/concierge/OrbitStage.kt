package com.pleos.challenge.concierge

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import kotlin.math.abs
import kotlin.math.min

/** C layout: four anchored panel positions, with a stacked layout in narrow windows. */
class OrbitStage(context: Context, val visual: View, val panel: ScrollView, val energy: View,
                 initialDock: Int, private val dockChanged: (Int) -> Unit) : ViewGroup(context) {
    var dock: Int = initialDock.coerceIn(0, 3)
        private set
    var wide: Boolean = false
        private set
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(26, 114, 205, 225); strokeWidth = 1f }
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    init {
        setWillNotDraw(false)
        background = OrbitAmbientDrawable()
        addView(visual); addView(panel); addView(energy)
        tag = "orbit-stage"
    }

    fun movePanel(next: Int) {
        dock = next.coerceIn(0, 3)
        dockChanged(dock)
        requestLayout()
    }

    /** Dragging only starts on the dedicated handle; scrolling and controls stay independent. */
    fun bindHandle(handle: View) {
        handle.contentDescription = "패널 이동 손잡이. 길게 끌어 모서리로 이동"
        handle.setOnTouchListener { v, event ->
            if (!wide) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; parent?.requestDisallowInterceptTouchEvent(true); true }
                MotionEvent.ACTION_MOVE -> true
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downX) + abs(event.rawY - downY) > slop) {
                        val position = IntArray(2); getLocationOnScreen(position)
                        val right = event.rawX - position[0] > width / 2f
                        val bottom = event.rawY - position[1] > height / 2f
                        movePanel(if (bottom) { if (right) 2 else 3 } else { if (right) 1 else 0 })
                    } else v.performClick()
                    parent?.requestDisallowInterceptTouchEvent(false); true
                }
                MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); true }
                else -> false
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(dp(200))
        wide = w >= dp(700) && h >= dp(360)
        val margin = dp(16)
        fun exact(v: View, width: Int, height: Int) = v.measure(
            MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height.coerceAtLeast(1), MeasureSpec.EXACTLY))
        if (wide) {
            val pw = dp(292)
            exact(panel, pw, min(h - margin * 2, dp(430)))
            exact(visual, w - pw - margin * 3, h - dp(100))
            exact(energy, dp(200), dp(76))
        } else {
            val visualHeight = min(dp(270), h / 2)
            exact(visual, w - margin * 2, visualHeight)
            exact(energy, w - margin * 2, dp(48))
            exact(panel, w - margin * 2, h - visualHeight - dp(64))
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val m = dp(16)
        fun place(v: View, x: Int, y: Int) = v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        if (wide) {
            val right = dock == 1 || dock == 2
            val bottom = dock >= 2
            place(panel, if (right) width - panel.measuredWidth - m else m,
                if (bottom) height - panel.measuredHeight - m else m)
            place(visual, if (right) m else panel.measuredWidth + m * 2, 0)
            place(energy, if (right) m else width - energy.measuredWidth - m, height - energy.measuredHeight - m)
        } else {
            place(visual, m, 0)
            place(energy, m, visual.measuredHeight)
            place(panel, m, visual.measuredHeight + energy.measuredHeight + dp(8))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = dp(32).coerceAtLeast(1)
        for (x in 0..width step gap) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), grid)
        for (y in 0..height step gap) canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), grid)
    }
}

/** Decorative original vector drawing, deliberately not a sensor or proximity display. */
class OrbitVehicleView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var pulse = 0f
    private var animator: ValueAnimator? = null
    internal val isPulsing: Boolean get() = animator?.isRunning == true
    private val body = Path().apply {
        moveTo(37f,20f); quadTo(70f,3f,103f,20f); lineTo(116f,54f); lineTo(116f,207f)
        quadTo(114f,232f,94f,240f); lineTo(46f,240f); quadTo(26f,232f,24f,207f)
        lineTo(24f,54f); close()
    }
    private val metal = LinearGradient(24f,0f,116f,0f,intArrayOf(Color.rgb(74,116,132),Color.rgb(210,237,240),Color.rgb(157,201,208),Color.rgb(53,92,112)),null,Shader.TileMode.CLAMP)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); startPulse() }
    override fun onDetachedFromWindow() { stopPulse(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) startPulse() else stopPulse()
    }
    private fun startPulse() {
        if (animator != null || !ValueAnimator.areAnimatorsEnabled()) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (!ValueAnimator.areAnimatorsEnabled()) stopPulse()
                else { pulse = it.animatedValue as Float; invalidate() }
            }
            start()
        }
    }
    private fun stopPulse() { animator?.cancel(); animator = null }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density
        paint.color = Color.argb(85, 116, 218, 237)
        bounds.set(cx - width * .42f, cy - height * .41f, cx + width * .42f, cy + height * .41f)
        canvas.drawOval(bounds, paint)
        bounds.inset(width * .055f, height * .055f); paint.alpha = 35; canvas.drawOval(bounds, paint)
        bounds.inset(-width * .05f * pulse, -height * .05f * pulse)
        paint.alpha = ((1f - pulse) * 60).toInt(); canvas.drawOval(bounds, paint)
        canvas.save()
        val scale = min(width / 260f, height / 275f)
        canvas.translate(cx - 70f * scale, cy - 125f * scale); canvas.scale(scale, scale)
        paint.alpha = 255; paint.style = Paint.Style.FILL; paint.shader = metal
        canvas.drawPath(body,paint); paint.shader = null
        paint.color = Color.rgb(13,43,61)
        bounds.set(40f,58f,100f,98f); canvas.drawRoundRect(bounds,10f,10f,paint)
        bounds.set(42f,105f,98f,158f); canvas.drawRoundRect(bounds,7f,7f,paint)
        bounds.set(39f,169f,101f,205f); canvas.drawRoundRect(bounds,9f,9f,paint)
        paint.color = Color.rgb(209,252,255); paint.strokeWidth = 5f; paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(36f,28f,52f,28f,paint); canvas.drawLine(88f,28f,104f,28f,paint)
        paint.strokeWidth = 3f; paint.color = Color.rgb(105,211,227)
        canvas.drawLine(35f,225f,53f,225f,paint); canvas.drawLine(87f,225f,105f,225f,paint)
        paint.strokeWidth = 2f; canvas.drawLine(30f,75f,30f,193f,paint); canvas.drawLine(110f,75f,110f,193f,paint)
        canvas.restore()
    }
}
