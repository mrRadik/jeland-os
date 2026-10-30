package ru.jeland.os.navigation

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.jeland.os.core.Maneuver
import ru.jeland.os.navigation.comaps.CoMapsScreen
import ru.jeland.os.navigation.comaps.CoMapsWidget
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons

@Composable fun FullNavigationScreen(repository:OfflineNavigationRepository,dark:Boolean)=CoMapsScreen(repository,dark)

@Composable fun OfflineNavigationWidget(repository:OfflineNavigationRepository,dark:Boolean,modifier:Modifier,framed:Boolean=true,onClick:()->Unit)=
    CoMapsWidget(repository,dark,modifier,framed,onClick)

@Composable internal fun navigationFieldColors()=OutlinedTextFieldDefaults.colors(
    focusedTextColor=Jeland.colors.text,unfocusedTextColor=Jeland.colors.text,cursorColor=Jeland.colors.accent,
    focusedBorderColor=Jeland.colors.accent,unfocusedBorderColor=Jeland.colors.outline,
    focusedLabelColor=Jeland.colors.accent,unfocusedLabelColor=Jeland.colors.muted)

@Composable internal fun navigationManeuverIcon(state:ru.jeland.os.core.NavigationState)=when {
    state.instruction.startsWith("Вы прибыли")||state.instruction=="Место назначения"->JIcons.Destination
    state.instruction.contains("разверн",ignoreCase=true)->JIcons.UTurn
    state.instruction.contains("кругов",ignoreCase=true)||state.instruction.contains("кольц",ignoreCase=true)->JIcons.Roundabout
    state.maneuver==Maneuver.LEFT->JIcons.TurnLeft
    state.maneuver==Maneuver.RIGHT->JIcons.TurnRight
    else->JIcons.Straight
}
