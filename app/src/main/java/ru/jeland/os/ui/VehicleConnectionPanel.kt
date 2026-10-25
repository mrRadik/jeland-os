package ru.jeland.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.*

@Composable fun VehicleConnectionPanel(s: UiState,model: MainViewModel) {
    val car=model.dependencies.car
    Column(Modifier.fillMaxWidth().testTag("vehicle-connection-panel"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("Подключение автомобиля",s.car.detail)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control("DEMO",Modifier.weight(1f).testTag("vehicle-mode-demo"),s.car.demo){car.useVehicle(false)}
            Control("Подключить ГУ",Modifier.weight(1f).testTag("vehicle-mode-live"),!s.car.demo){car.useVehicle(true)}
        }
        LogExportPanel(model)
        if(!s.car.demo) {
            Label("Управление доступно автоматически при подключении ГУ. Шторка — только со свободным проёмом.",small=true,muted=true)
            Control("Обновить показания",Modifier.testTag("vehicle-refresh")){car.refresh()}
            Label("Подробная диагностика сохраняется в журнале. Для отправки используйте сохранение логов.",small=true,muted=true)
        }else Label("Локальная демонстрация. Ни одна команда автомобиля не отправляется.",small=true,muted=true)
    }
}

@Composable private fun LogExportPanel(model:MainViewModel) {
    val saving by model.savingLogs.collectAsStateWithLifecycle()
    val clearing by model.clearingLogs.collectAsStateWithLifecycle()
    val saved by model.savedLogs.collectAsStateWithLifecycle()
    val message by model.logsMessage.collectAsStateWithLifecycle()
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().testTag("log-export-panel"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionTitle("Логи Jeland OS","Журнал сохраняется автоматически с запуска, в том числе в DEMO")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Control(if(saving)"Сохраняем…"else"Сохранить логи ZIP",Modifier.weight(1f).testTag("save-os-logs"),enabled=!saving&&!clearing){model.saveLogs()}
            saved?.let {archive->Control("Поделиться",Modifier.testTag("share-os-logs")) {
                try {
                    val intent=android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
                        .putExtra(android.content.Intent.EXTRA_STREAM,archive.uri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData=android.content.ClipData.newRawUri(archive.name,archive.uri)
                    context.startActivity(android.content.Intent.createChooser(intent,"Отправить логи Jeland OS"))
                }catch(error:Exception){model.logsMessage.value="Нет приложения для отправки. ZIP сохранён в ${archive.location}"}
            }}
        }
        LogCleanupButton(saving||clearing,model::clearLogs)
        message?.let {Label(it,Modifier.testTag("os-logs-result"),small=true)}
        Label("ZIP появится в Загрузки/JelandOS. Сохраняются журналы и уже полученные RAW; команды автомобилю не отправляются. Логи могут содержать данные автомобиля.",small=true,muted=true)
    }
}
