package ru.jeland.os.core

object DayNightSignal {
    /** Android Configuration.UI_MODE_NIGHT_MASK; unrelated UI mode bits are ignored. */
    fun read(uiMode:Int):Boolean?=when(uiMode and 0x30){0x20->true;0x10->false;else->null}
}
