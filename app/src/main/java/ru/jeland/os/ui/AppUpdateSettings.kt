package ru.jeland.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.BuildConfig
import ru.jeland.os.data.updates.AppUpdateManager
import ru.jeland.os.ui.theme.Jeland
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun AppUpdateSettings(manager:AppUpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    var confirmMobile by remember {mutableStateOf(false)}
    var confirmInstall by remember {mutableStateOf(false)}
    var installWithSession by remember {mutableStateOf(false)}
    var confirmCleanup by remember {mutableStateOf(false)}
    val busy=state.checking||state.downloading||state.installing||state.cleaningApks
    val candidate=state.available
    Panel(Modifier.fillMaxWidth().testTag("app-updates")) {
        Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
            SectionTitle("Обновление Jeland OS","Установлена ${BuildConfig.VERSION_NAME}")
            SettingsToggle("Проверять обновления автоматически","GitHub Releases · не чаще одного раза в 6 часов при открытом приложении",state.autoCheck,
                Modifier.testTag("updates-auto-check")){manager.autoCheck(!state.autoCheck)}
            SettingsToggle("Автоматически скачивать по Wi-Fi","Только сеть без ограничения трафика. Установка — после вашего подтверждения",state.autoDownloadWifi,
                Modifier.testTag("updates-auto-download")){manager.autoDownload(!state.autoDownloadWifi)}
            Label(state.message,Modifier.testTag("updates-status"),small=true)
            state.lastChecked?.let {at->Label("Последняя успешная проверка: "+DateTimeFormatter.ofPattern("d MMM, HH:mm",Locale.forLanguageTag("ru"))
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at)),small=true,muted=true)}
            Control(if(state.checking)"Проверяем…"else"Проверить обновления",Modifier.fillMaxWidth().testTag("updates-check"),enabled=!busy){manager.check()}
            candidate?.let {update->
                Label("${update.manifest.versionName} · ${String.format(Locale.forLanguageTag("ru"),"%.1f",update.manifest.size/1048576.0)} МБ",small=true)
                when {
                    state.downloading->{
                        Label("${state.progress}% · ${state.message}",small=true,muted=true)
                        Control("Остановить",Modifier.fillMaxWidth().testTag("updates-cancel-download")){manager.cancelDownload()}
                    }
                    state.downloaded->{
                        Control(if(state.installing)"Готовим установку…"else"Установить обновление",Modifier.fillMaxWidth().testTag("updates-install"),selected=true,enabled=!busy){installWithSession=false;confirmInstall=true}
                        if(state.installFallback)Control("Попробовать другой способ установки",Modifier.fillMaxWidth().testTag("updates-install-fallback"),enabled=!busy){installWithSession=true;confirmInstall=true}
                        Control("Скачать заново по Wi-Fi",Modifier.fillMaxWidth().testTag("updates-download-again"),enabled=!busy){manager.download()}
                    }
                    else->{
                        Control("Скачать по Wi-Fi",Modifier.fillMaxWidth().testTag("updates-download"),selected=true,enabled=!busy){manager.download()}
                        Control("Скачать через любую сеть",Modifier.fillMaxWidth().testTag("updates-download-mobile"),enabled=!busy){confirmMobile=true}
                    }
                }
            }
            Control(if(state.cleaningApks)"Очищаем…"else"Удалить скачанные APK",Modifier.fillMaxWidth().testTag("updates-clear-apks"),enabled=!busy){confirmCleanup=true}
            Label("Источник: mrRadik/jeland-os · проверка размера, SHA-256, пакета и подписи APK. Данные приложения сохраняются. Устанавливайте только на стоянке.",small=true,muted=true)
        }
    }
    if(confirmCleanup)Dialog(onDismissRequest={confirmCleanup=false}) {
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
            SectionTitle("Удалить скачанные APK?","Будут удалены пакеты обновления Jeland OS и незавершённая загрузка. Неактивная попытка обновления отменится. Установленные приложения, карты и настройки сохранятся.")
            Control("Отмена",Modifier.fillMaxWidth()){confirmCleanup=false}
            Control("Удалить APK",Modifier.fillMaxWidth().testTag("updates-confirm-clear-apks"),enabled=!busy){confirmCleanup=false;manager.clearDownloadedApks()}
        }}
    }
    if(confirmMobile&&candidate!=null)Dialog(onDismissRequest={confirmMobile=false}) {
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
            SectionTitle("Использовать мобильный интернет?","Будет скачано ${String.format(Locale.forLanguageTag("ru"),"%.1f",candidate.manifest.size/1048576.0)} МБ. Возможны расходы по тарифу.")
            Control("Отмена",Modifier.fillMaxWidth()){confirmMobile=false}
            Control("Скачать",Modifier.fillMaxWidth(),selected=true){confirmMobile=false;manager.download(allowMetered=true)}
        }}
    }
    if(confirmInstall)Dialog(onDismissRequest={confirmInstall=false}) {
        Panel(Modifier.fillMaxWidth()) {Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
            SectionTitle("Обновить приложение на стоянке?","Убедитесь, что автомобиль припаркован. Откроется системный установщик; Jeland OS может закрыться. Это обновление приложения, не прошивки автомобиля.")
            Control("Отмена",Modifier.fillMaxWidth()){confirmInstall=false}
            Control("Автомобиль припаркован — установить",Modifier.fillMaxWidth(),selected=true){confirmInstall=false;manager.install(useSession=installWithSession)}
        }}
    }
}
