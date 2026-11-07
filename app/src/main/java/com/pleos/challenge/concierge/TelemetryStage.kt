package com.pleos.challenge.concierge

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.ViewGroup
import kotlin.math.min

/** A Telemetry uses the allocated app window, never the full physical display size. */
class TelemetryStage(context: Context, val visual: View, val panel: View, val energy: View, val heading: View) : ViewGroup(context) {
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private var wide = false
    init { addView(visual); addView(heading); addView(energy); addView(panel); tag = "telemetry-stage" }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        val gap = dp(8); wide = w >= dp(660) && h >= dp(280)
        fun exact(view: View, width: Int, height: Int) = view.measure(MeasureSpec.makeMeasureSpec(width.coerceAtLeast(1),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height.coerceAtLeast(1),MeasureSpec.EXACTLY))
        if (wide) {
            val panelWidth = dp(284)
            val headingHeight = min(dp(138),(h*.29f).toInt())
            exact(heading,panelWidth,headingHeight)
            exact(panel,panelWidth,h-headingHeight-gap*2)
            // Artwork is a background layer; the readable control card is drawn last.
            exact(visual,w-panelWidth-gap*3,h-dp(68))
            exact(energy,dp(172),dp(64))
        } else {
            val artHeight = min(dp(132),(h*.26f).toInt())
            exact(heading,w-gap*2,0)
            heading.visibility=GONE
            exact(visual,w-gap*2,artHeight)
            exact(energy,w-gap*2,dp(56))
            exact(panel,w-gap*2,h-artHeight-dp(56)-gap*2)
        }
        if (wide) heading.visibility=VISIBLE
        setMeasuredDimension(w,h)
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        val gap = dp(8)
        fun place(v: View,x: Int,y: Int) = v.layout(x,y,x+v.measuredWidth,y+v.measuredHeight)
        if (wide) {
            place(heading,gap,0)
            place(visual,panel.measuredWidth+gap*2,dp(68))
            place(energy,width-energy.measuredWidth-gap,gap)
            place(panel,gap,heading.measuredHeight+gap)
        } else { place(heading,gap,0);place(visual,gap,0);place(energy,gap,visual.bottom);place(panel,gap,energy.bottom+gap) }
    }
}

