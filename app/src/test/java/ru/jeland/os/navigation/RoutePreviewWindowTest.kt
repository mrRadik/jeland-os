package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test

class RoutePreviewWindowTest {
    private val longRoute=(0..1000).map {55.0+it*.001 to 61.0}
    @Test fun longTripShowsLessThanOneKilometerNotTheWholeRoute() {
        val window=routePreviewWindow(longRoute,GeoPoint(55.0,61.0),20000,480,240)!!
        assertTrue(window.points.last().latitude<55.01)
        assertEquals(850.0,window.aheadMeters,0.0)
        assertTrue(window.zoom>12)
    }
    @Test fun windowMovesToTheCurrentSegmentInsteadOfRouteStart() {
        val window=routePreviewWindow(longRoute,GeoPoint(55.7,61.0),100,480,240)!!
        assertEquals(55.7,window.points.first().latitude,0.000001)
        assertTrue(window.points.last().latitude<55.705)
        assertEquals(320.0,window.aheadMeters,0.0)
    }
    @Test fun projectionStartsBetweenVertices() {
        val window=routePreviewWindow(listOf(55.0 to 61.0,55.01 to 61.0),GeoPoint(55.005,61.0),100,480,240)!!
        assertEquals(55.005,window.points.first().latitude,0.000001)
    }
    @Test fun emptyGeometryDoesNotInventRoute(){assertNull(routePreviewWindow(emptyList(),null,100,480,240))}
    @Test fun rotatedRouteFitsWidgetHeightRatherThanOldNorthUpWidth() {
        val east=listOf(55.0 to 61.0,55.0 to 61.1)
        val northUp=routePreviewWindow(east,GeoPoint(55.0,61.0),800,480,240,0.0)!!
        val courseUp=routePreviewWindow(east,GeoPoint(55.0,61.0),800,480,240,90.0)!!
        assertTrue(courseUp.zoom<northUp.zoom)
        assertEquals(northUp.center.latitude,courseUp.center.latitude,.000001)
        assertEquals(northUp.center.longitude,courseUp.center.longitude,.000001)
        for(angle in listOf(0.0,45.0,90.0,180.0,270.0,359.0)) {
            val window=routePreviewWindow(east,GeoPoint(55.0,61.0),800,480,240,angle)!!
            assertTrue(window.zoom.isFinite());assertTrue(window.center.latitude.isFinite())
            assertEquals(northUp.points,window.points)
        }
    }
    @Test fun repeatedCoordinatesRemainFinite() {
        val window=routePreviewWindow(listOf(55.0 to 61.0,55.0 to 61.0),null,0,480,240)!!
        assertTrue(window.zoom.isFinite());assertEquals(2,window.points.size)
    }
    @Test fun courtyardAndNeighborCannotSupplyBuildingAddress() {
        val outer=listOf(GeoPoint(0.0,0.0),GeoPoint(0.0,4.0),GeoPoint(4.0,4.0),GeoPoint(4.0,0.0))
        val courtyard=listOf(GeoPoint(1.0,1.0),GeoPoint(1.0,2.0),GeoPoint(2.0,2.0),GeoPoint(2.0,1.0))
        assertTrue(insideFootprint(GeoPoint(.5,.5),listOf(outer,courtyard)))
        assertFalse(insideFootprint(GeoPoint(1.5,1.5),listOf(outer,courtyard)))
        assertFalse(insideFootprint(GeoPoint(5.0,5.0),listOf(outer,courtyard)))
    }
}
