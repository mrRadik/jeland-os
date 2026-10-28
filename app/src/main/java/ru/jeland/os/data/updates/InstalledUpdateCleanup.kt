package ru.jeland.os.data.updates

import android.content.Context
import android.util.Log
import ru.jeland.os.core.OwnUpdateApkCache
import ru.jeland.os.data.AndroidSettingsStore

/** Runs in the NEW process, including after MY_PACKAGE_REPLACED. No UI or car graph. */
object InstalledUpdateCleanup {
    fun run(context:Context) {
        runCatching {
            val store=AndroidSettingsStore(context)
            val actual=context.packageManager.getPackageInfo(context.packageName,0).longVersionCode
            val pending=runCatching {GitHubUpdateCodec.restore(store.string("updates.pending",""))}.getOrNull()
            val confirmed=pending?.manifest?.versionCode?.let {it<=actual}==true
            val result=OwnUpdateApkCache.clearInstalled(context.filesDir,actual,clearStaging=confirmed)
            if(confirmed){store.put("updates.pending","");store.put("updates.install_message","");store.put("updates.install_session","-1")}
            Log.i("JelandOSUpdate","Installed APK cleanup: ${result.deleted} files, ${result.failed} failures")
        }.onFailure {Log.w("JelandOSUpdate","Installed APK cleanup will retry on next start",it)}
    }
}
