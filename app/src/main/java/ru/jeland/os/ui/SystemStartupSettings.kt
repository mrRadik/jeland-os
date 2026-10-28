package ru.jeland.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import ru.jeland.os.data.SystemStartupState
import ru.jeland.os.ui.theme.Jeland

@Composable fun SystemStartupSettings(state:SystemStartupState,onEnabled:(Boolean)->Unit,onPermission:()->Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
            SectionTitle("Запуск приложения")
            SettingsToggle("Запускать вместе с системой","Открывать после загрузки и пробуждения ГУ",state.enabled,
                Modifier.testTag("setting-system-startup")){onEnabled(!state.enabled)}
            if(state.enabled&&!state.permissionGranted) {
                Label(if(state.permissionScreenUnavailable)
                    "Для автозапуска выдайте разрешение с помощью установщика на компьютере. Затем вернитесь сюда."
                    else "Автозапуску требуется разрешение. Если системный экран недоступен, используйте установщик на компьютере.",small=true,muted=true)
                Control(if(state.permissionScreenUnavailable)"Проверить разрешение"else"Разрешение для автозапуска",
                    Modifier.fillMaxWidth().testTag("startup-permission"),onClick=onPermission)
            }else if(state.enabled) {
                Label("Автозапуск включён",small=true,muted=true,modifier=Modifier.testTag("startup-ready"))
            }
            Label("Ожидание выхода из сна использует тихое служебное уведомление. Экран и процессор не удерживаются включёнными.",small=true,muted=true)
        }
    }
}
