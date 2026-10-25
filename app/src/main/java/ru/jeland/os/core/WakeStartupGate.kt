package ru.jeland.os.core

/** One launch opportunity per observed screen-off cycle, retained across service recreation. */
class WakeStartupGate(initialPending:Boolean=false,private val save:(Boolean)->Unit={}) {
    var pending=initialPending
        private set
    fun screenOff() { pending=true;save(true) }
    fun clear() {pending=false;save(false)}
    fun takeWake(interactive:Boolean,locked:Boolean):Boolean {
        if(!pending||!interactive||locked)return false
        clear() // Consume BEFORE requesting an Activity; duplicates and failures never retry.
        return true
    }
}
