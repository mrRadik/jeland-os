package ru.jeland.os

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.navigation.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class NavigationVoiceInstrumentation {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    @Test fun engineVisibleAndOldModelIsNotPackaged() {
        assertTrue(ruVoiceInstalled(context))
        assertFalse(context.assets.list("voice").orEmpty().contains("supertonic-3-int8"))
        assertTrue(context.assets.open("voice/ruvoice/LICENSE.txt").bufferedReader().use {it.readText()}.contains("NonCommercial"))
    }
    @Test fun realFemaleAndMaleGenerateOfflineAndPlayWithStreetNames()=runBlocking<Unit> {
        lateinit var voice:NavigationVoice
        main {voice=NavigationVoice(context);voice.acceptLicense();voice.setVolume(.6f)}
        try {
            for(speaker in NavigationSpeaker.entries) {
                main {voice.setSpeaker(speaker);voice.test()}
                withTimeout(60000){voice.playing.first {it}}
                assertEquals(speaker.sid,voice.lastSpeaker.value)
                assertEquals("Через сто пятьдесят метров поверните направо на улицу Ленина",voice.lastSpoken.value)
                assertTrue(voice.lastAudioMillis.value in 2000..20000)
                val folder=File(context.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
                File(folder,"speaker-${speaker.sid}.txt").writeText("Speaker=${voice.lastSpeaker.value}; SynthesisMs=${voice.lastSynthesisMillis.value}; AudioMs=${voice.lastAudioMillis.value}; Text=${voice.lastSpoken.value}")
                withTimeout(25000){voice.playing.first {!it}}
                // Same voice/text is cached; replay then interrupt.
                main {voice.test()}
                withTimeout(5000){voice.playing.first {it}}
                assertTrue(voice.lastSynthesisMillis.value<1000)
                main {voice.stop()}
                withTimeout(3000){voice.playing.first {!it}}
            }
            lateinit var restored:NavigationVoice
            main {restored=NavigationVoice(context)}
            try {assertEquals(NavigationSpeaker.MALE,restored.speaker.value)}finally {restored.close()}
        }finally {voice.close()}
    }
    @Test fun cancellationDiscardsNativeResultAndReleaseIsSerialized()=runBlocking<Unit> {
        val entered=CountDownLatch(1);val finish=CountDownLatch(1);val released=CountDownLatch(1)
        val generating=AtomicBoolean();val raced=AtomicBoolean()
        lateinit var voice:NavigationVoice
        main {
            voice=NavigationVoice(context){object:VoiceSynthesizer {
                override fun generate(text:String,speaker:NavigationSpeaker,speed:Float,cancelled:()->Boolean):SynthesizedVoice {
                    generating.set(true);entered.countDown()
                    try {assertTrue(finish.await(10,TimeUnit.SECONDS));return SynthesizedVoice(FloatArray(24000){.1f},24000)}finally {generating.set(false)}
                }
                override fun close(){raced.set(generating.get());released.countDown()}
            }}
            voice.acceptLicense();voice.setVolume(.5f);voice.test()
        }
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            main {voice.stop();voice.close()}
            assertEquals(1L,released.count)
            finish.countDown()
            assertTrue(released.await(5,TimeUnit.SECONDS));delay(300)
            assertFalse(raced.get());assertFalse(voice.playing.value);assertEquals("",voice.lastSpoken.value)
        }finally {finish.countDown();voice.close()}
    }
    @Test fun noSystemFallbackOrInferenceBeforeLicenseAgreement() {
        val called=AtomicBoolean()
        val prefs=context.getSharedPreferences("jelandos_mock_only",0)
        prefs.edit().putBoolean("navigation-supertonic-license-v1",true).putBoolean("navigation-ruvoice-license-v1",false).commit()
        lateinit var voice:NavigationVoice
        main {voice=NavigationVoice(context){called.set(true);error("Must not initialize")};voice.test()}
        try {assertFalse(called.get());assertFalse(voice.playing.value);assertTrue(voice.status.value.contains("Подтвердите"))}finally {voice.close()}
    }
    @Test fun prefetchedThresholdPlaysFromCacheAndPreviewNeverSynthesizes()=runBlocking<Unit> {
        val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
        lateinit var voice:NavigationVoice
        main {voice=NavigationVoice(context){object:VoiceSynthesizer {
            override fun generate(text:String,speaker:NavigationSpeaker,speed:Float,cancelled:()->Boolean):SynthesizedVoice {
                calls.add(text);return SynthesizedVoice(FloatArray(24000){.02f},24000)
            }
            override fun close() {}
        }};voice.acceptLicense();voice.setEnabled(true);voice.setVolume(.5f)
            voice.announce(1,1,800,"Поверните налево", "TURN","LEFT",true)}
        try {
            delay(200);assertTrue(calls.isEmpty())
            main {voice.announce(1,1,800,"Поверните налево","TURN","LEFT",false)}
            withTimeout(5000){while(calls.isEmpty())delay(30)}
            assertFalse(voice.playing.value) // At 800 m city guidance only warms the 500 m cue.
            val prefetched="Через пятьсот метров поверните налево"
            assertEquals(1,calls.count {it==prefetched})
            main {voice.announce(1,1,500,"Поверните налево","TURN","LEFT",false)}
            withTimeout(3000){voice.playing.first {it}}
            assertEquals(prefetched,voice.lastSpoken.value)
            assertEquals(1,calls.count {it==prefetched})
        }finally {voice.close()}
    }
    @Test fun newManeuverDiscardsSlowOldDistance()=runBlocking<Unit> {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        lateinit var voice:NavigationVoice
        main {voice=NavigationVoice(context){object:VoiceSynthesizer {
            override fun generate(text:String,speaker:NavigationSpeaker,speed:Float,cancelled:()->Boolean):SynthesizedVoice {
                if(text.contains("налево")){entered.countDown();release.await(5,TimeUnit.SECONDS)}
                return SynthesizedVoice(FloatArray(24000){.02f},24000)
            }
            override fun close() {}
        }};voice.acceptLicense();voice.setVolume(.5f);voice.setEnabled(true)
            voice.announce(2,3,150,"Поверните налево","TURN","LEFT",false)}
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS))
            main {voice.announce(2,2,40,"Поверните направо","TURN","RIGHT",false)}
            release.countDown()
            withTimeout(5000){voice.playing.first {it}}
            assertTrue(voice.lastSpoken.value.contains("направо"))
            assertFalse(voice.lastSpoken.value.contains("налево"))
        }finally {release.countDown();voice.close()}
    }
}
