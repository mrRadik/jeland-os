package ru.jeland.os.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal const val RUVOICE_PACKAGE="ru.kost.ruvoice"

internal fun ruVoiceInstalled(context:Context):Boolean=runCatching {
    val info=context.packageManager.getApplicationInfo(RUVOICE_PACKAGE,0)
    info.enabled&&context.packageManager.queryIntentServices(
        Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(RUVOICE_PACKAGE),0).isNotEmpty()
}.getOrDefault(false)

/** One dedicated offline engine, not the user's default TTS. All waits are on our worker. */
internal class RuVoiceSynthesizer(private val context:Context):VoiceSynthesizer {
    private var engine:TextToSpeech?=null
    private val main=Handler(Looper.getMainLooper())

    private fun await(latch:CountDownLatch,cancelled:()->Boolean,timeout:Long=30000) {
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(!latch.await(50,TimeUnit.MILLISECONDS)) {
            if(cancelled())throw CancellationException("Voice request cancelled")
            check(SystemClock.elapsedRealtime()<deadline){"RuVoice response timed out"}
        }
        if(cancelled())throw CancellationException("Voice request cancelled")
    }

    private fun connected(cancelled:()->Boolean):TextToSpeech {
        engine?.let {return it}
        check(ruVoiceInstalled(context)){"Установите RuVoice из комплекта Jeland OS"}
        val created=CountDownLatch(1);val initialized=CountDownLatch(1)
        val status=AtomicInteger(TextToSpeech.ERROR)
        val instance=AtomicReference<TextToSpeech>()
        val failure=AtomicReference<Throwable>()
        // TextToSpeech binds asynchronously; never block the main looper waiting for OnInit.
        main.post {
            if(!cancelled())try {
                instance.set(TextToSpeech(context,{code->status.set(code);initialized.countDown()},RUVOICE_PACKAGE))
            }catch(e:Exception){failure.set(e)}
            created.countDown()
        }
        try {
            await(created,cancelled)
            failure.get()?.let {throw IllegalStateException("RuVoice initialization failed",it)}
            await(initialized,cancelled)
            check(status.get()==TextToSpeech.SUCCESS){"RuVoice initialization failed"}
            val tts=checkNotNull(instance.get())
            // Refuse other/default/network voices even if Android attempted engine fallback.
            check(NavigationSpeaker.entries.all {selected->tts.voices.orEmpty().any {
                it.name==selected.voiceName&&!it.isNetworkConnectionRequired&&it.locale.language=="ru"
            }}){"В RuVoice отсутствуют офлайн-голоса Xenia / Aidar"}
            return tts.also {engine=it}
        }catch(e:Exception){
            // Ordered after creation on the same main looper, including cancellation before bind.
            main.post {instance.get()?.shutdown()}
            throw e
        }
    }

    override fun warmUp(cancelled:()->Boolean) {
        generate("Навигация",NavigationSpeaker.FEMALE,1f,cancelled)
    }

    override fun generate(text:String,speaker:NavigationSpeaker,speed:Float,cancelled:()->Boolean):SynthesizedVoice {
        val tts=connected(cancelled)
        val voice=tts.voices.orEmpty().firstOrNull {it.name==speaker.voiceName&&!it.isNetworkConnectionRequired}
        check(voice!=null&&tts.setVoice(voice)==TextToSpeech.SUCCESS){"Голос RuVoice недоступен"}
        check(tts.setSpeechRate(speed)==TextToSpeech.SUCCESS&&tts.setPitch(1f)==TextToSpeech.SUCCESS)
        val done=CountDownLatch(1);val error=AtomicInteger(0)
        val directory=File(context.cacheDir,"navigation-voice").apply {check(mkdirs()||isDirectory)}
        val file=File.createTempFile("ruvoice-",".wav",directory)
        val id=file.name
        tts.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
            override fun onStart(utteranceId:String?) {}
            override fun onDone(utteranceId:String?) {if(utteranceId==id)done.countDown()}
            @Deprecated("Android compatibility callback")
            override fun onError(utteranceId:String?) {onError(utteranceId,TextToSpeech.ERROR)}
            override fun onError(utteranceId:String?,errorCode:Int) {
                if(utteranceId==id){error.set(if(errorCode==0)TextToSpeech.ERROR else errorCode);done.countDown()}
            }
        })
        try {
            if(cancelled())throw CancellationException()
            check(tts.synthesizeToFile(text,Bundle(),file,id)==TextToSpeech.SUCCESS){"RuVoice rejected synthesis"}
            await(done,cancelled)
            check(error.get()==0){"RuVoice synthesis error ${error.get()}"}
            check(file.length() in 44..6_000_000){"Invalid RuVoice audio length"}
            return readVoiceWave(file.readBytes())
        }catch(e:Exception){
            // A broken/timed-out service is re-bound on the next request, not replaced by default TTS.
            if(e !is CancellationException){engine=null;tts.shutdown()}else tts.stop()
            throw e
        }finally {
            tts.setOnUtteranceProgressListener(null)
            file.delete() // Only the unique file created above, never a broad cache cleanup.
        }
    }

    override fun close(){engine?.shutdown();engine=null}
}
