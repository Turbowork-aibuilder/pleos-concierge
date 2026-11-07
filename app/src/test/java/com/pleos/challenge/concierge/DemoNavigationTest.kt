package com.pleos.challenge.concierge

import android.graphics.Bitmap
import android.graphics.Canvas
import com.pleos.challenge.concierge.core.AppMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class DemoNavigationTest {
    @Test fun scriptedJourneyClampsAndResetsWithoutExposingLiveRoute(): Unit {
        var time = 1000L
        val route = DemoNavigation { time }
        assertNull(route.state(AppMode.UNAVAILABLE))
        assertEquals(1200,route.state(AppMode.ECO_COACHING)!!.meters)
        time += 36000
        assertEquals(.5f,route.state(AppMode.ECO_COACHING)!!.progress,0.001f)
        assertTrue(route.state(AppMode.ECO_COACHING)!!.instruction.contains("좌회전"))
        time += 900000
        assertEquals(.98f,route.state(AppMode.ECO_COACHING)!!.progress,0.001f)
        assertEquals(0,route.state(AppMode.SMART_PICKUP)!!.meters)
        route.setDestination()
        assertEquals("설정한 목적지",route.state(AppMode.ECO_COACHING)!!.destination)
        assertEquals(0f,route.state(AppMode.ECO_COACHING)!!.progress,0f)
        val controller = AppController(RuntimeEnvironment.getApplication(),false) {}
        assertNull(controller.navigationState())
        controller.close()
        assertNull(controller.navigationState())
    }
    @Test fun routeExposesManeuverDistanceRoadAndArrivalClock(): Unit {
        var time = 0L
        val route = DemoNavigation { time }
        time = 18000
        val first = route.state(AppMode.ECO_COACHING)!!
        assertEquals(Maneuver.RIGHT,first.maneuver)
        assertEquals(120,first.nextMeters)
        assertEquals("에코대로",first.nextRoad)
        assertEquals(4,first.minutes)
        time = 36000
        val second = route.state(AppMode.ECO_COACHING)!!
        assertEquals(Maneuver.LEFT,second.maneuver)
        assertEquals("픽업길",second.nextRoad)
        assertTrue(second.instruction.contains("${second.nextMeters} m"))
        assertEquals(3,second.minutes)
        assertEquals("0.6 km",second.distanceText)
        val kst = java.time.ZoneId.of("Asia/Seoul")
        val now = java.time.ZonedDateTime.of(2026,11,4,14,28,0,0,kst).toInstant().toEpochMilli()
        assertEquals("14:31",second.arrivalClock(now,kst))
        time = 54000
        assertEquals(Maneuver.STRAIGHT,route.state(AppMode.ECO_COACHING)!!.maneuver)
        val arrived = route.state(AppMode.SMART_PICKUP)!!
        assertEquals(Maneuver.ARRIVED,arrived.maneuver)
        assertEquals(0,arrived.minutes)
        assertEquals("14:28",arrived.arrivalClock(now,kst))
    }
    @Test fun cameraFollowsRouteWithoutJumps(): Unit {
        assertEquals(0f,DemoCamera.at(.1f).heading,0.01f)
        assertEquals(90f,DemoCamera.at(.48f).heading,0.01f)
        assertEquals(0f,DemoCamera.at(.8f).heading,0.01f)
        assertEquals(.3f,DemoCamera.at(1f).x,0.001f)
        assertEquals(.93f,DemoCamera.at(1f).z,0.001f)
        assertEquals(DemoCamera.at(0f),DemoCamera.at(Float.NaN))
        (0 until 100).forEach { i ->
            val a = DemoCamera.at(i / 100f)
            val b = DemoCamera.at((i + 1) / 100f)
            assertTrue(kotlin.math.abs(b.heading - a.heading) <= 15f)
            assertTrue(kotlin.math.hypot(b.x - a.x, b.z - a.z) <= .02f)
        }
    }
    @Test fun mapAcceptsExtremeInputsAndSmallWindows(): Unit {
        val map = NavigationMapView(RuntimeEnvironment.getApplication())
        listOf(Float.NaN,Float.POSITIVE_INFINITY,-10f,1f).forEach { progress ->
            map.update(progress)
            map.layout(0,0,320,96)
            map.draw(Canvas(Bitmap.createBitmap(320,96,Bitmap.Config.ARGB_8888)))
        }
        map.stop()
        assertFalse(map.isClickable)
    }
}
