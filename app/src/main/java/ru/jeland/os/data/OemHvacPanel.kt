package ru.jeland.os.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** OEM UI-only close request, from HvacService.onStartCommand. Never stops/disables HVAC. */
object OemHvacPanel {
    const val PACKAGE="com.desaysv.svhvac"
    const val SERVICE="com.desaysv.svhvac.service.HvacService"
    const val CLOSE="com.desaysv.svhvac.CLOSE_PANEL"
    const val OPEN="com.desaysv.svhvac.OPEN_PANEL"
    fun closeIntent()=Intent().setComponent(ComponentName(PACKAGE,SERVICE)).putExtra("type",CLOSE)
    fun close(context:Context) {
        @Suppress("DEPRECATION") val info=context.packageManager.getServiceInfo(ComponentName(PACKAGE,SERVICE),0)
        check(info.enabled && info.exported){"OEM HVAC UI service is not exported/enabled"}
        context.startService(closeIntent())
    }
}
