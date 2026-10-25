package ru.jeland.os.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

internal val Popup.isTransientControl:Boolean get()=this in setOf(Popup.CLIMATE,Popup.SHADE,Popup.DRIVER,Popup.PASSENGER,
    Popup.DRIVER_TEMPERATURE,Popup.PASSENGER_TEMPERATURE)

/** Observe, never consume gestures. Holding/dragging and paused activities suspend dismissal. */
@Composable internal fun controlPanelInactivity(key:Any,onClose:()->Unit):Modifier {
    var interaction by remember(key){mutableLongStateOf(0L)}
    var pressed by remember(key){mutableStateOf(false)}
    val close by rememberUpdatedState(onClose)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed=lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val timeout=LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis=4000L,containsIcons=true,containsText=true,containsControls=true)?:4000L
    LaunchedEffect(key,interaction,pressed,resumed,timeout) {
        if(resumed&&!pressed&&timeout<Long.MAX_VALUE){delay(timeout);close()}
    }
    return Modifier.onPreviewKeyEvent {interaction++;false}.pointerInput(key,resumed) {
        try {
            awaitPointerEventScope {
                while(true) {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    interaction++;pressed=event.changes.any {it.pressed}
                }
            }
        }finally {pressed=false}
    }
}
