package ru.jeland.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons

private const val SUPPORT_PROJECT_URL="https://t.tb.ru/pm_short/9OkQ4RGMfML"

@Composable fun SupportProjectSettings() {
    val uriHandler=LocalUriHandler.current
    var unavailable by remember {mutableStateOf(false)}
    Panel(Modifier.fillMaxWidth().testTag("support-project")) {
        Column(Modifier.padding(Jeland.space.large),verticalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(Jeland.space.regular)) {
                Icon(JIcons.FavoriteBorder,null,Modifier.size(36.dp),tint=Jeland.colors.accent)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(Jeland.space.pair)) {
                    SectionTitle("Поддержать Jeland OS")
                    Label("Добровольная благодарность автору. Все функции приложения доступны бесплатно.",small=true,muted=true)
                }
            }
            Control("Поблагодарить разработчика",Modifier.fillMaxWidth().testTag("support-project-open"),selected=true) {
                unavailable=false
                try {uriHandler.openUri(SUPPORT_PROJECT_URL)}
                catch(_:android.content.ActivityNotFoundException){unavailable=true}
                catch(_:SecurityException){unavailable=true}
                catch(_:IllegalArgumentException){unavailable=true}
            }
            if(unavailable)Label("Не удалось открыть страницу. Ссылка для другого устройства: $SUPPORT_PROJECT_URL",
                Modifier.testTag("support-project-unavailable"),small=true,muted=true)
        }
    }
}
