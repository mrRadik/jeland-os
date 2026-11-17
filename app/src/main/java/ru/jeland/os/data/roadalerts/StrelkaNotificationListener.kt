package ru.jeland.os.data.roadalerts

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ru.jeland.os.core.StrelkaNoticePolicy

/** Bound only by Android after explicit notification-access consent. No overlay/accessibility scraping. */
class StrelkaNotificationListener:NotificationListenerService() {
    private val endpoint get()=RoadAlertsRuntime.get(this)
    override fun onListenerConnected(){super.onListenerConnected();endpoint.attached(this);sync()}
    override fun onListenerDisconnected(){endpoint.detached(this);super.onListenerDisconnected()}
    override fun onDestroy(){endpoint.detached(this);super.onDestroy()}
    internal fun sync() {
        if(!endpoint.state.value.enabled)return
        runCatching {activeNotifications.orEmpty().filter {it.packageName==StrelkaNoticePolicy.PACKAGE}.forEach {endpoint.posted(it,replay=true)}}
    }
    override fun onNotificationPosted(sbn:StatusBarNotification?){
        if(sbn?.packageName==StrelkaNoticePolicy.PACKAGE)endpoint.posted(sbn)
    }
    override fun onNotificationRemoved(sbn:StatusBarNotification?){
        if(sbn?.packageName==StrelkaNoticePolicy.PACKAGE)endpoint.removed(sbn)
    }
}
