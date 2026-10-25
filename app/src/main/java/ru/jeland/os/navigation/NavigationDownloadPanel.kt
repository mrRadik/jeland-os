package ru.jeland.os.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.Jeland

@Composable internal fun NavigationDownloadPanel(repository:OfflineNavigationRepository,onOverview:()->Unit) {
    val download by repository.downloads.state.collectAsStateWithLifecycle()
    val installing by repository.importing.collectAsStateWithLifecycle()
    var address by rememberSaveable {mutableStateOf("")}
    var checksum by rememberSaveable {mutableStateOf("")}
    var kind by rememberSaveable {mutableStateOf("region")}
    var allowMetered by rememberSaveable {mutableStateOf(false)}
    var trust by rememberSaveable {mutableStateOf(false)}
    var error by remember {mutableStateOf("")}
    Label("Обзор дорог России · установлена")
    Label("Города, крупные дороги и водоёмы. Работает без сети. Для адресов и маршрутов нужен подробный регион с дорожным графом.",small=true,muted=true)
    Control("Показать обзор России",Modifier.fillMaxWidth().testTag("navigation-russia-overview"),onClick=onOverview)
    HorizontalDivider(color=Jeland.colors.outline)
    Label("Скачать карту по ссылке")
    Label("Готового каталога регионов Jeland пока нет. Нужна прямая ссылка на подготовленный пакет — не страница сайта и не исходный .osm.pbf.",small=true,muted=true)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Control("Регион Jeland",Modifier.weight(1f),selected=kind=="region",enabled=!download.running){kind="region"}
        Control("Подложка Protomaps",Modifier.weight(1f),selected=kind=="overview",enabled=!download.running){kind="overview"}
    }
    Label(if(kind=="region")"Формат .jelandnav.zip: карта + поиск + совместимый граф Valhalla. Установка региона остановит текущий маршрут."
        else"Формат .pmtiles, схема Protomaps v4. Только отрисовка карты, без графа маршрутов. Произвольные PMTiles других схем не подходят.",small=true,muted=true)
    OutlinedTextField(address,{address=it.take(2048)},label={Text("Прямая HTTPS-ссылка")},singleLine=true,enabled=!download.running,
        colors=navigationFieldColors(),modifier=Modifier.fillMaxWidth().testTag("map-download-url"))
    OutlinedTextField(checksum,{checksum=it.take(64)},label={Text("SHA-256 автора, если опубликован")},singleLine=true,enabled=!download.running,
        colors=navigationFieldColors(),modifier=Modifier.fillMaxWidth().testTag("map-download-sha256"))
    SettingsToggle("Разрешить мобильную сеть","По умолчанию — только сеть без тарификации. Роуминг запрещён. Лимит пакета 512 МБ.",allowMetered){allowMetered=!allowMetered}
    SettingsToggle("Источник мне известен","HTTPS и контрольная сумма не подтверждают автора. Не устанавливайте карты из непроверенных источников.",trust){trust=!trust}
    if(download.status.isNotBlank()) {
        Label(download.status,small=true)
        if(download.total>0) {
            Label("${download.received/1048576} / ${download.total/1048576} МБ",small=true,muted=true)
            LinearProgressIndicator(progress={(download.received.toFloat()/download.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Jeland.colors.accent)
        }
    }
    if(download.running)Control("Отменить загрузку",Modifier.fillMaxWidth(),onClick=repository.downloads::cancel)
    else Control("Скачать",Modifier.fillMaxWidth().testTag("map-download-start"),selected=true,enabled=address.isNotBlank()&&trust&&!installing) {
        error=""
        try {repository.downloads.start(address,kind,checksum,allowMetered)}catch(e:Exception){error=e.message?:"Не удалось начать загрузку"}
    }
    if(download.ready!=null)Control(if(installing)"Установка…"else"Установить загруженную карту",Modifier.fillMaxWidth().testTag("map-download-install"),selected=true,enabled=!installing,onClick=repository::installDownloaded)
    if(error.isNotBlank())Label(error,small=true)
    Label("Загрузку ведёт системный менеджер Android, она сохраняется при закрытии приложения. После скачивания нажмите «Установить»; текущая карта не заменяется при ошибке проверки.",small=true,muted=true)
}
