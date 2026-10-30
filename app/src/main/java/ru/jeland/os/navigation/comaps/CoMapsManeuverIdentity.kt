package ru.jeland.os.navigation.comaps

/** Street-label corrections must not turn one native maneuver into several spoken maneuvers. */
internal class CoMapsManeuverIdentity {
    private var id=0
    private var direction=""
    private var street=""
    private var distance=Int.MAX_VALUE
    fun reset(){id=0;direction="";street="";distance=Int.MAX_VALUE}
    fun update(nextDirection:String,nextStreet:String,nextDistance:Int):Int {
        val nextCloseTurn=distance<60&&nextDistance>distance+15&&nextStreet!=street
        if(id==0||nextDirection!=direction||nextDistance>distance.toLong()+100||nextCloseTurn)id++
        direction=nextDirection;street=nextStreet;distance=nextDistance
        return id
    }
}
