package com.pleos.challenge.concierge

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

/** The approved single image is drawn uniformly, without invented viewpoints. */
class TelemetryVehicleView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val image = context.assets.open("artwork/titanium-ev.webp")
        .use { BitmapFactory.decodeStream(it) }
    internal val isPulsing = false
    init { contentDescription = "차량 외관" }
    internal fun artworkBounds(): RectF {
        val scale = min(width * .92f / image.width, height * .9f / image.height)
        val w = image.width * scale
        val h = image.height * scale
        val x = (width - w) / 2
        val y = (height - h) * .46f
        return RectF(x, y, x + w, y + h)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val box = artworkBounds()
        drawGround(canvas, box)
        canvas.drawBitmap(image, null, box, paint)
    }
    private fun drawGround(canvas: Canvas, box: RectF) {
        canvas.save()
        canvas.translate(box.centerX(), box.top + box.height() * .86f)
        canvas.scale(box.width() * .4f, box.height() * .07f)
        paint.shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(Color.argb(95, 0, 6, 12), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, 1f, paint)
        paint.shader = null
        canvas.restore()
    }
}
