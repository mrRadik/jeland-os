package ru.jeland.os.navigation

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NavigationSpeechTest {
    @Test fun selectedSpeakerIdsStayIdenticalToTheListenedDemo() {
        assertEquals(1,NavigationSpeaker.FEMALE.sid)
        assertEquals(7,NavigationSpeaker.MALE.sid)
        assertEquals("xenia-ru",NavigationSpeaker.FEMALE.voiceName)
        assertEquals("aidar-ru",NavigationSpeaker.MALE.voiceName)
        assertEquals(NavigationSpeaker.FEMALE,NavigationSpeaker.fromSid(99))
    }
    @Test fun distancesAreExpandedAndStreetIsPreserved() {
        assertEquals("Через сто пятьдесят метров поверните налево на улицу Ленина",navigationSpeechText(voiceAnnouncement(150,"Поверните налево на улицу Ленина","left").text))
        assertEquals("Через один километр поверните направо",navigationSpeechText(voiceAnnouncement(1000,"Поверните направо","right").text))
        assertEquals("Через тридцать метров поверните налево",navigationSpeechText(voiceAnnouncement(30,"Поверните налево","left").text))
        assertEquals("Вы прибыли к месту назначения",navigationSpeechText("Вы прибыли к месту назначения"))
    }
    @Test fun preparesOnlyNextThreshold() {
        val instruction="Поверните налево на улицу Мира"
        val policy=VoiceAnnouncementPolicy()
        assertNull(policy.next(1,1,2000,instruction,"left",true,false,nowMillis=0))
        assertEquals(voiceAnnouncement(500,instruction,"left"),policy.upcoming(800,instruction,"left"))
        policy.next(1,1,500,instruction,"left",true,false,nowMillis=1000)
        assertEquals(voiceAnnouncement(150,instruction,"left"),policy.upcoming(400,instruction,"left"))
        policy.next(1,1,150,instruction,"left",true,false,nowMillis=20000)
        assertEquals(voiceAnnouncement(0,instruction,"left"),policy.upcoming(40,instruction,"left"))
        policy.next(1,1,20,instruction,"left",true,false,nowMillis=30000)
        assertNull(policy.upcoming(0,instruction,"left"))
        assertNull(policy.upcoming(500,"","left"))
        assertNull(policy.upcoming(500,"Вы прибыли","arrive"))
    }
    @Test fun fiftyNoLongerNeedsSupertonicStressHack() {
        for(hundreds in 0..9) {
            val text=navigationSpeechText(voiceAnnouncement(hundreds*100+50,"Поверните направо","right").text)
            assertTrue(text,text.contains("пятьдесят метров"))
            assertEquals(0,text.count {it=='\u0301'})
        }
    }
    @Test fun normalizationIsIdempotentAndDoesNotGuessOtherWords() {
        val text="Через сто пятьдесят метров поверните направо"
        assertEquals(text,navigationSpeechText(text))
        assertEquals("Улица Пятьдесятилетия",navigationSpeechText("Улица Пятьдесятилетия"))
        assertEquals("Через шестьдесят метров поверните налево",navigationSpeechText("Через 60 метров поверните налево"))
        assertEquals("ПЯТЬДЕСЯТ",navigationSpeechText("ПЯТЬДЕСЯТ"))
    }
    @Test fun pronunciationDoesNotChangeDisplayedRouteInstruction() {
        val instruction=voiceAnnouncement(150,"Поверните направо","right")
        navigationSpeechText(instruction.text)
        assertEquals("Через 150 метров поверните направо",instruction.text)
    }
    @Test fun distanceSpeechDoesNotContainDigitsAcrossAllThresholds() {
        for(meters in 30..1000)assertFalse(navigationSpeechText(voiceAnnouncement(meters,"Поверните направо","right").text).any {it.isDigit()})
    }
    @Test fun mapTextCannotInjectExpressionTags() {
        assertEquals("Поверните налево",navigationSpeechText("<laugh>Поверните\nналево"))
    }
    @Test fun waveConversionClampsSamplesAndUsesActualRate() {
        val wave=voiceWave(SynthesizedVoice(floatArrayOf(-2f,0f,2f,Float.NaN),24000))
        val buffer=ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(52,wave.size);assertEquals(24000,buffer.getInt(24));assertEquals(8,buffer.getInt(40))
        assertEquals(-32767,buffer.getShort(44).toInt());assertEquals(32767,buffer.getShort(48).toInt());assertEquals(0,buffer.getShort(50).toInt())
        assertTrue(runCatching {voiceWave(SynthesizedVoice(floatArrayOf(),24000))}.isFailure)
    }
    @Test fun decodesRiffChunksAndRejectsUnsupportedOrTruncatedAudio() {
        val wave=voiceWave(SynthesizedVoice(floatArrayOf(.1f,.2f),48000))
        val extra=wave.copyOfRange(0,36)+byteArrayOf(74,85,78,75,2,0,0,0,0,0)+wave.copyOfRange(36,wave.size)
        assertEquals(2,readVoiceWave(extra).samples.size)
        assertEquals(48000,readVoiceWave(extra).sampleRate)
        assertTrue(runCatching {readVoiceWave(wave.copyOf(wave.size-1))}.isFailure)
        val stereo=wave.clone().apply {this[22]=2}
        assertTrue(runCatching {readVoiceWave(stereo)}.isFailure)
        val oversized=wave.clone().apply {for(i in 40..43)this[i]=127}
        assertTrue(runCatching {readVoiceWave(oversized)}.isFailure)
    }
}
