package ru.jeland.os.navigation.comaps

/** Hides the obsolete drawing once per native rebuild, without closing routing or following. */
internal class CoMapsRouteUpdate {
    private var hidden=false
    fun reset(){hidden=false}
    fun clearDrawing(nativeState:String):Boolean {
        if(!isBuilding(nativeState)){hidden=false;return false}
        if(hidden)return false
        hidden=true;return true
    }
    companion object {
        fun isBuilding(state:String)=state in setOf("RouteBuilding","RouteNeedsRebuild","RouteRebuilding")
        fun ready(state:String)=state in setOf("RouteNotStarted","OnRoute","OffRoute","RouteNoFollowing")
    }
}
