package ru.jeland.os.navigation.comaps

import ru.jeland.os.navigation.NavigationJourneyEvent

/** Native route-build callbacks also arrive for reroutes; only the first may start the camera. */
internal class CoMapsGuidanceSession {
    var started=false
        private set
    private var finished=false
    private var rebuilding=false
    private var rebuildAnnounced=false
    private var onRouteFixes=0
    private var geometry:Int?=null
    fun reset(){started=false;finished=false;rebuilding=false;rebuildAnnounced=false;onRouteFixes=0;geometry=null}
    fun built(fingerprint:Int,following:Boolean):NavigationJourneyEvent? {
        if(!following||finished)return null
        if(!started){started=true;geometry=fingerprint;return NavigationJourneyEvent.STARTED}
        val changed=rebuilding||geometry!=fingerprint
        geometry=fingerprint;rebuilding=false
        if(!changed||rebuildAnnounced)return null
        rebuildAnnounced=true;onRouteFixes=0
        return NavigationJourneyEvent.REBUILT
    }
    fun update(nativeState:String,locationFix:Boolean=false):NavigationJourneyEvent? {
        if(!started||finished)return null
        if(nativeState in setOf("RouteNeedsRebuild","RouteRebuilding")) {rebuilding=true;onRouteFixes=0}
        else if(nativeState=="OnRoute"&&locationFix&&rebuildAnnounced) {
            // Build callbacks and intermediate geometry can arrive several times
            // for one deviation. Rearm only after GPS confirms we are back on route.
            if(++onRouteFixes>=3){rebuildAnnounced=false;rebuilding=false;onRouteFixes=0}
        }else if(nativeState!="OnRoute")onRouteFixes=0
        if(nativeState!="RouteFinished")return null
        finished=true
        return NavigationJourneyEvent.ARRIVED
    }
    fun stop():NavigationJourneyEvent? {
        val event=if(started&&!finished)NavigationJourneyEvent.STOPPED else null
        reset();return event
    }
}
