package ru.jeland.os.core

/** Visibility only: this policy never wakes/stops speech recognition or touches a microphone. */
class AssistantAvatarPolicy {
    private var asrActive=false
    private var speaking=false
    private var uncertainAsr=false
    private var uncertainTts=false
    fun asr(status:Int?) {
        when(status) {
            1000,1001,1002->{asrActive=true;uncertainAsr=false}
            1003->{asrActive=false;uncertainAsr=false}
            else->uncertainAsr=true // A new/unsupported protocol must not hide an active assistant.
        }
    }
    fun tts(type:String?,status:Int?) {
        when(type) {
            "ttsPlayBegin"->{speaking=true;uncertainTts=false}
            "ttsPlayComplete","ttsPlayError","ttsPlayInterrupted"->{speaking=false;uncertainTts=false}
            "ttsIsPlaying"->when(status){0->{speaking=false;uncertainTts=false};1->{speaking=true;uncertainTts=false};else->uncertainTts=true}
            "onProgressReturn"->Unit
            else->uncertainTts=true
        }
    }
    fun shouldShow(foreground:Boolean,subscribed:Boolean)=!foreground||!subscribed||asrActive||speaking||uncertainAsr||uncertainTts
}
