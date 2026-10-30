package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.navigation.comaps.CoMapsGuidanceSession
import ru.jeland.os.navigation.comaps.coMapsViewport
import ru.jeland.os.navigation.comaps.CoMapsDemoRoute
import ru.jeland.os.navigation.comaps.CoMapsManeuverIdentity

class NavigationJourneyTest {
    @Test fun correctedStreetLabelIsNotANewManeuverButTheNextCloseTurnIs() {
        val identity=CoMapsManeuverIdentity()
        assertEquals(1,identity.update("TURN_RIGHT","улица Мира",500))
        assertEquals(1,identity.update("TURN_RIGHT","Мира",150))
        assertEquals(1,identity.update("TURN_RIGHT","Мира",5))
        assertEquals(2,identity.update("TURN_RIGHT","Ленина",45))
    }
    @Test fun demoPlaybackKeepsNativeLongitudeLatitudeOrderAndStopsAtDestination() {
        val route=CoMapsDemoRoute(listOf(61.4 to 55.16,61.4 to 55.161,61.401 to 55.161))
        assertTrue(route.length>100)
        assertEquals(GeoPoint(55.16,61.4),route.sample(0.0)!!.first)
        assertEquals(GeoPoint(55.161,61.401),route.sample(route.length+100)!!.first)
        assertEquals(0.0,route.sample(0.0)!!.second,.1)
    }
    @Test fun aNativeRerouteDoesNotStartANewGuidanceCamera() {
        val session=CoMapsGuidanceSession()
        assertEquals(NavigationJourneyEvent.STARTED,session.built(10,true))
        assertNull(session.built(10,true))
        assertEquals(NavigationJourneyEvent.REBUILT,session.built(11,true))
        assertNull(session.built(11,true))
        assertTrue(session.started)
    }
    @Test fun completedRebuildCanKeepTheSameGeometryAndStillAnnouncesOnlyOnce() {
        val session=CoMapsGuidanceSession();session.built(10,true)
        session.update("RouteNeedsRebuild");session.update("RouteRebuilding")
        assertEquals(NavigationJourneyEvent.REBUILT,session.built(10,true))
        assertNull(session.built(10,true))
    }
    @Test fun previewAndUpcomingDestinationDoNotAnnounceArrival() {
        val session=CoMapsGuidanceSession()
        assertNull(session.built(10,false));assertNull(session.update("RouteFinished"))
        assertEquals(NavigationJourneyEvent.STARTED,session.built(10,true))
        assertNull(session.update("OnRoute"))
        assertEquals(NavigationJourneyEvent.ARRIVED,session.update("RouteFinished"))
        assertNull(session.update("RouteFinished"));assertNull(session.stop())
    }
    @Test fun manualEndIsDifferentFromArrivalAndANewTripCanStart() {
        val session=CoMapsGuidanceSession();session.built(1,true)
        assertEquals(NavigationJourneyEvent.STOPPED,session.stop())
        assertNull(session.stop())
        assertEquals(NavigationJourneyEvent.STARTED,session.built(1,true))
    }
    @Test fun overlaySpaceIsExcludedAndVehicleHasLookAheadRoomOnDifferentScreens() {
        for((width,height) in listOf(1080 to 1920,1920 to 1080,800 to 320)) {
            val viewport=coMapsViewport(width,height,100,110,true)!!
            assertTrue(viewport.top>=100.coerceAtMost(height/3))
            assertTrue(viewport.bottom<=height-110.coerceAtMost(height/3))
            val anchor=viewport.bottom-viewport.followOffset
            assertTrue(anchor<viewport.bottom)
            assertTrue(anchor>viewport.top+(viewport.bottom-viewport.top)*.65)
        }
        assertNull(coMapsViewport(0,0,0,0,true))
        val cramped=coMapsViewport(300,300,10000,10000,true)!!
        assertTrue(cramped.bottom>cramped.top)
    }
    @Test fun streetIsSpokenAtTheFirstWarningOnlyAndResetsForTheNextManeuver() {
        val policy=VoiceAnnouncementPolicy()
        fun next(meters:Int,time:Long,step:Int=1,street:String="улица Мира")=
            policy.next(1,step,meters,"Поверните направо · $street","right",true,false,nowMillis=time,street=street)!!
        assertEquals("Через 500 метров поверните направо, улица Мира",next(500,0).text)
        assertEquals("Через 150 метров поверните направо",next(150,13000).text)
        assertEquals("Поверните направо",next(20,17000).text)
        assertEquals("Через 500 метров поверните направо, улица Ленина",next(500,30000,2,"улица Ленина").text)
    }
    @Test fun failedSynthesisDoesNotConsumeTheOnlyStreetAnnouncement() {
        val policy=VoiceAnnouncementPolicy()
        val first=policy.next(1,1,500,"Поверните налево","left",true,false,nowMillis=0,recordPlayback=false,street="улица Мира")!!
        assertTrue(first.includesStreet)
        val retry=policy.next(1,1,150,"Поверните налево","left",true,false,nowMillis=15000,recordPlayback=false,street="улица Мира")!!
        assertTrue(retry.includesStreet)
        policy.playbackStarted(15000,retry.includesStreet)
        assertFalse(policy.upcoming(100,"Поверните налево","left",street="улица Мира")!!.includesStreet)
    }
}
