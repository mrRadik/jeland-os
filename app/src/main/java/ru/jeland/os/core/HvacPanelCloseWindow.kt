package ru.jeland.os.core

/** Bounds UI-only collapse requests to our own command, never a global OEM-window blocker. */
class HvacPanelCloseWindow {
    private var deadline=Long.MIN_VALUE
    private var last=Long.MIN_VALUE
    private var attempts=0
    fun arm(now:Long) {deadline=now+3200;last=Long.MIN_VALUE;attempts=0}
    fun cancel() {deadline=Long.MIN_VALUE}
    fun active(now:Long)=now<deadline && attempts<5
    fun claim(now:Long):Boolean {
        if(!active(now) || (last!=Long.MIN_VALUE && now-last<350))return false
        last=now;attempts++;return true
    }
}
