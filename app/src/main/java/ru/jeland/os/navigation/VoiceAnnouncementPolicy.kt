package ru.jeland.os.navigation

data class VoiceAnnouncement(val text:String,val distanceMeters:Int?=null,val includesStreet:Boolean=false)

private fun spokenManeuver(instruction:String,street:String,include:Boolean):String {
    if(street.isBlank())return instruction
    val action=instruction.substringBefore(" · ").trim()
    return if(include)"$action, ${street.trim()}"else action
}

/** Same ten-metre precision as the map, with full units for speech. */
fun voiceAnnouncement(meters:Int,instruction:String,kind:String):VoiceAnnouncement {
    val safeKind=kind.takeIf {it in setOf("left","right","straight","uturn","roundabout","arrive")}?:"straight"
    val clean=instruction.trim().trimEnd('.', ' ')
        .replace(Regex("(?iu)на лево"),"налево").replace(Regex("(?iu)на право"),"направо")
        .replace(Regex("(?iu)^через\\s+\\d+(?:[,.]\\d+)?\\s*(?:километр(?:а|ов)?|метр(?:а|ов)?|км|м)\\s+"),"")
    val distance=if(meters>=30&&safeKind!="arrive") (kotlin.math.round(meters.coerceAtMost(1000)/10.0)*10).toInt() else null
    val prefix=when(distance){null->"";1000->"Через 1 километр ";else->"Через $distance метров "}
    fun phrase(value:String)=if(prefix.isEmpty())value else prefix+value.replaceFirstChar {it.lowercase()}
    return VoiceAnnouncement(phrase(clean),distance)
}

/** Three phases per maneuver, not five distance buckets. Distances are our tuning, not an OEM standard.
 * Stable step IDs survive visual-text changes. Speed compensates fix age, audio startup and spoken prefix.
 */
class VoiceAnnouncementPolicy {
    private var step=""
    private var route:Int?=null
    private var lastStage=-1
    private var lastAt:Long?=null
    private var motorway=false
    private var previousProjected:Double?=null
    private var streetAnnounced=false
    fun reset(){step="";route=null;lastStage=-1;lastAt=null;motorway=false;previousProjected=null;streetAnnounced=false}
    private fun speed(value:Double?)=value?.takeIf {it.isFinite()&&it in 0.0..70.0}?:0.0
    private fun prepare()=if(motorway)1000 else 500
    private fun approach()=if(motorway)300 else 150
    internal fun leadMeters(speedMps:Double?,startupMillis:Long=250,fixAgeMillis:Long=0):Double =
        (speed(speedMps)*(2.0+startupMillis.coerceIn(0,1500)/1000.0+fixAgeMillis.coerceIn(0,2000)/1000.0)).coerceAtMost(150.0)
    private fun immediate(speedMps:Double?)=if(speed(speedMps)>0)(speed(speedMps)*3).coerceIn(20.0,80.0)else 25.0
    private fun due(projected:Double,speedMps:Double?)=when {
        projected<=immediate(speedMps)->2
        projected<=approach()->1
        projected<=prepare()->0
        else->-1
    }
    fun next(session:Int,stepId:Int,meters:Int,instruction:String,kind:String,enabled:Boolean,preview:Boolean,
        speedMps:Double?=null,nowMillis:Long=System.nanoTime()/1_000_000,startupMillis:Long=250,fixAgeMillis:Long=0,
        recordPlayback:Boolean=true,street:String=""):VoiceAnnouncement? {
        if(!enabled||preview||meters<0||instruction.isBlank())return null
        if(route!=session){reset();route=session}
        val key="$session/$stepId"
        if(key!=step){step=key;lastStage=-1;motorway=speed(speedMps)>=22.2;previousProjected=null;streetAnnounced=false}
        if(lastStage<0)motorway=speed(speedMps)>=22.2
        val projected=(meters-leadMeters(speedMps,startupMillis,fixAgeMillis)).coerceAtLeast(0.0)
        val previous=previousProjected;previousProjected=projected
        val candidate=if(kind=="arrive")3 else due(projected,speedMps)
        if(candidate<=lastStage)return null
        val gap=if(candidate==2)3000L else 12000L
        if(kind!="arrive"&&lastAt?.let {nowMillis-it<gap}==true)return null
        val nominal=when(candidate){0->prepare();1->approach();else->0}
        // Late first attachment must not say "500 m" when only 300 m really remain.
        val spoken=if(candidate in 0..1&&lastStage<0&&projected<nominal-35&&(previous==null||previous<=nominal))
            projected.toInt().coerceAtLeast(30) else nominal
        val includeStreet=street.isNotBlank()&&!streetAnnounced
        lastStage=candidate
        if(recordPlayback){lastAt=nowMillis;if(includeStreet)streetAnnounced=true}
        return voiceAnnouncement(spoken,spokenManeuver(instruction,street,includeStreet),kind).copy(includesStreet=includeStreet)
    }
    fun playbackStarted(nowMillis:Long,includesStreet:Boolean=false){lastAt=nowMillis;if(includesStreet)streetAnnounced=true}
    /** Uses exactly the same text as normal threshold playback, enabling a real cache hit. */
    fun upcoming(meters:Int,instruction:String,kind:String,speedMps:Double?=null,startupMillis:Long=250,fixAgeMillis:Long=0,street:String=""):VoiceAnnouncement? {
        if(instruction.isBlank()||kind=="arrive"||meters<0||lastStage>=2)return null
        val projected=(meters-leadMeters(speedMps,startupMillis,fixAgeMillis)).coerceAtLeast(0.0)
        val next=maxOf(lastStage+1,due(projected,speedMps))
        val includeStreet=street.isNotBlank()&&!streetAnnounced
        return voiceAnnouncement(when(next){0->prepare();1->approach();else->0},spokenManeuver(instruction,street,includeStreet),kind).copy(includesStreet=includeStreet)
    }
}
