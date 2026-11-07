package com.pleos.challenge.concierge

import kotlin.math.roundToInt

/** One viewport of horizontal movement turns the vehicle half a revolution. */
object VehicleRotation {
    fun normalize(yaw: Float): Float = ((yaw % 360f) + 360f) % 360f
    fun drag(yaw: Float, dx: Float, width: Float): Float =
        if (width <= 0f) normalize(yaw) else normalize(yaw + dx / width * 180f)
    fun frame(yaw: Float): Int = (normalize(yaw) / 2f).roundToInt() % 180
}
