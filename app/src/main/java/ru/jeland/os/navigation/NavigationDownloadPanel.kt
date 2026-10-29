package ru.jeland.os.navigation

import androidx.compose.runtime.Composable
import ru.jeland.os.navigation.comaps.CoMapsInitialSettings

@Composable fun NavigationMapSettings(repository:OfflineNavigationRepository)=CoMapsInitialSettings(repository)
