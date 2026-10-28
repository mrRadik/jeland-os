package ru.jeland.os.data.updates

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import ru.jeland.os.JelandApplication

/** Foreground caller for the OEM installer. No exported entry point or privileged identity. */
class UpdateInstallActivity:ComponentActivity() {
    private val updates get()=(application as JelandApplication).container.updates
    private var launched=false
    private val result=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(intent.hasExtra(SESSION))updates.sessionConfirmationReturned(intent.getIntExtra(SESSION,-1))
        else updates.nativeInstallReturned(it.resultCode,it.data)
        finish()
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        launched=savedInstanceState?.getBoolean("installerLaunched")?:false
    }
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putBoolean("installerLaunched",launched);super.onSaveInstanceState(outState)
    }
    override fun onPostResume() {
        super.onPostResume()
        if(launched)return // ActivityResultRegistry restores an already launched confirmation.
        launched=true
        try {
            @Suppress("DEPRECATION")
            val confirmation=intent.getParcelableExtra<Intent>(CONFIRMATION)
            var next=confirmation?:UpdateInstallerIntents.apk(this,checkNotNull(intent.data){"Не передан проверенный APK"})
            try {result.launch(next)} catch(error:ActivityNotFoundException) {
                if(confirmation!=null)throw error
                next=Intent(next).setAction(Intent.ACTION_VIEW);result.launch(next)
            }
            updates.nativeInstallerOpened(next)
        }catch(e:Exception){updates.nativeInstallFailed(e);finish()}
    }
    companion object {
        internal const val CONFIRMATION="ru.jeland.os.updates.CONFIRMATION"
        internal const val SESSION="ru.jeland.os.updates.SESSION"
    }
}

internal object UpdateInstallerIntents {
    const val MIME="application/vnd.android.package-archive"
    @Suppress("DEPRECATION") fun apk(context:Context,uri:Uri):Intent {
        require(uri.scheme=="content"&&uri.authority==context.packageName+".updates"&&
            uri.query==null&&uri.fragment==null&&
            Regex("/verified_update/JelandOS-[0-9]+\\.apk").matches(uri.path.orEmpty())){"Неверный файл обновления"}
        return Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_RETURN_RESULT,true).apply {clipData=ClipData.newRawUri("Jeland OS APK",uri)}
    }
}
