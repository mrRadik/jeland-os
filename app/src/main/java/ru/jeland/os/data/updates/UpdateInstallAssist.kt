package ru.jeland.os.data.updates

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.jeland.os.JelandApplication
import ru.jeland.os.core.SettingsStore
import ru.jeland.os.core.UpdateInstallClickGate

data class UpdateInstallAssistState(val enabled:Boolean=false,val available:Boolean=false,val message:String="")

class UpdateInstallAssist(context:Context,private val store:SettingsStore,private val report:(String)->Unit) {
    private val app=context.applicationContext
    private val gate=UpdateInstallClickGate()
    private val mutable=MutableStateFlow(UpdateInstallAssistState())
    val state=mutable.asStateFlow()
    init {refresh()}
    fun refresh() {
        val component=ComponentName(app,UpdateInstallAssistService::class.java)
        val available=runCatching {app.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {ComponentName.unflattenFromString(it.id)==component}}.getOrDefault(false)
        // Granting this service from the PC is also an explicit opt-in. A saved Off choice wins.
        mutable.value=mutable.value.copy(enabled=store.string(KEY,"").toBooleanStrictOrNull()?:available,available=available)
    }
    fun enabled(value:Boolean) {store.put(KEY,value.toString());if(!value)cancel();refresh()}
    fun openPermission() {mutable.value=mutable.value.copy(message="Подключите ГУ к компьютеру по USB и запустите enable-updates.cmd из установщика Jeland OS.")}
    /** Called only after APK size, SHA-256, identity and signing checks pass. */
    fun arm() {
        cancel();refresh()
        if(!state.value.enabled||!state.value.available)return
        gate.arm(app.packageManager.getApplicationLabel(app.applicationInfo).toString(),SystemClock.elapsedRealtime())
        report("UPDATE_INSTALL_ASSIST_ARMED timeout=180s")
    }
    fun cancel(){gate.clear()}
    internal fun pending()=gate.pendingLabel(SystemClock.elapsedRealtime())!=null
    internal fun tryClick(root:AccessibilityNodeInfo) {
        val now=SystemClock.elapsedRealtime()
        val label=gate.pendingLabel(now)?:return
        val pkg=root.packageName?.toString().orEmpty()
        if(pkg !in UpdateInstallClickGate.INSTALLERS)return
        val titles=root.findAccessibilityNodeInfosByText(label)
        val titleMatches=try {titles.any {it.text?.toString()?.trim()==label}}
            finally {titles.forEach {@Suppress("DEPRECATION") it.recycle()}}
        if(!titleMatches)return
        val buttons=root.findAccessibilityNodeInfosByViewId("android:id/button1")
        try {for(node in buttons) {
            if(gate.claim(pkg,titleMatches,node.viewIdResourceName.orEmpty(),node.text?.toString().orEmpty(),
                    node.isEnabled,node.isVisibleToUser,node.isClickable,now)) {
                // Accessibility ACTION_CLICK, not a screen-coordinate tap or a change to enabled state.
                val accepted=runCatching {node.performAction(AccessibilityNodeInfo.ACTION_CLICK)}.getOrDefault(false)
                report("UPDATE_INSTALL_ASSIST_CLICK accepted=$accepted package=$pkg")
                return
            }
        }}finally {buttons.forEach {@Suppress("DEPRECATION") it.recycle()}}
    }
    companion object {private const val KEY="updates.install_click_assist"}
}

/** Android binds this only after the user enables it in system accessibility settings. */
class UpdateInstallAssistService:AccessibilityService() {
    private val assist get()=(application as JelandApplication).container.updates.installAssist
    override fun onServiceConnected(){super.onServiceConnected();assist.refresh()}
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(!assist.pending()||event?.packageName?.toString() !in UpdateInstallClickGate.INSTALLERS)return
        val root=rootInActiveWindow?:return
        try {assist.tryClick(root)}catch(_:RuntimeException) {}finally {@Suppress("DEPRECATION") root.recycle()}
    }
    override fun onInterrupt(){assist.cancel()}
    override fun onUnbind(intent:Intent?):Boolean {assist.cancel();return super.onUnbind(intent)}
}
