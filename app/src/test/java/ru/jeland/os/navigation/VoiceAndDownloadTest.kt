package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test

class VoiceAndDownloadTest {
    @Test fun bundledVoiceActuallyIncludes150MetersNotJustAhead() {
        val value=VoiceAnnouncementPolicy().next(1,5,150,"Поверните на лево","left",true,false)!!
        assertEquals("Через 150 метров поверните налево",value.text)
        assertEquals(150,value.distanceMeters)
        assertFalse(value.text.contains("Впереди",true))
    }
    @Test fun allBundledDistancesMatchMapPrecision() {
        for(meters in 30..1000) {
            val value=voiceAnnouncement(meters,"Поверните направо","right")
            val rounded=(kotlin.math.round(meters/10.0)*10).toInt()
            assertEquals(rounded,value.distanceMeters)
            assertTrue(value.text.startsWith("Через "))
        }
        assertEquals("Через 1 километр поверните направо",voiceAnnouncement(1000,"Поверните направо","right").text)
    }
    @Test fun closeTurnStillSaysDistanceThenSpeaksImmediateAction() {
        val policy=VoiceAnnouncementPolicy()
        assertEquals("Через 40 метров поверните налево",policy.next(1,1,40,"Поверните налево","left",true,false,nowMillis=0)!!.text)
        assertNull(policy.next(1,1,35,"Поверните налево","left",true,false,nowMillis=1000))
        assertEquals("Поверните налево",policy.next(1,1,20,"Поверните налево","left",true,false,nowMillis=4000)!!.text)
        assertNull(policy.next(1,1,10,"Поверните налево","left",true,false,nowMillis=5000))
    }
    @Test fun arrivalNeverAnnouncesDistanceAfterArrival() {
        val value=voiceAnnouncement(0,"Вы прибыли к месту назначения","arrive")
        assertEquals("Вы прибыли к месту назначения",value.text)
        assertNull(value.distanceMeters)
    }
    @Test fun ttsKeepsStreetButBuiltInVoiceDoesNotPretendToReadIt() {
        val value=voiceAnnouncement(150,"Поверните налево на улицу Ленина","left")
        assertEquals("Через 150 метров поверните налево на улицу Ленина",value.text)
        assertEquals("Через 150 метров поверните налево",voiceAnnouncement(150,"Через 150 м поверните налево","left").text)
        assertEquals("Через 150 метров поверните налево",voiceAnnouncement(150,"Через 150 метров поверните налево","left").text)
    }
    @Test fun voiceDoesNotRepeatOnEveryFix() {
        val p=VoiceAnnouncementPolicy()
        assertNotNull(p.next(1,5,490,"Поверните налево","left",true,false,nowMillis=0))
        assertNull(p.next(1,5,480,"Поверните налево","left",true,false,nowMillis=1000))
        assertNotNull(p.next(1,5,140,"Поверните налево","left",true,false,nowMillis=14000))
        assertNull(p.next(1,5,450,"Поверните налево","left",true,false,nowMillis=15000))
        assertNull(p.next(1,5,30,"Поверните налево","left",true,false,nowMillis=17000))
    }
    @Test fun newStepAndNewRouteCanSpeakSameTurn() {
        val p=VoiceAnnouncementPolicy()
        assertNotNull(p.next(1,5,30,"Поверните направо","right",true,false,nowMillis=0))
        assertNull(p.next(1,4,30,"Поверните направо","right",true,false,nowMillis=1000))
        assertNotNull(p.next(1,4,30,"Поверните направо","right",true,false,nowMillis=13000))
        assertNotNull(p.next(2,4,30,"Поверните направо","right",true,false,nowMillis=14000))
    }
    @Test fun noAutomaticVoiceInPreviewOrWhenMuted() {
        val p=VoiceAnnouncementPolicy()
        assertNull(p.next(1,1,100,"Поверните налево","left",true,true))
        assertNull(p.next(1,1,100,"Поверните налево","left",false,false))
        assertNotNull(p.next(1,1,100,"Поверните налево","left",true,false))
    }
    @Test fun distantManeuverDoesNotSayAheadForTwentyKilometers() {
        assertNull(VoiceAnnouncementPolicy().next(1,1,20000,"Поверните налево","left",true,false))
    }
    @Test fun resetAllowsFreshGuidance() {
        val p=VoiceAnnouncementPolicy();p.next(1,1,100,"Поверните налево","left",true,false)
        p.reset();assertNotNull(p.next(1,1,100,"Поверните налево","left",true,false))
    }
}
