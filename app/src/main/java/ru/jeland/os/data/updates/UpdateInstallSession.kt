package ru.jeland.os.data.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** Called only after APK identity/hash checks. System confirmation is always required. */
internal class UpdateInstallSession(private val context:Context) {
    private val installer=context.packageManager.packageInstaller
    fun prepare(file:File):Int {
        val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName);setSize(file.length())
            if(Build.VERSION.SDK_INT>=31)setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id=installer.createSession(params)
        try {
            installer.openSession(id).use {session->
                session.openWrite("base.apk",0,file.length()).use {output->
                    file.inputStream().use {it.copyTo(output)};session.fsync(output)
                }
            }
            return id
        }catch(e:Exception){abandon(id);throw e}
    }
    fun commit(id:Int) {
        val callback=Intent(context,UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
            .putExtra(PackageInstaller.EXTRA_SESSION_ID,id)
        val flags=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
        val result=PendingIntent.getBroadcast(context,id,callback,flags)
        installer.openSession(id).use {it.commit(result.intentSender)}
    }
    fun abandon(id:Int){if(id>=0)runCatching {installer.abandonSession(id)}}
}
