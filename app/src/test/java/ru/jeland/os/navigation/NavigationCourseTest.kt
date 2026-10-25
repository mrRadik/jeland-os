package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.NavigationState

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
    @Test fun routeFallbackUsesCurrentLegNotBearingToNextTurnOrDestination() {
        val route=listOf(origin,point(east=100.0),point(east=100.0,north=100.0)).map {it.latitude to it.longitude}
        assertEquals(90.0,routeForwardCourse(route,point(east=40.0))!!,.02)
        assertEquals(0.0,routeForwardCourse(route,point(east=100.0,north=40.0))!!,.02)
    }
    @Test fun routeFallbackHandlesAllFourDirectionsWithCarUpright() {
        for((end,expected) in listOf(point(north=100.0) to 0.0,point(east=100.0) to 90.0,
            point(north=-100.0) to 180.0,point(east=-100.0) to 270.0)) {
            val heading=routeForwardCourse(listOf(origin.latitude to origin.longitude,end.latitude to end.longitude),origin)!!
            assertEquals(expected,heading,.02)
            val camera=navigationFollowCamera(origin,NavigationCamera(origin,16.5),true,heading)
            assertEquals(0f,navigationMarkerRotation(heading,camera.bearing))
        }
    }
    @Test fun remainingDistanceDisambiguatesOverlappingReturnLeg() {
        val route=listOf(origin,point(east=100.0),origin).map {it.latitude to it.longitude}
        assertEquals(90.0,routeForwardCourse(route,point(east=40.0),160.0)!!,.02)
        assertEquals(270.0,routeForwardCourse(route,point(east=40.0),40.0)!!,.02)
    }
    @Test fun fallbackRejectsRemoteFixAndDegenerateGeometry() {
        val pair=origin.latitude to origin.longitude
        val route=listOf(pair,point(east=100.0).let {it.latitude to it.longitude})
        assertNull(routeForwardCourse(route,point(north=500.0)))
        assertNull(routeForwardCourse(listOf(pair,pair),origin))
        assertNull(routeForwardCourse(listOf(pair,Double.NaN to 61.4),origin))
        assertEquals(90.0,routeForwardCourse(listOf(pair)+route,null)!!,.02)
    }
    @Test fun sameCourseFeedsMainMapAndWidgetOnlyDuringGuidance() {
        val route=listOf(origin.latitude to origin.longitude,point(east=100.0).let {it.latitude to it.longitude})
        val state=NavigationState(active=true,geometry=route,kilometers=.1)
        assertEquals(90.0,navigationCourse(state,origin,null)!!,.02)
        assertEquals(270.0,navigationCourse(state,origin,270.0)!!,.01)
        assertNull(navigationCourse(state.copy(active=false),origin,null))
        assertEquals(90.0,navigationCourse(state,origin,Double.NaN)!!,.02)
    }
}
