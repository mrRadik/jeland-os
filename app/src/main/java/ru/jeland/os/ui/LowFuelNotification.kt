package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.FuelState
import ru.jeland.os.ui.theme.*

@Composable fun LowFuelNotification(fuel:FuelState) {
    val c=Jeland.colors
    AnimatedVisibility(fuel.low,enter=fadeIn(),exit=fadeOut()) {
        Row(Modifier.widthIn(max=420.dp).clip(Jeland.control).background(c.panel.copy(alpha=.98f))
            .border(1.5.dp,c.warm,Jeland.control).padding(16.dp).testTag("low-fuel-warning")
            .semantics {liveRegion=LiveRegionMode.Polite},verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Icon(JIcons.Fuel,null,Modifier.size(32.dp),tint=c.warm)
            Column {
                Text("Низкий уровень топлива",color=c.warm,fontSize=Jeland.type.secondary)
                Text("Осталось менее 10%. Заправьте автомобиль.",color=c.text,fontSize=Jeland.type.caption)
            }
        }
    }
}
