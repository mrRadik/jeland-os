package ru.jeland.os

import android.content.pm.PackageInstaller
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.data.AndroidSettingsStore
import ru.jeland.os.data.updates.UpdateInstallSession
import java.io.File
import java.util.UUID

class AppUpdateInstallInstrumentation {
    @Test fun sessionWritesApkAndReportsAndroidRejectionThroughPrivateReceiver()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=(context.applicationContext as JelandApplication).container.updates
        check(!manager.state.value.downloading&&!manager.state.value.installing)
        val store=AndroidSettingsStore(context)
        val oldSession=store.string("updates.install_session","-1")
        val oldMessage=store.string("updates.install_message","")
        val file=File(context.cacheDir,"qa-invalid-update-${UUID.randomUUID()}.apk")
        // Deliberately invalid fixture: Android must reject it, never install anything.
        val payload="not-an-android-package".toByteArray();file.writeBytes(payload)
        val installer=UpdateInstallSession(context)
        var session=-1
        try {
            session=installer.prepare(file)
            val pm=context.packageManager.packageInstaller
            assertEquals(context.packageName,pm.getSessionInfo(session)!!.appPackageName)
            pm.openSession(session).use {open->open.openRead("base.apk").use {assertArrayEquals(payload,it.readBytes())}}
            store.put("updates.install_session",session.toString())
            store.put("updates.install_message","awaiting-test-result")
            installer.commit(session)
            withTimeout(15_000) {
                while(!store.string("updates.install_message","").contains("Android не смог установить APK"))delay(50)
            }
            assertEquals("-1",store.string("updates.install_session",""))
            assertFalse(manager.state.value.installing)
        }finally {
            installer.abandon(session);file.delete()
            store.put("updates.install_session",oldSession);store.put("updates.install_message",oldMessage)
        }
    }
}
