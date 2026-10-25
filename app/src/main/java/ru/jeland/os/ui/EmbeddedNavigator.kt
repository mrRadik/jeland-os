package ru.jeland.os.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ru.jeland.os.core.NavigationProvider
import ru.jeland.os.navhost.NavHostConnection
import ru.jeland.os.ui.theme.*

@Composable fun EmbeddedNavigator(provider:NavigationProvider,modifier:Modifier=Modifier,visible:Boolean=true) {
    val connection by NavHostConnection.state.collectAsState()
    Column(modifier.testTag("embedded-navigation"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Control("Назад в карте",Modifier.weight(1f),enabled=connection.attached,onClick=NavHostConnection::back)
            Control("Открыть заново",Modifier.weight(1f),enabled=connection.connected,onClick=NavHostConnection::reopen)
        }
        key(provider,connection.connected) {
            AndroidView(factory={context->
                SurfaceView(context).apply {
                    setZOrderOnTop(true)
                    val token=Any()
                    holder.addCallback(object:SurfaceHolder.Callback {
                        override fun surfaceCreated(holder:SurfaceHolder) {}
                        override fun surfaceChanged(holder:SurfaceHolder,format:Int,width:Int,height:Int) {
                            if(connection.connected)NavHostConnection.attach(token,provider,holder.surface,width,height,resources.displayMetrics.densityDpi)
                        }
                        override fun surfaceDestroyed(holder:SurfaceHolder) {NavHostConnection.detach(token)}
                    })
                    setOnTouchListener {_,event->parent?.requestDisallowInterceptTouchEvent(true);NavHostConnection.touch(event);true}
                }
            },update={it.visibility=if(visible)View.VISIBLE else View.INVISIBLE},modifier=Modifier.fillMaxWidth().weight(1f).testTag("navigation-map-surface"))
        }
        connection.error?.let {Label(it,small=true,muted=true)}
    }
}
