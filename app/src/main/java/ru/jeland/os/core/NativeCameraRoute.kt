package ru.jeland.os.core

internal enum class NativeCameraRoute {CARINFO,RVC}
internal data class NativeCameraConfiguration(val architecture:String,val flag55:Int?,val flag112:Int?) {
    // Same decision as Launcher/SystemUI; their missing configurations default to non-1.
    val route get()=if(flag55==1||flag112==1)NativeCameraRoute.CARINFO else NativeCameraRoute.RVC
}
internal fun nativeCameraConfiguration(architecture:String,config3:String?,config4:String?):NativeCameraConfiguration {
    fun bytes(value:String?):List<Int>? {
        val text=value?.trim()?.takeIf {it.isNotEmpty()&&it.length%2==0&&it.length<=512&&it.all {c->c.isDigit()||c.lowercaseChar() in 'a'..'f'}}?:return null
        return runCatching {text.chunked(2).map {it.toInt(16)}}.getOrNull()
    }
    val third=bytes(config3);val fourth=bytes(config4)
    return if(architecture=="5.1")NativeCameraConfiguration(architecture,
        third?.takeIf {it.size>=63}?.get(10)?.let {(it shr 7) and 1},null) // 112 unsupported on OEM EEA5.1.
    else NativeCameraConfiguration(architecture,third?.takeIf {it.size>=8}?.get(4)?.let {(it shr 2) and 1},
        fourth?.takeIf {it.size>=8}?.get(5)?.let {(it shr 7) and 1})
}
