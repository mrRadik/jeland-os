package ru.jeland.os

import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.MemorySettingsStore
import ru.jeland.os.data.*

/** Fake Context only: never grants app-ops, opens a real activity or reboots a device. */
class SystemStartupInstrumentation {
    private class FakeContext(base:Context):ContextWrapper(base) {
        val requests=mutableListOf<Intent>()
        var attempts=0
        var reject=false
        var missingPermissionRoutes=0
        override fun getApplicationContext():Context=this
        override fun startActivity(intent:Intent) {
            attempts++
            if(intent.action==Settings.ACTION_MANAGE_OVERLAY_PERMISSION&&missingPermissionRoutes>0) {
                missingPermissionRoutes--;throw ActivityNotFoundException("Synthetic OEM missing settings")
            }
            if(reject)throw SecurityException("Synthetic background launch denial")
            requests+=Intent(intent)
        }
    }
    private fun context()=FakeContext(InstrumentationRegistry.getInstrumentation().targetContext)
    @Test fun defaultOffAndMalformedStoredValueNeverStartAnything() {
        val context=context();val store=MemorySettingsStore()
        val startup=SystemStartup(context,store,permissionCheck={true})
        assertFalse(startup.state.value.enabled)
        assertEquals(BootStartupResult.DISABLED,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        store.put(SystemStartup.ENABLED_KEY,"not-a-boolean")
        assertEquals(BootStartupResult.DISABLED,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0,context.attempts)
    }
    @Test fun togglePersistsAndDisablingSurvivesControllerRecreation() {
        val context=context();val store=MemorySettingsStore()
        SystemStartup(context,store,permissionCheck={true}).setEnabled(true)
        val recreated=SystemStartup(context,store,permissionCheck={true})
        assertTrue(recreated.state.value.enabled)
        recreated.setEnabled(false)
        assertFalse(SystemStartup(context,store,permissionCheck={true}).state.value.enabled)
        assertEquals(BootStartupResult.DISABLED,recreated.onBoot(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0,context.attempts)
    }
    @Test fun onlyCompletedBootCanRequestOurNormalLauncherActivity() {
        val context=context();val store=MemorySettingsStore()
        val startup=SystemStartup(context,store,permissionCheck={true}).apply {setEnabled(true)}
        for(action in listOf(null,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_USER_PRESENT,Intent.ACTION_SCREEN_ON,"untrusted.action"))
            assertEquals(BootStartupResult.IGNORED,startup.onBoot(action))
        assertEquals(0,context.attempts)
        assertEquals(BootStartupResult.REQUESTED,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        val request=context.requests.single()
        assertEquals(Intent.ACTION_MAIN,request.action)
        assertEquals(ComponentName(context,MainActivity::class.java),request.component)
        assertTrue(request.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertTrue(request.flags and Intent.FLAG_ACTIVITY_NEW_TASK!=0)
        assertTrue(request.flags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED!=0)
        assertTrue(request.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP!=0)
        assertEquals(0,request.flags and (Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        assertEquals("system_boot",request.getStringExtra("ru.jeland.os.launch_reason"))
        assertTrue(store.string(SystemStartup.LAST_BOOT_KEY,"").contains("REQUESTED"))
    }
    @Test fun permissionReturnAndRevocationAreRefreshedWithoutBackgroundPrompt() {
        val context=context();var granted=false
        val startup=SystemStartup(context,MemorySettingsStore(),permissionCheck={granted}).apply {setEnabled(true)}
        assertFalse(startup.state.value.permissionGranted)
        assertEquals(BootStartupResult.NEEDS_PERMISSION,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0,context.attempts)
        granted=true;startup.refresh();assertTrue(startup.state.value.permissionGranted)
        granted=false;startup.refresh();assertFalse(startup.state.value.permissionGranted)
        assertEquals(BootStartupResult.NEEDS_PERMISSION,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0,context.attempts)
    }
    @Test fun permissionScreenIsOnlyAnExplicitPackageScopedRequest() {
        val context=context()
        val startup=SystemStartup(context,MemorySettingsStore(),permissionCheck={false})
        assertTrue(startup.openPermissionSettings())
        val request=context.requests.single()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,request.action)
        assertEquals("package:${context.packageName}",request.dataString)
        assertTrue(request.flags and Intent.FLAG_ACTIVITY_NEW_TASK!=0)
        assertFalse(startup.state.value.enabled)
    }
    @Test fun oemMissingPackageRouteFallsBackToCommonPermissionScreen() {
        val context=context().apply {missingPermissionRoutes=1}
        val startup=SystemStartup(context,MemorySettingsStore(),permissionCheck={false})
        assertTrue(startup.openPermissionSettings())
        assertEquals(2,context.attempts)
        assertNull(context.requests.single().data)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,context.requests.single().action)
        assertFalse(startup.state.value.permissionScreenUnavailable)
        assertFalse(startup.state.value.permissionGranted)
    }
    @Test fun missingBothOemScreensShowsInstallerAndExternalGrantClearsBlock() {
        val context=context().apply {missingPermissionRoutes=2};var granted=false
        val startup=SystemStartup(context,MemorySettingsStore(),permissionCheck={granted}).apply {setEnabled(true)}
        assertFalse(startup.openPermissionSettings())
        assertEquals(2,context.attempts)
        assertTrue(context.requests.isEmpty())
        assertTrue(startup.state.value.permissionScreenUnavailable)
        startup.refresh()
        assertTrue(startup.state.value.permissionScreenUnavailable)
        assertEquals(BootStartupResult.NEEDS_PERMISSION,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        granted=true;startup.refresh()
        assertTrue(startup.state.value.permissionGranted)
        assertFalse(startup.state.value.permissionScreenUnavailable)
        assertTrue(startup.openPermissionSettings()) // Already granted: no permission activity needed.
        assertEquals(2,context.attempts)
        assertEquals(BootStartupResult.REQUESTED,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(3,context.attempts)
    }
    @Test fun platformDenialIsRecordedAndNeverRetried() {
        val context=context().apply {reject=true};val store=MemorySettingsStore()
        val startup=SystemStartup(context,store,permissionCheck={true}).apply {setEnabled(true)}
        assertEquals(BootStartupResult.FAILED,startup.onBoot(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1,context.attempts)
        assertTrue(store.string(SystemStartup.LAST_BOOT_KEY,"").contains("SecurityException"))
        context.reject=false
        assertTrue(startup.state.value.enabled)
    }
    @Suppress("DEPRECATION") @Test fun bootEntryIsPrivateAndBootPermissionIsDeclared() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val info=context.packageManager.getReceiverInfo(ComponentName(context,SystemBootReceiver::class.java),0)
        assertTrue(info.enabled);assertFalse(info.exported)
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED,context.packageName))
    }
    @Test fun wakeUsesTheSameOptInPermissionAndNormalActivityWithoutTouchingLastBoot() {
        val context=context();val store=MemorySettingsStore();var permission=false
        val sync=mutableListOf<Boolean>()
        val startup=SystemStartup(context,store,permissionCheck={permission},wakeControl=sync::add)
        assertEquals(BootStartupResult.DISABLED,startup.onWake())
        startup.setEnabled(true);assertFalse(sync.last())
        assertEquals(BootStartupResult.NEEDS_PERMISSION,startup.onWake());assertEquals(0,context.attempts)
        permission=true;startup.refresh();assertTrue(sync.last())
        assertEquals(BootStartupResult.REQUESTED,startup.onWake())
        assertEquals("system_wake",context.requests.single().getStringExtra("ru.jeland.os.launch_reason"))
        assertEquals("untouched",store.string(SystemStartup.LAST_BOOT_KEY,"untouched"))
        assertTrue(store.string(SystemStartup.LAST_WAKE_KEY,"").contains("REQUESTED"))
        startup.setEnabled(false);assertFalse(sync.last())
        assertEquals(BootStartupResult.DISABLED,startup.onWake());assertEquals(1,context.attempts)
    }
    @Test fun wakeDenialIsRecordedAsFailureNotAClaimOfVisibility() {
        val context=context().apply {reject=true};val store=MemorySettingsStore()
        val startup=SystemStartup(context,store,permissionCheck={true}).apply {setEnabled(true)}
        assertEquals(BootStartupResult.FAILED,startup.onWake());assertEquals(1,context.attempts)
        assertTrue(store.string(SystemStartup.LAST_WAKE_KEY,"").contains("FAILED SecurityException"))
    }
    @Suppress("DEPRECATION") @Test fun wakeServiceIsPrivateAndDoesNotDieWhenTaskIsRemoved() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val info=context.packageManager.getServiceInfo(ComponentName(context,WakeStartupService::class.java),0)
        assertFalse(info.exported)
        assertEquals(0,info.flags and android.content.pm.ServiceInfo.FLAG_STOP_WITH_TASK)
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(android.Manifest.permission.FOREGROUND_SERVICE,context.packageName))
    }
}
