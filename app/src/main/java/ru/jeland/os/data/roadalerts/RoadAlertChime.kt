package ru.jeland.os.data.roadalerts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import ru.jeland.os.R

/** Short local cue routed through the same navigation usage as spoken guidance. No audio queue. */
internal class RoadAlertChime(private val context:Context) {
    private val audio=context.getSystemService(AudioManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private val attributes=AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var player:MediaPlayer?=null
    private var focus:AudioFocusRequest?=null
    private val timeout=Runnable {stop()}

    fun play() {
        stop()
        val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener({change->
                if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)stop()
            },handler).build()
        if(audio.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return
        focus=request
        try {
            val next=MediaPlayer()
            player=next
            next.apply {
                setAudioAttributes(attributes)
                context.resources.openRawResourceFd(R.raw.strelka_chime).use {
                    setDataSource(it.fileDescriptor,it.startOffset,it.length)
                }
                setOnPreparedListener {if(player===it)it.start()}
                setOnCompletionListener {if(player===it)stop()}
                setOnErrorListener {p,_,_->if(player===p)stop();true}
                prepareAsync()
            }
            handler.postDelayed(timeout,2000)
        }catch(e:Exception){stop();android.util.Log.w("JelandRoadAlerts","Chime playback failed",e)}
    }
    fun stop() {
        handler.removeCallbacks(timeout)
        player?.release();player=null
        focus?.let {audio.abandonAudioFocusRequest(it)};focus=null
    }
}
