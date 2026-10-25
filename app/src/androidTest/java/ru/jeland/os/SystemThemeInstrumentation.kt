package ru.jeland.os

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.data.AndroidDayNightProvider

class SystemThemeInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun actualAndroidDayNightAndLiveIgnoresDemoOverride() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val original=manager.nightMode
        val link=MutableStateFlow(VehicleLinkState(demo=false))
        val provider=AndroidDayNightProvider(context,link)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var collector:Job
        try {
            ui.runOnIdle {provider.start();collector=scope.launch {provider.isNight.collect {}}}
            shell("cmd uimode night no")
            ui.waitUntil(10000){!provider.isNight.value}
            ui.runOnIdle {provider.toggle()}
            assertFalse("DEMO toggle cannot override real HU day",provider.isNight.value)
            shell("cmd uimode night yes")
            ui.waitUntil(10000){provider.isNight.value}
            ui.runOnIdle {provider.onConfigurationChanged(Configuration().apply {uiMode=0})}
            assertTrue("Undefined configuration retains last valid mode",provider.isNight.value)
            ui.runOnIdle {provider.stop()}
            shell("cmd uimode night no")
            ui.runOnIdle {provider.start()}
            ui.waitUntil(10000){!provider.isNight.value}
        } finally {
            ui.runOnIdle {provider.stop();scope.cancel()}
            shell("cmd uimode night "+when(original){0->"auto";2->"yes";3->"custom";else->"no"})
        }
    }
    private fun shell(command:String) {
        val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {it.readText()}
    }
}
