package ru.jeland.os.navigation

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Keep the old persisted IDs so upgrading does not swap the user's selected sex.
enum class NavigationSpeaker(val sid:Int,val title:String,val voiceName:String) {
    FEMALE(1,"Женский","xenia-ru"),MALE(7,"Мужской","aidar-ru");
    companion object {fun fromSid(sid:Int)=entries.firstOrNull {it.sid==sid}?:FEMALE}
}

/** Expand distance explicitly instead of relying on the model to guess Russian numerals/units. */
internal fun navigationSpeechText(text:String):String {
    fun number(value:Int):String {
        if(value==1000)return "тысячу"
        val hundreds=listOf("","сто","двести","триста","четыреста","пятьсот","шестьсот","семьсот","восемьсот","девятьсот")
        val tens=listOf("","десять","двадцать","тридцать","сорок","пятьдесят","шестьдесят","семьдесят","восемьдесят","девяносто")
        val ones=listOf("","один","два","три","четыре","пять","шесть","семь","восемь","девять")
        val teens=listOf("десять","одиннадцать","двенадцать","тринадцать","четырнадцать","пятнадцать","шестнадцать","семнадцать","восемнадцать","девятнадцать")
        val rest=value%100
        return listOf(hundreds[value/100],if(rest in 10..19)teens[rest-10]else listOf(tens[rest/10],ones[rest%10]).filter {it.isNotEmpty()}.joinToString(" ")).filter {it.isNotEmpty()}.joinToString(" ")
    }
    val clean=text.replace(Regex("<[^>]*>"),"").replace(Regex("[\\p{Cc}\\p{Cf}]")," ").replace(Regex("\\s+")," ").trim()
    return Regex("(?iu)^через (\\d{1,4}) (метров|километр)(?=\\s|$)").replace(clean) {match->
        val value=match.groupValues[1].toInt()
        if(value !in 1..1000)match.value else "Через ${number(value)} ${match.groupValues[2]}"
    }
}

internal data class SynthesizedVoice(val samples:FloatArray,val sampleRate:Int)
internal interface VoiceSynthesizer:AutoCloseable {
    fun warmUp(cancelled:()->Boolean) {}
    fun generate(text:String,speaker:NavigationSpeaker,speed:Float,cancelled:()->Boolean):SynthesizedVoice
}

internal fun voiceWave(audio:SynthesizedVoice):ByteArray {
    require(audio.sampleRate in 8000..48000&&audio.samples.isNotEmpty()&&audio.samples.size<=audio.sampleRate*60)
    val dataSize=audio.samples.size*2
    val result=ByteBuffer.allocate(44+dataSize).order(ByteOrder.LITTLE_ENDIAN)
    result.put("RIFF".toByteArray()).putInt(36+dataSize).put("WAVEfmt ".toByteArray()).putInt(16)
        .putShort(1).putShort(1).putInt(audio.sampleRate).putInt(audio.sampleRate*2).putShort(2).putShort(16)
        .put("data".toByteArray()).putInt(dataSize)
    audio.samples.forEach {sample->result.putShort((if(sample.isFinite())sample.coerceIn(-1f,1f)*32767 else 0f).toInt().toShort())}
    return result.array()
}

/** Android TTS WAVs may contain additional RIFF chunks; do not assume a 44-byte header. */
internal fun readVoiceWave(wave:ByteArray):SynthesizedVoice {
    require(wave.size in 44..6_000_000)
    val buffer=ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN)
    fun tag(offset:Int)=String(wave,offset,4,Charsets.US_ASCII)
    require(tag(0)=="RIFF"&&tag(8)=="WAVE")
    var rate=0;var format=0;var channels=0;var bits=0;var offset=12
    var dataStart=0;var dataLength=0
    while(offset+8<=wave.size) {
        val size=buffer.getInt(offset+4)
        require(size>=0&&size<=wave.size-offset-8)
        when(tag(offset)) {
            "fmt "->{require(size>=16);format=buffer.getShort(offset+8).toInt();channels=buffer.getShort(offset+10).toInt()
                rate=buffer.getInt(offset+12);bits=buffer.getShort(offset+22).toInt()}
            "data"->{dataStart=offset+8;dataLength=size}
        }
        offset+=8+size+(size and 1)
    }
    require(format==1&&channels==1&&bits==16&&rate in 8000..48000)
    require(dataLength>0&&dataLength%2==0&&dataLength/2<=rate*60)
    return SynthesizedVoice(FloatArray(dataLength/2){buffer.getShort(dataStart+it*2)/32768f},rate)
}
