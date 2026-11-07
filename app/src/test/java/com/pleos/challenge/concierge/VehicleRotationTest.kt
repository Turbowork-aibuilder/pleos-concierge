package com.pleos.challenge.concierge

import org.junit.Assert.*
import org.junit.Test

class VehicleRotationTest {
    @Test fun dragUsesTheVehicleViewportAndWrapsContinuously() {
        assertEquals(90f, VehicleRotation.drag(0f, 200f, 400f), .001f)
        assertEquals(270f, VehicleRotation.drag(0f, -200f, 400f), .001f)
        assertEquals(10f, VehicleRotation.drag(350f, 40f, 360f), .001f)
    }
    @Test fun nearestSingleViewCrossesTheSeamWithoutDoubleContours() {
        assertEquals(179, VehicleRotation.frame(358f))
        assertEquals(0, VehicleRotation.frame(359.9f))
        assertEquals(0, VehicleRotation.frame(360f))
        assertEquals(45, VehicleRotation.frame(90f))
    }
    @Test fun everyDegreeUsesExactlyOneValidViewpoint() {
        (0..360).forEach { assertTrue(VehicleRotation.frame(it.toFloat()) in 0..179) }
    }
    @Test fun unknownWidthCannotMoveTheCar() {
        assertEquals(25f, VehicleRotation.drag(25f, 300f, 0f), .001f)
    }
}
