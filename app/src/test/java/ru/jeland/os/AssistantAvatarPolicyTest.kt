package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class AssistantAvatarPolicyTest {
    @Test fun hidingRequiresForegroundAndAWorkingStatusSubscription() {
        val policy=AssistantAvatarPolicy()
        assertTrue(policy.shouldShow(false,false));assertTrue(policy.shouldShow(true,false))
        assertFalse(policy.shouldShow(true,true))
        assertTrue(policy.shouldShow(false,true))
    }
    @Test fun allActiveAsrStatesShowAndInactiveHides() {
        val policy=AssistantAvatarPolicy()
        for(status in listOf(1000,1001,1002)){policy.asr(status);assertTrue(policy.shouldShow(true,true))}
        policy.asr(1003);assertFalse(policy.shouldShow(true,true))
    }
    @Test fun spokenResponseStaysVisibleAfterAsrBecomesInactive() {
        val policy=AssistantAvatarPolicy();policy.asr(1000);policy.tts("ttsPlayBegin",0);policy.asr(1003)
        assertTrue(policy.shouldShow(true,true))
        policy.tts("onProgressReturn",99);assertTrue(policy.shouldShow(true,true))
        policy.tts("ttsPlayComplete",0);assertFalse(policy.shouldShow(true,true))
    }
    @Test fun ttsEndDoesNotHideWhileListeningAndErrorOrInterruptedEndsSpeech() {
        for(end in listOf("ttsPlayComplete","ttsPlayError","ttsPlayInterrupted")) {
            val policy=AssistantAvatarPolicy();policy.asr(1001);policy.tts("ttsPlayBegin",0);policy.tts(end,0)
            assertTrue(policy.shouldShow(true,true));policy.asr(1003);assertFalse(policy.shouldShow(true,true))
        }
    }
    @Test fun malformedStatesFailOpenIndependentlyUntilMatchingKnownState() {
        val policy=AssistantAvatarPolicy();policy.asr(null);assertTrue(policy.shouldShow(true,true))
        policy.tts("ttsPlayComplete",0);assertTrue(policy.shouldShow(true,true))
        policy.asr(1003);assertFalse(policy.shouldShow(true,true))
        policy.tts(null,null);policy.asr(1003);assertTrue(policy.shouldShow(true,true))
        policy.tts("ttsIsPlaying",0);assertFalse(policy.shouldShow(true,true))
        policy.tts("ttsIsPlaying",5);assertTrue(policy.shouldShow(true,true))
    }
    @Test fun unsupportedHazardRequestsNeverEnterProductionActuatorAllowList() {
        for(raw in 0..255)assertFalse(VerifiedVehicleProtocol.allowed(VdsWrite(VdsKey(0x50008,24),raw,VdsKey(0x50004,79))))
        assertTrue(79 in VerifiedVehicleProtocol.subscriptions.getValue(VerifiedVehicleProtocol.INFO))
    }
}
