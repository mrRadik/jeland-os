package ru.jeland.os.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.jeland.os.navigation.comaps.coMapsMeters

class CoMapsDistanceTest {
    @Test fun metricAndImperialDistancesReachGuidanceInMeters() {
        assertEquals(150.0,coMapsMeters(150.0,0),.001)
        assertEquals(1500.0,coMapsMeters(1.5,1),.001)
        assertEquals(30.48,coMapsMeters(100.0,2),.001)
        assertEquals(1609.344,coMapsMeters(1.0,3),.001)
    }
    @Test fun invalidNativeDistancesNeverBecomeHugeOrNegativeCues() {
        listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0).forEach {assertEquals(0.0,coMapsMeters(it,0),0.0)}
        assertEquals(0.0,coMapsMeters(500.0,99),0.0)
    }
}
