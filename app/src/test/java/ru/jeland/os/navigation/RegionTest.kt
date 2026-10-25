package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test

class RegionTest {
    @Test fun oldRegionsDoNotClaimHouseNumbersAndNewRegionsCarryCoverage() {
        val legacy=RegionData("old","Old map",0,"OSM")
        assertEquals(0,legacy.houseNumberCount)
        assertEquals(100,legacy.copy(houseNumberCount=100).houseNumberCount)
    }
    @Test fun coordinatesAreFiniteAndInRange() {
        assertEquals(GeoPoint(55.16,61.4),parseCoordinates("55.16, 61.4"))
        listOf("NaN,1","Infinity,0","91,0","0,181","55,61,3","place","").forEach {assertNull(it,parseCoordinates(it))}
    }
    @Test fun boundedRegionDoesNotClaimWholeOblast() {
        val region=RegionData("test","Zone",0,"OSM",GeoPoint(55.16,61.4),listOf(59.4,54.65,62.2,55.8))
        assertTrue(region.contains(GeoPoint(55.16,61.4)))
        assertFalse(region.contains(GeoPoint(53.4,59.0)))
    }
}
