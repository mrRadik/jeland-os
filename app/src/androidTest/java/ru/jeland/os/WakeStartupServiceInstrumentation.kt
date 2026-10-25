package ru.jeland.os

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ru.jeland.os.data.*

/** Actual screen cycle, but only on an explicitly selected local emulator. Never on a head unit. */
class WakeStartupServiceInstrumentation {
    @Suppress("DEPRECATION") @Test fun localScreenCycleStartsOnceAndTurningOffRemovesWatcher() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("localWakeSmoke")=="true")
        assumeTrue(Build.HARDWARE.contains("vbox",true)||Build.HARDWARE.contains("goldfish",true)||Build.HARDWARE.contains("ranchu",true))
        assumeTrue(Settings.canDrawOverlays(context)) // This test never grants/revokes any permission.
        val preferences=context.getSharedPreferences("jelandos_mock_only",0)
        val keys=listOf(SystemStartup.ENABLED_KEY,SystemStartup.WAKE_PENDING_KEY,SystemStartup.LAST_WAKE_KEY,SystemStartup.WATCH_STATUS_KEY)
        val before=keys.associateWith {preferences.getString(it,null)}
        val manager=context.getSystemService(ActivityManager::class.java)
        fun serviceRunning()=manager.getRunningServices(100).any {it.service.className==WakeStartupService::class.java.name&&it.foreground}
        fun await(label:String,condition:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+10000
            while(!condition()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
            assertTrue(label,condition())
        }
        fun shell(command:String){instrumentation.uiAutomation.executeShellCommand(command).use {android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use {stream->stream.readBytes()}}}
        try {
            assertTrue(preferences.edit().putString(SystemStartup.ENABLED_KEY,"true")
                .putString(SystemStartup.WAKE_PENDING_KEY,"false").remove(SystemStartup.LAST_WAKE_KEY).commit())
            context.startActivity(SystemStartup.launchIntent(context,"qa_wake_setup"))
            await("foreground watcher running"){serviceRunning()}
            shell("input keyevent 3") // Real HOME, so reopening cannot be skipped as already-visible.
            instrumentation.waitForIdleSync()
            shell("input keyevent 223")
            await("off cycle persisted"){preferences.getString(SystemStartup.WAKE_PENDING_KEY,"false")=="true"}
            shell("input keyevent 224")
            await("wake launch requested"){preferences.getString(SystemStartup.LAST_WAKE_KEY,"").orEmpty().contains("REQUESTED")}
            await("MainActivity actually visible"){StartupVisibility.resumed}
            val wake=preferences.getString(SystemStartup.LAST_WAKE_KEY,"")
            assertEquals("false",preferences.getString(SystemStartup.WAKE_PENDING_KEY,"true"))
            shell("input keyevent 224")
            SystemClock.sleep(1000)
            assertEquals("no second launch without a new off cycle",wake,preferences.getString(SystemStartup.LAST_WAKE_KEY,""))
            assertTrue(preferences.edit().putString(SystemStartup.ENABLED_KEY,"false").commit())
            WakeStartupService.sync(context,false)
            await("watcher removed after disabling"){!serviceRunning()}
            assertEquals("false",preferences.getString(SystemStartup.WAKE_PENDING_KEY,"true"))
        }finally {
            shell("input keyevent 224")
            context.stopService(Intent(context,WakeStartupService::class.java))
            await("watcher stopped before restore"){!serviceRunning()}
            val editor=preferences.edit()
            before.forEach {(key,value)->if(value==null)editor.remove(key)else editor.putString(key,value)}
            assertTrue(editor.commit())
            if(before[SystemStartup.ENABLED_KEY]=="true")WakeStartupService.sync(context,true)
        }
    }
}
