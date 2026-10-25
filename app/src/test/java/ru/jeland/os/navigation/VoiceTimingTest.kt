package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test

class VoiceTimingTest {
    private fun cue(p:VoiceAnnouncementPolicy,meters:Int,time:Long,speed:Double?=null,step:Int=5,text:String="Поверните направо")=
        p.next(1,step,meters,text,"right",true,false,speed,time)
    @Test fun cityHasAtMostThreeStagesAndNoExtraFiftyMeterReminder() {
        val p=VoiceAnnouncementPolicy();val cues=mutableListOf<VoiceAnnouncement>()
        for((seconds,meters) in listOf(0 to 1300,10 to 800,20 to 501,21 to 500,22 to 490,30 to 210,40 to 150,41 to 140,44 to 50,45 to 20,46 to 10))
            cue(p,meters,seconds*1000L)?.let(cues::add)
        assertEquals(listOf(500,150,null),cues.map {it.distanceMeters})
    }
    @Test fun speedTriggersTheSameCachedDistanceBeforeReachingIt() {
        val p=VoiceAnnouncementPolicy()
        assertNull(cue(p,560,0,20.0))
        assertEquals(voiceAnnouncement(500,"Поверните направо","right"),p.upcoming(560,"Поверните направо","right",20.0))
        assertEquals(500,cue(p,540,1000,20.0)!!.distanceMeters) // 45 m lead, not a late new 490 m synthesis.
        assertNull(cue(p,500,3000,20.0))
    }
    @Test fun highwayUsesLongerPreparationAndApproachButStillThreePhases() {
        val p=VoiceAnnouncementPolicy()
        assertNull(cue(p,1200,0,30.0))
        assertEquals(1000,cue(p,1060,1000,30.0)!!.distanceMeters)
        assertEquals(300,cue(p,360,25000,30.0)!!.distanceMeters)
        assertNull(cue(p,250,26000,30.0))
        assertNull(cue(p,100,30000,30.0)!!.distanceMeters)
    }
    @Test fun crossingAThresholdWithANormalGpsStrideStillUsesThePreparedPhrase() {
        val p=VoiceAnnouncementPolicy()
        assertNull(cue(p,1100,0,30.0))
        assertEquals(1000,cue(p,1020,2000,30.0)!!.distanceMeters)
    }
    @Test fun normalCooldownCrossesStepBoundariesButUrgentCueHasShorterGap() {
        val p=VoiceAnnouncementPolicy()
        assertNotNull(cue(p,500,0))
        assertNull(cue(p,150,5000))
        assertNotNull(cue(p,150,12000))
        assertNull(cue(p,20,14000,step=4))
        assertNotNull(cue(p,20,15000,step=4))
    }
    @Test fun visualTextChangesAndGpsOscillationDoNotResetSpokenStage() {
        val p=VoiceAnnouncementPolicy()
        assertNotNull(cue(p,500,0,text="Поверните направо"))
        assertNull(cue(p,490,15000,text="Поверните направо на улицу Мира"))
        assertNull(cue(p,510,20000,text="Поверните направо."))
        assertNull(cue(p,480,25000))
    }
    @Test fun leadUsesBoundedLatencyAndFixAgeAndRejectsInvalidSpeeds() {
        val p=VoiceAnnouncementPolicy()
        assertEquals(45.0,p.leadMeters(20.0),.001)
        assertEquals(90.0,p.leadMeters(20.0,500,2000),.001)
        for(speed in listOf(null,Double.NaN,Double.POSITIVE_INFINITY,-1.0,1000.0))assertEquals(0.0,p.leadMeters(speed),0.0)
        assertEquals(150.0,p.leadMeters(70.0,99999,99999),0.0)
    }
    @Test fun lateFirstAttachmentDoesNotInventAnOldFiveHundredMeterDistance() {
        assertEquals(300,cue(VoiceAnnouncementPolicy(),300,0)!!.distanceMeters)
    }
    @Test fun synthesisThatNeverPlayedDoesNotSilenceTheNextRealManeuver() {
        val p=VoiceAnnouncementPolicy()
        assertNotNull(p.next(1,5,150,"Налево","left",true,false,nowMillis=0,recordPlayback=false))
        assertNotNull(p.next(1,4,40,"Направо","right",true,false,nowMillis=100,recordPlayback=false))
        p.playbackStarted(100)
        assertNull(p.next(1,3,150,"Налево","left",true,false,nowMillis=1000))
    }
    @Test fun arrivalIsUniqueEvenInsideNormalCooldown() {
        val p=VoiceAnnouncementPolicy();cue(p,150,0)
        assertEquals("Вы прибыли",p.next(1,0,0,"Вы прибыли","arrive",true,false,nowMillis=1000)!!.text)
        assertNull(p.next(1,0,0,"Вы прибыли","arrive",true,false,nowMillis=2000))
    }
}
