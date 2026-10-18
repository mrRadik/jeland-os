package ru.jeland.os.core

object RadioReadings {
    fun wifiLevel(rssi:Int?):Int?=rssi?.takeIf {it in -126..-1}?.let {when {it<=-100->0;it>=-55->4;else->((it+100)*4/45)}}
    fun mobileType(raw:String?)=raw?.trim()?.uppercase()?.takeIf {it in setOf("2G","3G","4G","5G")}
    fun mobileLevel(raw:Int?)=raw?.takeIf {it in 0..4}
}
