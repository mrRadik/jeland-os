package ru.jeland.os.core

/** One cue per visible alert episode, shared by the map and cluster. Updates are silent. */
class RoadAlertChimePolicy {
    private var active=false
    private var pending=false
    fun update(alert:RoadAlert?,replay:Boolean=false) {
        if(alert==null){active=false;pending=false}
        else if(!active){active=true;pending=!replay}
    }
    fun shown():Boolean {
        val play=pending
        pending=false
        return play
    }
}
