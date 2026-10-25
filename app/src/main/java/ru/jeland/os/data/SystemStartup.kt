package ru.jeland.os.data

import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.MainActivity
import ru.jeland.os.core.SettingsStore

data class SystemStartupState(val enabled:Boolean=false,val permissionGranted:Boolean=false,val permissionScreenUnavailable:Boolean=false)
enum class BootStartupResult { IGNORED, DISABLED, NEEDS_PERMISSION, REQUESTED, FAILED }

/** Explicit opt-in to open our normal Activity at boot and after an observed wake.
 * Wake monitoring is wired separately; no overlay window, HOME replacement or vehicle SET.
 */
class SystemStartup(
    context:Context,private val store:SettingsStore,private val report:(String)->Unit={},
    private val permissionCheck:(Context)->Boolean=Settings::canDrawOverlays,
    private val wakeControl:(Boolean)->Unit={}
) {
    private val context=context.applicationContext
    private fun enabled()=store.string(ENABLED_KEY,"false").toBooleanStrictOrNull()?:false
    private fun permitted()=runCatching {permissionCheck(context)}.getOrDefault(false)
    private val mutableState=MutableStateFlow(SystemStartupState(enabled(),permitted()))
    val state=mutableState.asStateFlow()
    fun refresh() {
        val granted=permitted()
        mutableState.value=SystemStartupState(enabled(),granted,!granted&&state.value.permissionScreenUnavailable)
        runCatching {wakeControl(state.value.enabled&&granted)}.onFailure {report("STARTUP watcher_sync_failed ${it.javaClass.simpleName}")}
        report("STARTUP enabled=${state.value.enabled} permission=${state.value.permissionGranted} last=${store.string(LAST_BOOT_KEY,"none")}")
    }
    fun setEnabled(value:Boolean) {store.put(ENABLED_KEY,value.toString());refresh()}
    fun openPermissionSettings():Boolean {
        refresh()
        if(state.value.permissionGranted)return true
        // Some OEM builds have the common screen but not its package-scoped route.
        for(intent in listOf(permissionIntent(context.packageName),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            try {
                context.startActivity(intent)
                mutableState.value=state.value.copy(permissionScreenUnavailable=false)
                report("STARTUP user_open_permission_settings package_scoped=${intent.data!=null}")
                return true
            }catch(error:ActivityNotFoundException) {
                report("STARTUP permission_settings_missing package_scoped=${intent.data!=null}")
            }catch(error:Exception) {
                report("STARTUP permission_settings_failed ${error.javaClass.simpleName}")
                break
            }
        }
        mutableState.value=state.value.copy(permissionScreenUnavailable=true)
        report("STARTUP permission_setup_required use_START_A; only ru.jeland.os SYSTEM_ALERT_WINDOW")
        return false
    }

    fun onBoot(action:String?):BootStartupResult {
        if(action!=Intent.ACTION_BOOT_COMPLETED)return BootStartupResult.IGNORED
        return requestLaunch("system_boot",LAST_BOOT_KEY,"STARTUP_BOOT")
    }
    fun onWake():BootStartupResult=requestLaunch("system_wake",LAST_WAKE_KEY,"STARTUP_WAKE")
    private fun requestLaunch(reason:String,lastKey:String,tag:String):BootStartupResult {
        fun outcome(result:BootStartupResult,detail:String=""):BootStartupResult {
            val message="${System.currentTimeMillis()} $result $detail".trim()
            store.put(lastKey,message)
            report("$tag $message")
            return result
        }
        if(!enabled())return outcome(BootStartupResult.DISABLED)
        if(!permitted())return outcome(BootStartupResult.NEEDS_PERMISSION)
        return try {
            context.startActivity(launchIntent(context,reason))
            // Acceptance of startActivity is a request, not proof of OEM focus/visibility.
            outcome(BootStartupResult.REQUESTED)
        }catch(error:Exception){outcome(BootStartupResult.FAILED,error.javaClass.simpleName)}
    }
    companion object {
        const val ENABLED_KEY="system.autostart"
        const val LAST_BOOT_KEY="system.autostart.last_boot"
        const val LAST_WAKE_KEY="system.autostart.last_wake"
        const val WAKE_PENDING_KEY="system.autostart.wake_pending"
        const val WATCH_STATUS_KEY="system.autostart.watcher_status"
        fun permissionIntent(packageName:String)=Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fun launchIntent(context:Context,reason:String="system_boot")=Intent.makeMainActivity(ComponentName(context,MainActivity::class.java))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("ru.jeland.os.launch_reason",reason)
    }
}

/** System-only manifest entry point. It never initializes the app's vehicle graph. */
class SystemBootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent?) {
        if(intent?.action !in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED))return
        try {
            val store=AndroidSettingsStore(context)
            if(intent?.action==Intent.ACTION_BOOT_COMPLETED)store.put(SystemStartup.WAKE_PENDING_KEY,"false")
            val startup=SystemStartup(context,store,{Log.i("JelandOSStartup",it)},wakeControl={WakeStartupService.sync(context,it)})
            startup.refresh()
            startup.onBoot(intent?.action) // Package update restarts monitoring, never opens the Activity.
        }catch(error:Exception){Log.e("JelandOSStartup","Startup handling failed",error)}
    }
}
