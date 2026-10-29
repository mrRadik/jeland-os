package ru.jeland.os.navigation.comaps

import ru.jeland.os.navigation.NavigationJourneyEvent

/** Native route-build callbacks also arrive for reroutes; only the first may start the camera. */
internal class CoMapsGuidanceSession {
    var started=false
        private set
    private var finished=false
    private var rebuilding=false
    private var geometry:Int?=null
    fun reset(){started=false;finished=false;rebuilding=false;geometry=null}
    fun built(fingerprint:Int,following:Boolean):NavigationJourneyEvent? {
        if(!following||finished)return null
        if(!started){started=true;geometry=fingerprint;return NavigationJourneyEvent.STARTED}
        val changed=rebuilding||geometry!=fingerprint
        geometry=fingerprint;rebuilding=false
        return if(changed)NavigationJourneyEvent.REBUILT else null
    }
    fun update(nativeState:String):NavigationJourneyEvent? {
        if(!started||finished)return null
        if(nativeState in setOf("RouteNeedsRebuild","RouteRebuilding"))rebuilding=true
        if(nativeState!="RouteFinished")return null
        finished=true
        return NavigationJourneyEvent.ARRIVED
    }
    fun stop():NavigationJourneyEvent? {
        val event=if(started&&!finished)NavigationJourneyEvent.STOPPED else null
        reset();return event
    }
}
