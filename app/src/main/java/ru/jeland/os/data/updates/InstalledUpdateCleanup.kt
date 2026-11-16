package ru.jeland.os.data.updates

import android.content.Context
import android.util.Log
import ru.jeland.os.core.OwnUpdateApkCache
import ru.jeland.os.core.UpdateNews
import ru.jeland.os.core.UpdateNewsTracker
import ru.jeland.os.data.AndroidSettingsStore

/** Runs in the NEW process, including after MY_PACKAGE_REPLACED. No UI or car graph. */
object InstalledUpdateCleanup {
    fun run(context:Context) {
        runCatching {
            val store=AndroidSettingsStore(context)
            val actual=context.packageManager.getPackageInfo(context.packageName,0).longVersionCode
            val pending=runCatching {GitHubUpdateCodec.restore(store.string("updates.pending",""))}.getOrNull()
            val news=UpdateNewsTracker(store)
            // One-time migration from updater versions predating installation receipts.
            // Legacy metadata is accepted only when Android identifies JOS as the installer source.
            if(store.string(UpdateNewsTracker.SUPPORTED,"").isEmpty()&&pending?.manifest?.versionCode==actual) {
                val ownSource=runCatching {context.packageManager.getInstallSourceInfo(context.packageName).let {
                    it.initiatingPackageName==context.packageName||it.installingPackageName==context.packageName||it.originatingPackageName==context.packageName
                }}.getOrDefault(false)
                val attempted=store.string("updates.install_method","") in setOf("session","package_intent")
                if(ownSource&&attempted&&actual>0)news.arm(actual-1,UpdateNews(actual,pending.manifest.versionName,pending.releaseNotes))
            }
            news.captureInstalled(actual)
            store.putDurable(UpdateNewsTracker.SUPPORTED,"1")
            val confirmed=pending?.manifest?.versionCode?.let {it<=actual}==true
            val result=OwnUpdateApkCache.clearInstalled(context.filesDir,actual,clearStaging=confirmed)
            if(confirmed){store.put("updates.pending","");store.put("updates.install_message","");store.put("updates.install_session","-1")}
            Log.i("JelandOSUpdate","Installed APK cleanup: ${result.deleted} files, ${result.failed} failures")
        }.onFailure {Log.w("JelandOSUpdate","Installed APK cleanup will retry on next start",it)}
    }
}
