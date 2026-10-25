package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class VehicleNoticesTest {
    private val door=VehicleOpening.FRONT_LEFT
    private val hood=VehicleOpening.HOOD
    @Test fun closedVehicleNeverShowsAnything() {
        val reducer=VehicleNoticeReducer()
        assertFalse(reducer.update(emptySet(),false,0).visible)
        assertFalse(reducer.update(emptySet(),true,5000).visible)
    }
    @Test fun parkedNoticeExpiresAtExactlyFourSecondsAndDoesNotReplay() {
        val reducer=VehicleNoticeReducer()
        assertTrue(reducer.update(setOf(door),false,100).visible)
        assertTrue(reducer.update(setOf(door),false,4099).visible)
        assertFalse(reducer.update(setOf(door),false,4100).visible)
        assertFalse(reducer.update(setOf(door),false,10000).visible)
    }
    @Test fun closingRemovesOnlyTheCorrespondingNotice() {
        val reducer=VehicleNoticeReducer()
        reducer.update(setOf(door,hood),false,0)
        assertEquals(listOf(hood),reducer.update(setOf(hood),false,1000).parked.map {it.opening})
        assertFalse(reducer.update(emptySet(),false,1001).visible)
    }
    @Test fun newOpeningDoesNotExtendOtherDeadlines() {
        val reducer=VehicleNoticeReducer()
        reducer.update(setOf(door),false,0)
        val both=reducer.update(setOf(door,hood),false,3000)
        assertEquals(listOf(4000L,7000L),both.parked.map {it.expiresAtMillis})
        assertEquals(listOf(hood),reducer.update(setOf(door,hood),false,4000).parked.map {it.opening})
        assertFalse(reducer.update(setOf(door,hood),false,7000).visible)
    }
    @Test fun reopeningStartsANewFourSecondWindow() {
        val reducer=VehicleNoticeReducer()
        reducer.update(setOf(door),false,0);reducer.update(emptySet(),false,1000)
        assertEquals(5500L,reducer.update(setOf(door),false,1500).parked.single().expiresAtMillis)
    }
    @Test fun startingToMovePromotesEvenAnExpiredNoticeToPersistentWarning() {
        val reducer=VehicleNoticeReducer()
        reducer.update(setOf(door,hood),false,0)
        assertFalse(reducer.update(setOf(door,hood),false,4000).visible)
        val moving=reducer.update(setOf(door,hood),true,5000)
        assertTrue(moving.persistent);assertTrue(moving.parked.isEmpty())
        assertEquals(setOf(door,hood),moving.warningOpenings)
        assertTrue(reducer.update(setOf(door,hood),false,100000).persistent)
        assertEquals(setOf(hood),reducer.update(setOf(hood),false,100001).warningOpenings)
    }
    @Test fun closingEverythingResetsThePersistentWarning() {
        val reducer=VehicleNoticeReducer()
        reducer.update(setOf(door),true,0)
        assertFalse(reducer.update(emptySet(),false,100).visible)
        val next=reducer.update(setOf(hood),false,200)
        assertFalse(next.persistent);assertEquals(hood,next.parked.single().opening)
    }
    @Test fun allSixOpeningsAreIndependentWhileMoving() {
        val reducer=VehicleNoticeReducer();var opened=VehicleOpening.entries.toSet()
        assertEquals(6,reducer.update(opened,true,0).warningOpenings.size)
        VehicleOpening.entries.forEachIndexed {index,opening->
            opened=opened-opening
            val state=reducer.update(opened,true,index+1L)
            assertEquals(opened,state.warningOpenings)
            assertEquals(opened.isNotEmpty(),state.visible)
        }
    }
}
