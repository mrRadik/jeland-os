package ru.jeland.os.navigation.comaps

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.navigation.GeoPoint

class CoMapsDestinationRecoveryTest {
    private val target=GeoPoint(55.3,61.5)
    private val origin=GeoPoint(55.15,61.4)
    private fun route(lat:Double,end:GeoPoint)=listOf(origin,GeoPoint(lat,61.49),GeoPoint(lat,61.51),end)
    @Test fun onlyDestinationFailuresStartRecovery() {
        for(code in 0..16)assertEquals(code in setOf(6,8),CoMapsDestinationRecovery.eligible(code,false,false,origin,target))
        for(code in listOf(6,8)) {
            assertFalse(CoMapsDestinationRecovery.eligible(code,true,false,origin,target))
            assertFalse(CoMapsDestinationRecovery.eligible(code,false,true,origin,target))
            assertFalse(CoMapsDestinationRecovery.eligible(code,false,false,null,target))
            assertFalse(CoMapsDestinationRecovery.eligible(code,false,false,target,target))
            assertFalse(CoMapsDestinationRecovery.eligible(code,false,false,origin,GeoPoint(Double.NaN,0.0)))
        }
    }
    @Test fun noRoadSearchIsBoundedAndCanBeCancelled() {
        val search=CoMapsDestinationRecovery(target,origin)
        var step=search.start();var builds=0
        while(step is CoMapsDestinationRecovery.Step.Build) {builds++;assertTrue(CoMapsDestinationRecovery.distance(target,step.point)<3100);step=search.failed()}
        assertEquals(32,builds);assertEquals(CoMapsDestinationRecovery.Step.Exhausted,step)
        val cancelled=CoMapsDestinationRecovery(target,origin);cancelled.start();cancelled.cancel()
        assertEquals(CoMapsDestinationRecovery.Step.Exhausted,cancelled.failed())
        assertEquals(CoMapsDestinationRecovery.Step.Exhausted,cancelled.built(route(55.299,target)))
    }
    @Test fun offroadConnectorIsExcludedAndTheBestRoadPointIsNativelyVerified() {
        val search=CoMapsDestinationRecovery(target,origin)
        val first=search.start() as CoMapsDestinationRecovery.Step.Build
        var next=search.built(route(55.29,first.point))
        repeat(31){next=search.failed()}
        val verify=next as CoMapsDestinationRecovery.Step.Build
        assertEquals(55.29,verify.point.latitude,1e-8);assertEquals(61.5,verify.point.longitude,1e-8)
        assertNotEquals(first.point,verify.point)
        val accepted=search.built(listOf(origin,verify.point)) as CoMapsDestinationRecovery.Step.Accepted
        assertEquals(target,accepted.approach.requested)
        assertEquals(verify.point,accepted.approach.point)
        assertEquals(1111.95,accepted.approach.distanceMeters,1.0)
    }
    @Test fun closerRoadWinsRatherThanFirstSuccessfulProbe() {
        val search=CoMapsDestinationRecovery(target,origin)
        val first=search.start() as CoMapsDestinationRecovery.Step.Build
        val second=search.built(route(55.29,first.point)) as CoMapsDestinationRecovery.Step.Build
        var next=search.built(route(55.298,second.point))
        repeat(30){next=search.failed()}
        val verify=next as CoMapsDestinationRecovery.Step.Build
        assertEquals(55.298,verify.point.latitude,1e-8)
        // A failed final verification tries the other independently confirmed road.
        val other=search.failed() as CoMapsDestinationRecovery.Step.Build
        assertEquals(55.29,other.point.latitude,1e-8)
        assertEquals(CoMapsDestinationRecovery.Step.Exhausted,search.built(listOf(origin,target)))
    }
    @Test fun invalidOrEmptyGeometryNeverCreatesAnApproach() {
        assertNull(CoMapsDestinationRecovery.closestRoadPoint(target,emptyList()))
        assertNull(CoMapsDestinationRecovery.closestRoadPoint(target,listOf(origin,target)))
        assertNull(CoMapsDestinationRecovery.closestRoadPoint(target,listOf(origin,GeoPoint(Double.NaN,0.0),target)))
        val near=CoMapsDestinationRecovery.closestRoadPoint(target,route(55.29,GeoPoint(55.29999,61.5)))!!
        assertEquals(55.29,near.latitude,1e-8)
    }
}
