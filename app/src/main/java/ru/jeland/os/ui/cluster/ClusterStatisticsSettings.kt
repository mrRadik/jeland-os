package ru.jeland.os.ui.cluster

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.ClusterTripPeriod
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.ui.Control
import ru.jeland.os.ui.theme.Jeland

@Composable internal fun ClusterStatisticsSettings(selected:ClusterTripPeriod,onSelect:(ClusterTripPeriod)->Unit) {
    Column(Modifier.fillMaxWidth().testTag("cluster-statistics-settings"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Статистика поездки",color=Jeland.colors.text,fontSize=Jeland.type.body)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            ClusterTripPeriod.entries.forEach {period->
                Control(period.title,Modifier.weight(1f).testTag("cluster-trip-${period.name.lowercase()}"),selected=selected==period) {
                    onSelect(period)
                }
            }
        }
    }
}
