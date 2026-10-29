package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationCourseTest {
    private val origin=GeoPoint(55.16,61.4)
    private fun point(north:Double=0.0,east:Double=0.0)=GeoPoint(origin.latitude+north/111320,
        origin.longitude+east/(111320*kotlin.math.cos(Math.toRadians(origin.latitude))))
    private fun tracker()=NavigationCourseTracker()
    @Test fun gpsCourseWinsWhileMovingIncludingValidNorth() {
        val t=tracker()
        assertEquals(270.0,t.update(origin,1000,270.0,8.0,5.0)!!,.01)
        assertEquals(0.0,t.update(point(north=8.0),2000,0.0,8.0,5.0)!!,.01)
    }
    @Test fun missingGpsCourseIsRecoveredFromActualMovement() {
        val t=tracker()
        assertNull(t.update(origin,1000,null,null,5.0))
        assertEquals(90.0,t.update(point(east=10.0),2000,null,null,5.0)!!,.02)
        assertEquals(180.0,t.update(point(east=10.0,north=-10.0),3000,null,null,5.0)!!,.02)
    }
    @Test fun smallMovementsAccumulateInsteadOfResettingEveryFix() {
        val t=tracker();t.update(origin,1000,null,null,5.0)
        assertNull(t.update(point(east=2.0),2000,null,null,5.0))
        assertNull(t.update(point(east=4.0),3000,null,null,5.0))
        assertEquals(90.0,t.update(point(east=6.0),4000,null,null,5.0)!!,.02)
    }
    @Test fun stationaryZeroBearingAndJitterDoNotSpinMapNorth() {
        val t=tracker();t.update(origin,1000,270.0,8.0,5.0)
        assertEquals(270.0,t.update(origin,2000,0.0,0.0,5.0)!!,.01)
        assertEquals(270.0,t.update(point(north=2.0),3000,0.0,0.0,5.0)!!,.01)
        assertEquals(270.0,t.update(point(east=-2.0),4000,null,null,5.0)!!,.01)
    }
    @Test fun poorFixDoesNotInventCourseFromDisplacement() {
        val t=tracker();t.update(origin,1000,270.0,8.0,5.0)
        assertEquals(270.0,t.update(point(north=60.0),2000,null,null,100.0)!!,.01)
        assertEquals(90.0,t.update(point(east=10.0),3000,null,null,5.0)!!,.02)
    }
    @Test fun staleOrOutOfOrderFixesCannotReverseCourse() {
        val t=tracker();t.update(origin,1000,90.0,8.0,5.0)
        assertEquals(90.0,t.update(point(east=-30.0),1000,270.0,8.0,5.0)!!,.01)
        assertEquals(90.0,t.update(point(north=100.0),20000,null,null,5.0)!!,.01)
        assertEquals(90.0,t.update(point(north=100.0,east=10.0),21000,null,null,5.0)!!,.02)
        t.reset();assertNull(t.update(origin,22000,null,null,5.0))
    }
}
