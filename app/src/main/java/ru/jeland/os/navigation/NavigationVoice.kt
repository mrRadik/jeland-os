package ru.jeland.os.navigation

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Offline RuVoice synthesis; Jeland owns navigation timing, caching and playback. */
class NavigationVoice internal constructor(context:Context,private val factory:(Context)->VoiceSynthesizer):AutoCloseable {
    constructor(context:Context):this(context,::RuVoiceSynthesizer)
    private val app=context.applicationContext
    private val prefs=app.getSharedPreferences("jelandos_mock_only",0)
    val enabled=MutableStateFlow(prefs.getBoolean("navigation-voice",true))
    val volume=MutableStateFlow(prefs.getFloat("navigation-voice-volume",.8f).coerceIn(0f,1f))
    val speaker=MutableStateFlow(NavigationSpeaker.fromSid(prefs.getInt("navigation-voice-speaker",1)))
    val speed=MutableStateFlow(prefs.getFloat("navigation-voice-speed",1f).takeIf {it.isFinite()}?.coerceIn(.8f,1.2f)?:1f)
    val licenseAccepted=MutableStateFlow(prefs.getBoolean("navigation-ruvoice-license-v1",false))
    private fun readyStatus()=if(licenseAccepted.value)"RuVoice · Silero · без сети"else"RuVoice · подтвердите условия модели"
    val status=MutableStateFlow(readyStatus())
    val lastSpoken=MutableStateFlow("")
    val lastSpeaker=MutableStateFlow(-1)
    val lastSynthesisMillis=MutableStateFlow(0L)
    val lastAudioMillis=MutableStateFlow(0L)
    val playing=MutableStateFlow(false)
    val generating=MutableStateFlow(false)
    private val handler=Handler(Looper.getMainLooper())
    private val audio=app.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val policy=VoiceAnnouncementPolicy()
    private val worker=Executors.newSingleThreadExecutor {r->Thread(r,"Jeland-RuVoice").apply {isDaemon=true}}
    private var engine:VoiceSynthesizer?=null
    private val cache=object:LinkedHashMap<String,ByteArray>(8,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,ByteArray>?)=size>12
    }
    private var player:MediaPlayer?=null
    private var focus:AudioFocusRequest?=null
    private val closed=AtomicBoolean()
    private val generation=AtomicLong()
    private var guidanceKey=""
    private var prefetched=""
    private var warming=false
    private var eventInFlight=false
    private var startupEstimateMillis=250.0
    private fun onMain(block:()->Unit){if(Looper.myLooper()==Looper.getMainLooper())block()else handler.post(block)}
    fun setEnabled(value:Boolean){enabled.value=value;prefs.edit().putBoolean("navigation-voice",value).apply();if(!value)stop()else prepare()}
    fun setVolume(value:Float){if(!value.isFinite())return;volume.value=value.coerceIn(0f,1f);prefs.edit().putFloat("navigation-voice-volume",volume.value).apply();onMain {player?.setVolume(volume.value,volume.value)}}
    fun setSpeaker(value:NavigationSpeaker){stop();speaker.value=value;prefs.edit().putInt("navigation-voice-speaker",value.sid).apply()}
    fun setSpeed(value:Float){if(!value.isFinite())return;stop();speed.value=value.coerceIn(.8f,1.2f);prefs.edit().putFloat("navigation-voice-speed",speed.value).apply()}
    fun acceptLicense(){licenseAccepted.value=true;prefs.edit().putBoolean("navigation-ruvoice-license-v1",true).apply();status.value=readyStatus()}
    fun installed()=ruVoiceInstalled(app)
    /** Load the model while the route is being calculated, before a maneuver needs speaking. */
    fun prepare(){onMain {
        if(closed.get()||!enabled.value||!licenseAccepted.value||warming)return@onMain
        warming=true
        val token=generation.get()
        worker.execute {
            fun obsolete()=closed.get()||token!=generation.get()
            try {if(!obsolete())(engine?:factory(app).also {engine=it}).warmUp(::obsolete)}
            catch(e:Exception){android.util.Log.w("JelandNavigation","RuVoice warm-up: ${e.message}")}
            finally {onMain {warming=false}}
        }
    }}
    fun announce(session:Int,step:Int,meters:Int,instruction:String,type:String?,modifier:String?,preview:Boolean,
        speedMps:Double?=null,fixAgeMillis:Long=0,street:String="") {
        val kind=when {
            type.orEmpty().contains("ARRIVE")->"arrive"
            type.orEmpty().contains("ROUNDABOUT")||type.orEmpty().contains("ROTARY")||instruction.contains("кругов",true)->"roundabout"
            modifier.orEmpty().replace("_","").contains("UTURN")||instruction.contains("разверн",true)->"uturn"
            modifier.orEmpty().contains("LEFT")||instruction.contains("налево",true)->"left"
            modifier.orEmpty().contains("RIGHT")||instruction.contains("направо",true)->"right"
            else->"straight"
        }
        val token=generation.get()
        onMain {
            if(closed.get()||token!=generation.get()||!licenseAccepted.value||!enabled.value||preview||eventInFlight)return@onMain
            val key="$session/$step"
            if(key!=guidanceKey){generation.incrementAndGet();stopAudio();guidanceKey=key;prefetched=""}
            policy.next(session,step,meters,instruction,kind,true,false,speedMps,SystemClock.elapsedRealtime(),
                startupEstimateMillis.toLong(),fixAgeMillis,recordPlayback=false,street=street)?.let {
                android.util.Log.i("JelandNavigation","VOICE_CUE session=$session step=$step remainingM=$meters spokenM=${it.distanceMeters} speedMps=$speedMps fixAgeMs=$fixAgeMillis leadM=${policy.leadMeters(speedMps,startupEstimateMillis.toLong(),fixAgeMillis)}")
                speak(it.text,true,includesStreet=it.includesStreet)
            }
            policy.upcoming(meters,instruction,kind,speedMps,startupEstimateMillis.toLong(),fixAgeMillis,street)?.let {prefetch(it.text)}
        }
    }
    fun journey(event:NavigationJourneyEvent){onMain {
        if(closed.get()||!enabled.value||!licenseAccepted.value)return@onMain
        policy.reset();guidanceKey="";prefetched=""
        speak(event.speech,event=true)
    }}
    fun test(){onMain {if(!closed.get())speak(voiceAnnouncement(150,"Поверните направо на улицу Ленина","right").text)}}
    private fun speak(value:String,timed:Boolean=false,includesStreet:Boolean=false,event:Boolean=false) {
        val token=generation.incrementAndGet()
        stopAudio()
        if(!licenseAccepted.value){status.value="Подтвердите условия RuVoice в настройках голоса";return}
        if(volume.value<=0f){status.value="Громкость голоса — 0";return}
        val text=navigationSpeechText(value)
        if(text.isBlank()||text.length>500){status.value="Некорректная голосовая подсказка";return}
        val chosen=speaker.value;val rate=speed.value
        val requestedAt=SystemClock.elapsedRealtime()
        generating.value=true;eventInFlight=event;status.value="Подготовка голоса · ${chosen.title.lowercase()}"
        worker.execute {
            // Do not play an old distance after a slow service initialization or route change.
            fun obsolete()=closed.get()||token!=generation.get()||(timed&&SystemClock.elapsedRealtime()-requestedAt>3000)
            try {
                if(obsolete())return@execute
                val key="${chosen.sid}/$rate/$text"
                val wave=cache[key]?:run {
                    val synthesizer=engine?:factory(app).also {engine=it}
                    if(obsolete())return@execute
                    val rendered=synthesizer.generate(text,chosen,rate,::obsolete)
                    if(obsolete())return@execute
                    voiceWave(rendered).also {cache[key]=it}
                }
                onMain {if(!obsolete()){
                    generating.value=false;lastSynthesisMillis.value=SystemClock.elapsedRealtime()-requestedAt
                    lastAudioMillis.value=(wave.size-44L)*1000/(java.nio.ByteBuffer.wrap(wave).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(24)*2)
                    playWave(wave,text,chosen,token,requestedAt,timed,includesStreet,::obsolete)
                }}
            }catch(e:Exception){onMain {if(!obsolete()){
                generating.value=false;status.value=e.message?.takeIf {it.contains("RuVoice")||it.contains("Установите")}?:"Не удалось подготовить голос RuVoice"
                android.util.Log.e("JelandNavigation","RuVoice synthesis failed",e)
            }}}
            catch(e:LinkageError){onMain {if(!obsolete()){
                generating.value=false;status.value="Голос не поддерживается на этом устройстве"
                android.util.Log.e("JelandNavigation","RuVoice unavailable",e)
            }}}
            finally {onMain {if(token==generation.get()&&!playing.value){generating.value=false;if(player==null)eventInFlight=false}}}
        }
    }
    /** Only the next threshold, once per step/configuration; never queue a whole route. */
    private fun prefetch(value:String) {
        val text=navigationSpeechText(value);val chosen=speaker.value;val rate=speed.value
        if(text.isBlank()||text.length>500||volume.value<=0f)return
        val key="${chosen.sid}/$rate/$text"
        if(prefetched==key)return
        prefetched=key
        val token=generation.get()
        worker.execute {
            fun obsolete()=closed.get()||token!=generation.get()
            if(obsolete()||cache.containsKey(key))return@execute
            try {
                val rendered=(engine?:factory(app).also {engine=it}).generate(text,chosen,rate,::obsolete)
                if(!obsolete())cache[key]=voiceWave(rendered)
            }catch(e:Exception){if(!obsolete())android.util.Log.w("JelandNavigation","RuVoice prefetch: ${e.message}")}
        }
    }
    private fun playWave(wave:ByteArray,text:String,chosen:NavigationSpeaker,token:Long,requestedAt:Long,timed:Boolean,includesStreet:Boolean,obsolete:()->Boolean) {
        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({change->onMain {
                if(token==generation.get()&&(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))stop()
            }},handler).build()
        if(audio.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){eventInFlight=false;status.value="Голос: аудиоканал занят";return}
        focus=request
        try {
            player=MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(object:MediaDataSource() {
                    override fun getSize()=wave.size.toLong()
                    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
                        if(size==0)return 0
                        if(position<0||position>=wave.size)return -1
                        val count=minOf(size,wave.size-position.toInt())
                        wave.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count
                    }
                    override fun close(){}
                })
                setVolume(volume.value,volume.value)
                setOnPreparedListener {if(player===it&&!obsolete()){
                    val startup=(SystemClock.elapsedRealtime()-requestedAt).coerceIn(0,1500)
                    startupEstimateMillis=startupEstimateMillis*.7+startup*.3
                    if(timed)policy.playbackStarted(SystemClock.elapsedRealtime(),includesStreet)
                    it.start();lastSpoken.value=text;lastSpeaker.value=chosen.sid;playing.value=true
                    status.value="RuVoice · ${chosen.voiceName.removeSuffix("-ru")} · без сети"
                    android.util.Log.i("JelandNavigation","VOICE_RUVOICE voice=${chosen.voiceName} readyMs=${lastSynthesisMillis.value} audioMs=${lastAudioMillis.value}")
                }else if(player===it)stopAudio()}
                setOnCompletionListener {if(player===it){stopAudio()}}
                setOnErrorListener {p,_,_->if(player===p){stopAudio();status.value="Ошибка воспроизведения голоса"};true}
                prepareAsync()
            }
        }catch(e:Exception){stopAudio();status.value="Голос недоступен";android.util.Log.e("JelandNavigation","Voice playback failed",e)}
    }
    private fun stopAudio(){player?.release();player=null;playing.value=false;generating.value=false;eventInFlight=false;focus?.let {audio.abandonAudioFocusRequest(it)};focus=null}
    fun stop(){generation.incrementAndGet();onMain {policy.reset();guidanceKey="";prefetched="";stopAudio();status.value=readyStatus()}}
    override fun close(){
        if(!closed.compareAndSet(false,true))return
        generation.incrementAndGet();onMain {stopAudio()}
        worker.execute {try {engine?.close();engine=null;cache.clear()}finally {worker.shutdown()}}
    }
}
