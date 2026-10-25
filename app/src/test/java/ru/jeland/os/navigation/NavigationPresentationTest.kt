package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationPresentationTest {
    @Test fun distancesHaveReadableMetricUnits() {
        assertEquals("800 м",distanceLabel(800.0))
        assertEquals("1,5 км",distanceLabel(1500.0))
        assertEquals("—",distanceLabel(Double.NaN))
        assertEquals("—",distanceLabel(-1.0))
    }
    @Test fun navigationCameraKeepsOrientationAcrossTabs() {
        val camera=NavigationCamera(GeoPoint(55.16,61.40),15.5,90.0,40.0)
        assertEquals(90.0,camera.bearing,0.0)
        assertEquals(40.0,camera.tilt,0.0)
    }
    @Test fun courseUpUsesSameAngleForMapAndVehicleWithoutDoubleRotation() {
        for(heading in listOf(0.0,45.0,90.0,180.0,270.0,359.0)) {
            val camera=navigationBearing(heading)
            assertEquals(heading,camera,0.0)
            assertEquals(0f,navigationMarkerRotation(heading,camera))
        }
        assertEquals(90f,navigationMarkerRotation(90.0,0.0))
        assertEquals(2f,navigationMarkerRotation(1.0,359.0))
    }
    @Test fun invalidCourseKeepsLastDirectionAndWrapsAtNorth() {
        assertEquals(270.0,navigationBearing(null,270.0),0.0)
        assertEquals(270.0,navigationBearing(Double.NaN,270.0),0.0)
        assertEquals(0.0,navigationBearing(null,Double.NaN),0.0)
        assertEquals(1.0,navigationBearing(361.0),0.0)
        assertEquals(359.0,navigationBearing(-1.0),0.0)
    }
    @Test fun guidanceStartZoomsFromOverviewEvenAtTheSamePosition() {
        val point=GeoPoint(55.16,61.4);val old=NavigationCamera(point,5.0,90.0)
        val guiding=navigationFollowCamera(point,old,true,null)
        assertEquals(point,guiding.target);assertEquals(16.5,guiding.zoom,0.0)
        assertEquals(90.0,guiding.bearing,0.0);assertEquals(35.0,guiding.tilt,0.0)
        assertEquals(14.5,navigationFollowCamera(point,old,false,null).zoom,0.0)
        assertEquals(18.0,navigationFollowCamera(point,old.copy(zoom=18.0),true,null).zoom,0.0)
    }
}
