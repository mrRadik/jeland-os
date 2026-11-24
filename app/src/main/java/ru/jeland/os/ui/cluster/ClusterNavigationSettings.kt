package ru.jeland.os.ui.cluster

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.ClusterNavigationMode
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.ui.Control
import ru.jeland.os.ui.theme.Jeland

@Composable internal fun ClusterNavigationSettings(selected:ClusterNavigationMode,onSelect:(ClusterNavigationMode)->Unit) {
    Column(Modifier.fillMaxWidth().testTag("cluster-navigation-settings"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Режим отображения навигации",color=Jeland.colors.text,fontSize=Jeland.type.body)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ClusterNavigationMode.entries.forEach {mode->
                Control(mode.title,Modifier.weight(1f).testTag("cluster-navigation-${mode.name.lowercase()}"),selected=selected==mode) {
                    onSelect(mode)
                }
            }
        }
        Text(if(selected==ClusterNavigationMode.MAP)"Карта видна постоянно, подсказки появляются при активном маршруте."
            else "Подсказки появляются только при активном маршруте.",color=Jeland.colors.muted,fontSize=Jeland.type.footnote)
    }
}
