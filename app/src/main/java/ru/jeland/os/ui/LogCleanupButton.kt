package ru.jeland.os.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import ru.jeland.os.ui.theme.Jeland

@Composable fun LogCleanupButton(busy:Boolean,onConfirm:()->Unit) {
    var asking by remember {mutableStateOf(false)}
    Control(if(busy)"Журналы обрабатываются…"else"Очистить логи с ГУ",
        Modifier.testTag("clear-os-logs"),enabled=!busy,onClick={asking=true})
    if(asking)AlertDialog(onDismissRequest={asking=false},containerColor=Jeland.colors.panel,
        title={Text("Очистить логи Jeland OS?",color=Jeland.colors.text)},
        text={Text("Будут безвозвратно удалены журналы Jeland OS и ZIP, сохранённые этим приложением в Download/JelandOS. Текущий журнал начнётся заново. Системные логи, логи Probe, карты и настройки не затрагиваются.",color=Jeland.colors.text)},
        confirmButton={TextButton(enabled=!busy,modifier=Modifier.testTag("confirm-clear-os-logs"),onClick={asking=false;onConfirm()}){Text("Удалить логи",color=Jeland.colors.critical)}},
        dismissButton={TextButton(modifier=Modifier.testTag("cancel-clear-os-logs"),onClick={asking=false}){Text("Отмена",color=Jeland.colors.text)}})
}
