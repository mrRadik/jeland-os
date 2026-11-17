package ru.jeland.os.core

import java.util.Locale

enum class ClusterColorMode { MANUAL, DRIVE_MODE }

/** Preset names remain compatible with saved settings; custom RGB is opaque and lossless. */
data class ClusterAccent(val name:String,val title:String,val darkArgb:Int,val lightArgb:Int) {
    val hex:String get()=String.format(Locale.ROOT,"#%06X",darkArgb and 0xffffff)
    companion object {
        val CYAN=ClusterAccent("CYAN","Голубая",0xFF27CAFF.toInt(),0xFF00769F.toInt())
        val GREEN=ClusterAccent("GREEN","Зелёная",0xFF3CD4A0.toInt(),0xFF007650.toInt())
        val RED=ClusterAccent("RED","Красная",0xFFF27F70.toInt(),0xFFB74136.toInt())
        val entries=listOf(CYAN,GREEN,RED)
        fun custom(argb:Int):ClusterAccent {
            val opaque=argb or (0xff shl 24)
            return ClusterAccent(String.format(Locale.ROOT,"CUSTOM_%06X",opaque and 0xffffff),"Свой цвет",opaque,opaque)
        }
        fun valueOf(name:String):ClusterAccent = entries.firstOrNull {it.name==name}
            ?:if(Regex("CUSTOM_[0-9A-Fa-f]{6}").matches(name))custom(name.substring(7).toInt(16))
            else throw IllegalArgumentException("Unknown cluster colour")
    }
}

/** Stock CarInfoUtil: MODULE_NEW_ENERGY/4; raw 5=sand, 6=off-road (not their UI indices). */
enum class ClusterDriveMode(val raw:Int,val title:String,val accent:ClusterAccent) {
    ECO(0,"Эко",ClusterAccent.GREEN),
    COMFORT(1,"Стандарт",ClusterAccent.CYAN),
    SPORT(2,"Спорт",ClusterAccent.RED),
    SNOW(3,"Снег",ClusterAccent.custom(0xFF9BDFF6.toInt())),
    MUD(4,"Грязь",ClusterAccent.custom(0xFFE9AF66.toInt())),
    SAND(5,"Песок",ClusterAccent.custom(0xFFF2D17A.toInt())),
    OFFROAD(6,"Бездорожье",ClusterAccent.custom(0xFF77D5BF.toInt()));

    val englishTitle:String get()=if(this==COMFORT)"Standard"else name.lowercase(Locale.ROOT).replaceFirstChar {it.uppercase(Locale.ROOT)}
    companion object {
        const val MODULE=0x50002
        const val COMMAND=4
        fun read(link:VehicleLinkState,elapsed:Long):ClusterDriveMode? {
            if(link.demo||!link.connected)return null
            val sample=link.raw[VdsKey(MODULE,COMMAND)]?:return null
            if(elapsed-sample.receivedMillis !in 0..8_000)return null
            val code=sample.values?.singleOrNull()?:return null
            return entries.firstOrNull {it.raw==code}
        }
    }
}

fun InterfacePreferences.clusterAccentFor(mode:ClusterDriveMode?):ClusterAccent=
    if(clusterColorMode==ClusterColorMode.DRIVE_MODE)mode?.accent?:clusterAccent else clusterAccent
