package com.pleos.challenge.concierge

import kotlin.math.hypot

/** Follow-camera pose on the scripted DEMO route. Heading 0° faces +z, 90° faces +x. */
data class DemoCamera(val x: Float, val z: Float, val heading: Float) {
    companion object {
        val route: List<Pair<Float, Float>> = listOf(0f to .03f, 0f to .45f, .3f to .45f, .3f to .93f)
        private const val LENGTH: Float = 1.2f
        private const val BLEND: Float = .04f
        fun at(progress: Float): DemoCamera {
            val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
            val (x, z) = point(p * LENGTH)
            return DemoCamera(x, z, 90f * (ramp(p - .35f) - ramp(p - .6f)))
        }
        private fun ramp(offset: Float): Float = ((offset + BLEND) / (2 * BLEND)).coerceIn(0f, 1f)
        private fun point(distance: Float): Pair<Float, Float> {
            var left = distance
            route.zipWithNext().forEach { (a, b) ->
                val length = hypot(b.first - a.first, b.second - a.second)
                if (left <= length) {
                    val t = left / length
                    return (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
                }
                left -= length
            }
            return route.last()
        }
    }
}
