package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import ru.jeland.os.core.ClimateState
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons
import kotlin.math.roundToInt

internal fun climateDockTemperature(s:ClimateState,driver:Boolean):String {
    val raw=if(driver)s.driverRaw else s.passengerRaw
    val value=if(driver)s.driver else s.passenger
    return when {
        !s.known(if(driver)10 else 13)->"—"
        s.available!=null&&raw==0->"LO"
        s.available!=null&&raw==16->"HI"
        !value.isFinite()->"—"
        else->value.roundToInt().toString()
    }
}
internal fun fanCycleMillis(level:Int)=2600-level.coerceIn(1,10)*160

@Composable internal fun fanRotation(running:Boolean,level:Int):Float {
    if(!running)return 0f
    val transition=rememberInfiniteTransition(label="hvac-fan")
    val angle by transition.animateFloat(0f,360f,infiniteRepeatable(tween(fanCycleMillis(level),easing=LinearEasing),RepeatMode.Restart),label="hvac-fan-angle")
    return angle
}

@Composable fun ClimateFan(s:ClimateState,modifier:Modifier=Modifier) {
    val running=s.controlsEnabled&&s.known(12)&&s.fan>0
    val rotation=fanRotation(running,s.fan)
    Icon(JIcons.Fan,null,modifier.semantics {stateDescription=if(running)"Вращается"else"Остановлен"}
        .graphicsLayer {rotationZ=rotation},tint=if(running)Jeland.colors.accent else Jeland.colors.unknown)
}
