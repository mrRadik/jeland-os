package ru.jeland.os.ui

import ru.jeland.os.localization.LocalizedIcon as Icon

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons

/** Independent destination; picker and screen share the same search, grid and repository. */
@Composable fun AppsScreen(s:UiState,model:MainViewModel) {
    var query by rememberSaveable {mutableStateOf("")}
    var favoritesOnly by rememberSaveable {mutableStateOf(false)}
    val state=s.apps
    val source=if(favoritesOnly)state.favoriteApps else state.installed
    val apps=remember(source,query) {source.filter {it.label.contains(query,true)||it.packageName.contains(query,true)}}
    Column(Modifier.fillMaxSize().testTag("screen-apps"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(58.dp).clip(Jeland.control).background(Brush.linearGradient(listOf(Jeland.colors.deep.copy(alpha=.4f),Jeland.colors.raised)))
                .border(1.dp,Jeland.colors.outline,Jeland.control),contentAlignment=Alignment.Center){Icon(JIcons.Apps,null,Modifier.size(30.dp),tint=Jeland.colors.accent)}
            Column(Modifier.weight(1f)) {
                SectionTitle("Приложения")
                Label("Установлено: ${state.installed.size} · избранное: ${state.favoriteApps.size}",small=true,muted=true)
            }
        }
        AppsSearch(query,{query=it},model::refreshApps)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control("Все · ${state.installed.size}",Modifier.weight(1f).testTag("apps-filter-all"),!favoritesOnly){favoritesOnly=false}
            Control("Избранное · ${state.favoriteApps.size}",Modifier.weight(1f).testTag("apps-filter-favorites"),favoritesOnly){favoritesOnly=true}
        }
        if(favoritesOnly)FavoriteOrderEditor(state,model)
        if(state.loading)Label("Обновляем список…",small=true,muted=true)
        state.error?.let {Label(it,small=true)}
        if(apps.isEmpty()&&!state.loading) {
            Panel(Modifier.fillMaxWidth().testTag("apps-empty")) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Label(if(query.isNotBlank())"Ничего не найдено"else if(favoritesOnly)"В избранном пока пусто"else"Нет доступных приложений")
                    Label(if(query.isNotBlank())"Измените запрос или очистите поиск."else if(favoritesOnly)"Выберите звезду на карточке в разделе «Все»."else"Отображаются только доступные launcher-приложения. Попробуйте обновить список.",small=true,muted=true)
                }
            }
        }
        AppsGrid(apps,state,model,Modifier.fillMaxWidth().weight(1f))
        Text(if(favoritesOnly)"Настоящие ярлыки устройства · стрелки меняют порядок избранного"else"Нажмите карточку для запуска · звезда добавляет в избранное",color=Jeland.colors.muted,fontSize=Jeland.type.footnote,modifier=Modifier.padding(bottom=8.dp))
    }
}
