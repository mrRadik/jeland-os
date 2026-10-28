package ru.jeland.os

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.data.updates.UpdateInstallActivity
import ru.jeland.os.data.updates.UpdateInstallerIntents
import java.io.File

class UpdateInstallerIntentInstrumentation {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Suppress("DEPRECATION") @Test fun nativeInstallerGetsReadAccessAndForegroundResult() {
        val file=File(context.filesDir,"updates/ready/JelandOS-999999.apk")
        check(!file.exists());file.parentFile!!.mkdirs();file.writeText("not an installable APK")
        try {
            val uri=FileProvider.getUriForFile(context,context.packageName+".updates",file)
            val intent=UpdateInstallerIntents.apk(context,uri)
            assertEquals(Intent.ACTION_INSTALL_PACKAGE,intent.action)
            assertEquals(UpdateInstallerIntents.MIME,intent.type)
            assertEquals(uri,intent.data);assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
            assertEquals(0,intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT,false))
            assertFalse(intent.hasExtra("android.intent.extra.NOT_UNKNOWN_SOURCE"))
            context.contentResolver.openInputStream(uri)!!.use {assertEquals("not an installable APK",it.reader().readText())}
            val activity=context.packageManager.getActivityInfo(android.content.ComponentName(context,UpdateInstallActivity::class.java),0)
            assertFalse(activity.exported)
        }finally {file.delete()}
    }
    @Test fun unverifiedOrForeignFileCannotBePassedToInstaller() {
        for(address in listOf("file:///sdcard/Download/update.apk","content://other.app/verified_update/JelandOS-93.apk",
            "content://${context.packageName}.updates/staging/JelandOS-93.apk",
            "content://${context.packageName}.updates/verified_update/JelandOS-93.apk?file=other")) {
            try {UpdateInstallerIntents.apk(context,Uri.parse(address));fail(address)}catch(_:IllegalArgumentException){}
        }
    }
}
