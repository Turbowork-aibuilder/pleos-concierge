package com.pleos.challenge.concierge

import android.graphics.*
import android.graphics.drawable.Drawable

/** Cached radial lighting and fine grain; no bitmap or blur is applied to readable text. */
class OrbitAmbientDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var leftLight: Shader? = null
    private var rightLight: Shader? = null
    private val grain = FloatArray(2048 * 2)
    override fun onBoundsChange(bounds: Rect) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return
        leftLight = RadialGradient(w * .28f,h * .38f,w * .65f,Color.argb(120,25,118,135),Color.TRANSPARENT,Shader.TileMode.CLAMP)
        rightLight = RadialGradient(w * .9f,h * .8f,w * .58f,Color.argb(90,48,65,128),Color.TRANSPARENT,Shader.TileMode.CLAMP)
        val random = java.util.Random(34)
        for (i in grain.indices step 2) { grain[i] = random.nextFloat() * w; grain[i+1] = random.nextFloat() * h }
    }
    override fun draw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(7,24,39))
        paint.alpha = 255
        paint.shader = leftLight; canvas.drawRect(bounds,paint)
        paint.shader = rightLight; canvas.drawRect(bounds,paint); paint.shader = null
        paint.color = Color.argb(8,208,237,248); paint.strokeWidth = 1f
        canvas.drawPoints(grain,paint)
    }
    override fun setAlpha(alpha: Int) { }
    override fun setColorFilter(colorFilter: ColorFilter?) { }
    @Suppress("DEPRECATION") override fun getOpacity(): Int = PixelFormat.OPAQUE
}
