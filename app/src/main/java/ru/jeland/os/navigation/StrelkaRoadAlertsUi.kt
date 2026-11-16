package ru.jeland.os.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.jeland.os.data.roadalerts.StrelkaRoadAlerts
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.localization.localized
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*

@Composable internal fun rememberStrelkaRoadAlert(repository:StrelkaRoadAlerts):ru.jeland.os.core.RoadAlert? {
    val state by repository.state.collectAsStateWithLifecycle()
    val alert=state.alert?.takeIf {state.enabled&&state.permission&&state.connected}?:return null
    LaunchedEffect(alert.key,alert.postedAt){while(true){delay(1000);repository.expire()}}
    return alert
}

@Composable fun StrelkaRoadAlertCard(repository:StrelkaRoadAlerts,modifier:Modifier=Modifier,compact:Boolean=false) {
    val alert=rememberStrelkaRoadAlert(repository)?:return
    val c=Jeland.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.panel.copy(alpha=.97f))
        .clickable(onClick=repository::openApp).padding(if(compact)10.dp else 14.dp).testTag("strelka-road-alert"),
        horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(JIcons.SpeedCamera,null,Modifier.size(if(compact)28.dp else 36.dp),tint=c.accent)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(alert.title,color=c.text,fontSize=if(compact)Jeland.type.body else Jeland.type.title,maxLines=if(compact)1 else 2,overflow=TextOverflow.Ellipsis)
            if(!compact&&alert.detail.isNotBlank())Text(alert.detail,color=c.muted,fontSize=Jeland.type.footnote,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        if(!compact)Text("Стрелка",color=c.accent,fontSize=Jeland.type.footnote)
    }
}

@Composable fun StrelkaRoadAlertsSettings(repository:StrelkaRoadAlerts) {
    val state by repository.state.collectAsStateWithLifecycle()
    val c=Jeland.colors
    var help by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().testTag("strelka-settings"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionTitle("Камеры и дорожные опасности")
        SettingsToggle("Предупреждения Стрелки","Предупреждения в навигации, виджете карты и на приборной панели",state.enabled,Modifier.testTag("strelka-enabled")){repository.setEnabled(!state.enabled)}
        if(state.enabled) {
            val status=when {
                !state.installed->"Установите и настройте Стрелку"
                !state.permission->"Нужен доступ к предупреждениям"
                !state.connected->"Ожидаем подключения к уведомлениям"
                state.running->"Сервис Стрелки запущен"
                else->"Стрелка не запущена"
            }
            Text(status,color=c.muted,fontSize=Jeland.type.body)
            if(state.installed) {
                SettingsToggle("Доступ к предупреждениям","Android разрешает доступ ко всем уведомлениям. Jeland OS обрабатывает только уведомления Стрелки.",state.permission,Modifier.testTag("strelka-permission")){repository.openPermission()}
                SettingsToggle("Запускать со Jeland OS","Внешний запуск зависит от лицензии и настроек Стрелки",state.autoStart,Modifier.testTag("strelka-auto-start")){repository.setAutoStart(!state.autoStart)}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Control("Запустить",Modifier.weight(1f).testTag("strelka-start"),onClick=repository::start)
                    Control("Открыть Стрелку",Modifier.weight(1f),onClick=repository::openApp)
                }
            }else Control("Скачать Стрелку",Modifier.fillMaxWidth(),onClick=repository::download)
            Text("База и звук настраиваются в Стрелке. Дата её базы недоступна Jeland OS.",color=c.muted,fontSize=Jeland.type.footnote)
            Control(if(help)"Скрыть инструкцию"else"Как настроить",Modifier.fillMaxWidth().testTag("strelka-help")){help=!help}
            if(help)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("1. Установите обычную Стрелку и предоставьте ей GPS.",color=c.text,fontSize=Jeland.type.footnote)
                Text("2. Обновите базу и включите её автоматическое обновление каждые 24 часа.",color=c.text,fontSize=Jeland.type.footnote)
                Text("3. В Стрелке: Опции → Дополнительно → Вывод уведомлений → включить и применить.",color=c.text,fontSize=Jeland.type.footnote)
                Text("4. Разрешите Jeland OS доступ к предупреждениям. Если системный экран недоступен, выполните setup-strelka.cmd на компьютере.",color=c.text,fontSize=Jeland.type.footnote)
            }
            if(state.message.isNotBlank())Text(localized(state.message),color=c.muted,fontSize=Jeland.type.footnote)
        }
    }
}
