package ru.jeland.os.core

/** Short, single-use correlation with our own noisy request, not a global OEM UI blocker. */
internal class ClimatePanelDismissal {
    companion object {const val WINDOW_MILLIS=1500L}
    private var sequence=0L
    private var deadline=0L
    private var claimed=false
    @Synchronized fun arm(commands:List<VdsWrite>,now:Long):Long? {
        if(commands.none(::mayOpenPanel))return null
        sequence++;deadline=now+WINDOW_MILLIS;claimed=false
        return sequence
    }
    @Synchronized fun opened(now:Long):Long? {
        if(deadline==0L||now>deadline||claimed)return null
        claimed=true
        return sequence
    }
    @Synchronized fun canDismiss(ticket:Long,now:Long)=ticket==sequence&&claimed&&deadline!=0L&&now<=deadline
    @Synchronized fun cancel(ticket:Long?=null) {
        if(ticket==null||ticket==sequence){sequence++;deadline=0;claimed=false}
    }
    private fun mayOpenPanel(write:VdsWrite):Boolean=when(write.key.module) {
        VerifiedVehicleProtocol.HVAC->write.key.cmd in setOf(1,3,4,6,8)||(write.key.cmd==18&&write.value==1)
        VerifiedVehicleProtocol.CAR->write.key.cmd in setOf(93,94,95,96,139,141,160)
        else->false
    }
}
