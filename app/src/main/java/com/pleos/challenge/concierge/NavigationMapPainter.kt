package com.pleos.challenge.concierge

import android.graphics.*
import kotlin.math.cos
import kotlin.math.sin

/** Perspective streets and extruded blocks of a fictional DEMO district, seen from a camera that follows the car. */
class NavigationMapPainter {
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fog: Paint = Paint()
    private val arrow: List<PointF> = listOf(PointF(0f,-1f),PointF(.8f,1f),PointF(0f,.55f),PointF(-.8f,1f))
    private val streets: List<RectF> = listOf(-.3f,.1f,.45f,.70f,.94f,1.2f,1.5f,1.8f).map { RectF(-3.2f,it-.035f,3.2f,it+.035f) } +
        listOf(-.88f,-.44f,0f,.3f,.8f,1.3f).map { RectF(it-.035f,-1.6f,it+.035f,3.2f) }
    private val blocks: List<RectF> = listOf(-.2f,.20f,.52f,.76f,1.0f,1.28f,1.58f).flatMap { z ->
        listOf(-.78f,-.34f,.04f,.45f,.9f).map { x -> RectF(x,z,x+.22f,z+.13f) }
    }
    private var width: Float = 1f
    private var height: Float = 1f
    private var fogHeight: Float = -1f
    private var camera: DemoCamera = DemoCamera.at(0f)
    private var sine: Float = 0f
    private var cosine: Float = 1f
    private fun view(x: Float, z: Float): PointF {
        val dx = x - camera.x; val dz = z - camera.z
        return PointF(dx * cosine - dz * sine, dx * sine + dz * cosine + CAR)
    }
    /** True pinhole projection: straight ground lines stay straight while the camera turns. */
    private fun project(p: PointF, elevation: Float = 0f): PointF {
        val scale = 1f / (1f + p.y)
        return PointF(width * (.5f + p.x * .58f * scale), height * (HORIZON + (SPAN - elevation) * scale))
    }
    private fun clip(points: List<PointF>, bound: Float, far: Boolean): List<PointF> = points.indices.flatMap { i ->
        val a = points[i]; val b = points[(i + 1) % points.size]
        val inside = { p: PointF -> if (far) p.y <= bound else p.y >= bound }
        val cross = if (inside(a) != inside(b)) listOf(lerp(a, b, (bound - a.y) / (b.y - a.y))) else emptyList()
        (if (inside(a)) listOf(a) else emptyList()) + cross
    }
    private fun clip(points: List<PointF>): List<PointF> = clip(clip(points, FAR, true), NEAR, false)
    /** Open polyline cut at NEAR; the driven part of the route is a prefix, so the kept part stays connected. */
    private fun trail(points: List<PointF>): List<PointF> = points.zipWithNext().flatMap { (a, b) ->
        val cut = { lerp(a, b, (NEAR - a.y) / (b.y - a.y)) }
        when {
            a.y >= NEAR && b.y >= NEAR -> listOf(a, b)
            a.y >= NEAR -> listOf(a, cut())
            b.y >= NEAR -> listOf(cut(), b)
            else -> emptyList()
        }
    }
    private fun lerp(a: PointF, b: PointF, t: Float): PointF = PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    private fun corners(r: RectF): List<PointF> = listOf(view(r.left,r.top),view(r.right,r.top),view(r.right,r.bottom),view(r.left,r.bottom))
    private fun polygon(canvas: Canvas, points: List<PointF>, color: Int, stroke: Float = 0f): Unit {
        if (points.size < 2) return
        val path = Path().apply { moveTo(points[0].x,points[0].y); points.drop(1).forEach { lineTo(it.x,it.y) }; if (stroke == 0f) close() }
        paint.color = color
        paint.style = if (stroke == 0f) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(path,paint)
    }
    private fun block(canvas: Canvas, ground: List<PointF>): Unit {
        ground.indices.sortedByDescending { ground[it].y + ground[(it + 1) % 4].y }.forEach { i ->
            val a = ground[i]; val b = ground[(i + 1) % 4]
            val color = if (i % 2 == 0) Color.rgb(18,43,55) else Color.rgb(25,52,64)
            polygon(canvas,listOf(project(a),project(b),project(b,ROOF),project(a,ROOF)),color)
        }
        polygon(canvas,ground.map { project(it,ROOF) },Color.rgb(37,65,77))
    }
    private fun district(canvas: Canvas): Unit {
        streets.forEach { polygon(canvas,clip(corners(it)).map { project(it) },Color.rgb(36,67,79)) }
        blocks.map { corners(it) }.filter { ground -> ground.all { it.y in NEAR..FAR } }
            .sortedByDescending { ground -> ground.sumOf { it.y.toDouble() } }.forEach { block(canvas,it) }
        val line = trail(DemoCamera.route.map { view(it.first,it.second) }).map { project(it) }
        listOf(.022f to Color.rgb(10,42,51),.011f to Color.rgb(136,241,210)).forEach { (size,color) -> polygon(canvas,line,color,width*size) }
    }
    private fun dot(canvas: Canvas, p: PointF, radius: Float, color: Int): Unit {
        paint.color = color; paint.style = Paint.Style.FILL
        canvas.drawCircle(p.x,p.y,radius,paint)
    }
    private fun follow(progress: Float): Unit {
        camera = DemoCamera.at(progress)
        val radians = Math.toRadians(camera.heading.toDouble())
        sine = sin(radians).toFloat(); cosine = cos(radians).toFloat()
    }
    fun draw(canvas: Canvas, w: Int, h: Int, progress: Float): Unit {
        width = w.toFloat(); height = h.toFloat()
        follow(progress)
        canvas.drawColor(Color.rgb(10,31,43))
        district(canvas)
        DemoCamera.route.last().let { view(it.first,it.second) }.takeIf { it.y >= NEAR }?.let { project(it) }?.let { destination ->
            dot(canvas,destination,width*.019f,Color.rgb(160,246,222))
            dot(canvas,destination,width*.007f,Color.rgb(10,31,43))
        }
        if (fogHeight != height) {
            fogHeight = height
            fog.shader = LinearGradient(0f,0f,0f,height*.32f,Color.rgb(10,31,43),Color.argb(0,10,31,43),Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f,0f,width,height*.32f,fog)
        val p = project(PointF(0f,CAR))
        val radius = width*.019f
        dot(canvas,p,radius*2.4f,Color.argb(45,160,246,222))
        canvas.save()
        canvas.translate(p.x,p.y)
        canvas.scale(radius,radius)
        polygon(canvas,arrow,Color.WHITE)
        canvas.restore()
    }
    private companion object {
        const val CAR: Float = .18f
        const val FAR: Float = 1.6f
        const val NEAR: Float = -.3f
        const val HORIZON: Float = -.805f
        const val SPAN: Float = 1.89f
        const val ROOF: Float = .055f
    }
}
