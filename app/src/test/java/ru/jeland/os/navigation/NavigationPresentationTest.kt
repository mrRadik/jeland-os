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
    @Test fun invalidCoursePreservesPreviousDirection() {
        assertEquals(90.0,navigationBearing(Double.NaN,90.0),0.0)
        assertEquals(359.0,navigationBearing(-1.0),0.0)
    }
}
