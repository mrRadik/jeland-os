package ru.jeland.os

import android.content.pm.PackageInstaller
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.data.AndroidSettingsStore
import ru.jeland.os.data.updates.UpdateInstallSession
import java.io.File
import java.util.UUID

class AppUpdateInstallInstrumentation {
    @Test fun validSessionShowsEnabledConfirmationAndCancellationReturnsThroughCallback()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        // This test presses Cancel in a real installer. Never operate the head unit in CI/local QA.
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("allowInstallerUiTest")=="true")
        check(runCatching {context.packageManager.getPackageInfo("com.desaysv.ivi.vds.carinfo",0)}.isFailure)
        val manager=(context.applicationContext as JelandApplication).container.updates
        val store=AndroidSettingsStore(context)
        val oldSession=store.string("updates.install_session","-1")
        val oldMessage=store.string("updates.install_message","")
        val oldMethod=store.string("updates.install_method","")
        val installer=UpdateInstallSession(context)
        var session=-1
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        fun button(id:String):AccessibilityNodeInfo?=instrumentation.uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()
        try {
            // Android parses the installed, signed APK itself; no invalid-APK error dialog fixture.
            session=installer.prepare(File(context.applicationInfo.sourceDir))
            store.put("updates.install_session",session.toString())
            store.put("updates.install_method","session")
            withContext(Dispatchers.Main){manager.resume();installer.commit(session)}
            withTimeout(15_000) {
                while(button("android:id/button1")?.isEnabled!=true)delay(100)
            }
            val accept=button("android:id/button1")!!
            assertTrue("System install button is enabled",accept.isEnabled)
            assertTrue("Installer actually received the prepared session",
                store.string("updates.install_component","").contains("packageinstaller",ignoreCase=true))
            val cancel=button("android:id/button2")!!
            assertTrue(cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            withTimeout(15_000){while(store.string("updates.install_session","")!="-1")delay(100)}
            assertTrue(store.string("updates.install_message","").contains("отменена"))
            assertFalse(manager.state.value.installing)
        }finally {
            installer.abandon(session);scenario.close()
            store.put("updates.install_session",oldSession);store.put("updates.install_message",oldMessage)
            store.put("updates.install_method",oldMethod)
        }
    }
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
