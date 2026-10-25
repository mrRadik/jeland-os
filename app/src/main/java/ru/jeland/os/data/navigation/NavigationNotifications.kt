package ru.jeland.os.data.navigation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ru.jeland.os.JelandApplication
import ru.jeland.os.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory only. Other applications are rejected before their extras are read. */
object NavigationNotices {
    private val reducer=NavigationNoticeReducer()
    private val flow=MutableStateFlow(0L)
    val changes=flow.asStateFlow()
    var connected=false;private set
    fun connection(value:Boolean) {connected=value;reducer.clear();flow.value++}
    fun current(provider:NavigationProvider?)=reducer.current(provider)
    fun posted(sbn:StatusBarNotification):Boolean {
        if(NavigationProvider.fromPackage(sbn.packageName)==null)return false
        val n=sbn.notification
        val extras=n.extras
        val accepted=reducer.posted(sbn.key,sbn.packageName,n.category,sbn.isOngoing,
            n.flags and Notification.FLAG_GROUP_SUMMARY!=0,
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?:extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),android.os.SystemClock.elapsedRealtime())
        flow.value++;return accepted
    }
    fun removed(key:String) {reducer.removed(key);flow.value++}
}

class NavigationNotificationListener:NotificationListenerService() {
    private fun report(value:String) {(application as JelandApplication).container.car.recordIntegration(value)}
    override fun onListenerConnected() {
        NavigationNotices.connection(true)
        runCatching {activeNotifications?.forEach(::receive)}.onFailure {report("NAV_NOTICE snapshot_failed ${it.javaClass.simpleName}")}
        report("NAV_NOTICE connected")
    }
    override fun onListenerDisconnected() {NavigationNotices.connection(false);report("NAV_NOTICE disconnected")}
    override fun onNotificationPosted(sbn:StatusBarNotification) {receive(sbn)}
    private fun receive(sbn:StatusBarNotification) {
        val provider=NavigationProvider.fromPackage(sbn.packageName)?:return
        runCatching {NavigationNotices.posted(sbn)}.onSuccess {accepted->
            // No destination, coordinates, notification text or unrelated app data in diagnostics.
            report("NAV_NOTICE provider=${provider.name} accepted=$accepted category=${sbn.notification.category}")
        }.onFailure {NavigationNotices.removed(sbn.key);report("NAV_NOTICE parse_failed ${it.javaClass.simpleName}")}
    }
    override fun onNotificationRemoved(sbn:StatusBarNotification) {
        if(NavigationProvider.fromPackage(sbn.packageName)!=null)NavigationNotices.removed(sbn.key)
    }
    override fun onDestroy() {NavigationNotices.connection(false);super.onDestroy()}
}
