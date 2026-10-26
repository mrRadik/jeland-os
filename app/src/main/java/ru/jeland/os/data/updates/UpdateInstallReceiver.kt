package ru.jeland.os.data.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.jeland.os.JelandApplication

/** Explicit, non-exported callback. Android fills the result via our PendingIntent. */
class UpdateInstallReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!=ACTION)return
        (context.applicationContext as JelandApplication).container.updates.installStatus(intent)
    }
    companion object {const val ACTION="ru.jeland.os.updates.INSTALL_STATUS"}
}
