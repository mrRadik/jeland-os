package ru.jeland.os.data.cluster

import android.content.Context
import android.content.Intent

/** The proven standalone APK's wire sequence, including removal of Lion's independent map. */
internal class OemClusterRoute(private val app:Context,private val report:(String)->Unit):ClusterRoute {
    private val vignette by lazy {CarLanClient(app,report)}
    private val navigation by lazy {CarLanClient(app,report)}
    override fun resumeAfterSleep() {
        vignette.resumeAfterSleep()
        navigation.resumeAfterSleep()
    }
    override fun open(afterArea:()->Unit) {
        vignette.setWindowVisible(false)
        ping()
        broadcast(Intent("turbodog.navigation.system.message").putExtra("CODE",203))
        broadcast(Intent("AUTONAVI_STANDARD_BROADCAST_SEND").putExtra("KEY_TYPE",10019).putExtra("EXTRA_STATE",12))
        closeStockMap("before-launch")
        navigation.displayAreaOpen(10,afterArea)
    }
    override fun restore() {restore {}}
    override fun restore(completed:(Boolean)->Unit) {
        navigation.releaseCluster {released->
            // Always attempt to recover the native window, even when a Binder request fails.
            vignette.setWindowVisible(true) {visible->
                report("CLUSTER_STOCK_RESTORE_RESULT released=$released visible=$visible")
                completed(released&&visible)
            }
        }
    }
    private fun ping() {
        broadcast(Intent("turbodog.navigation.system.message").putExtra("CODE",206))
        broadcast(Intent("turbodog.navigation.system.message").putExtra("CODE",207))
        broadcast(Intent("AUTONAVI_STANDARD_BROADCAST_SEND").putExtra("KEY_TYPE",10019).putExtra("EXTRA_STATE",3))
        broadcast(Intent("lion.navigation.system.message").putExtra("CODE",207))
    }
    override fun closeStockMap(phase:String) {
        broadcast(Intent("LIONNAVI_STANDARD_BROADCAST_RECV")
            .setClassName("com.liontech.autonavi","com.liontech.autonavi.adapter.LionBroadcastReceiver")
            .putExtra("KEY_TYPE",10116).putExtra("EXTSCREEN_OPERATE_TYPE",1)
            .putExtra("EXTSCREEN_WINDOW_INFO",1002).putExtra("EXTSCREEN_MODE_INFO",0))
        report("CLUSTER_STOCK_MAP_CLOSE_SENT phase=$phase window=1002")
    }
    private fun broadcast(intent:Intent) {
        runCatching {app.sendBroadcast(intent)}.onFailure {report("CLUSTER_NAV_BROADCAST_ERROR ${intent.action} $it")}
    }
}
