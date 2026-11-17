package ru.jeland.os.core

data class RoadAlert(val key:String,val title:String,val detail:String,val postedAt:Long,val expiresAt:Long)
data class StrelkaNotice(val packageName:String,val channel:String?,val id:Int,val tag:String?,val key:String,
    val title:String,val detail:String,val postedAt:Long,val foreground:Boolean=false,val summary:Boolean=false)

/** Only the camera-alert notification emitted by the verified official Strelka build.
 * No distance, camera type or database date is inferred from arbitrary notification text.
 */
object StrelkaNoticePolicy {
    const val PACKAGE="com.ivolk.StrelkaGPS"
    const val TTL=60_000L
    fun alert(n:StrelkaNotice,elapsed:Long,wall:Long,replay:Boolean=false):RoadAlert? {
        if(n.packageName!=PACKAGE||n.channel!="1038"||n.id!=10017||n.tag!="Alert"||n.summary||n.foreground)return null
        val title=n.title.trim().take(180);val detail=n.detail.trim().take(360)
        if(title.isBlank()||n.key.isBlank())return null
        val age=wall-n.postedAt
        if(replay&&(age !in 0 until TTL))return null
        val remaining=if(age in 0 until TTL)TTL-age else if(!replay&&age<0)TTL else return null
        return RoadAlert(n.key,title,detail,n.postedAt,elapsed+remaining)
    }
    fun running(n:StrelkaNotice)=n.packageName==PACKAGE&&n.id==1095&&n.channel=="1037"&&n.foreground&&!n.summary
}

/** Ephemeral inbox; stale alerts are never saved or replayed as current after sleep. */
class RoadAlertInbox {
    var current:RoadAlert?=null;private set
    fun accept(n:StrelkaNotice,elapsed:Long,wall:Long,replay:Boolean=false):Boolean {
        val next=StrelkaNoticePolicy.alert(n,elapsed,wall,replay)?:return false
        val old=current
        if(old?.key==next.key&&old.postedAt==next.postedAt&&old.title==next.title&&old.detail==next.detail)return true
        current=next
        return true
    }
    fun remove(key:String){if(current?.key==key)current=null}
    fun expire(elapsed:Long){if(current?.let {elapsed>=it.expiresAt}==true)current=null}
    fun clear(){current=null}
}
