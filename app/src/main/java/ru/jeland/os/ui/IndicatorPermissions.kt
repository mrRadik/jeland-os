package ru.jeland.os.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable fun IndicatorPermissions(model:MainViewModel) {
    val context=LocalContext.current
    var revision by remember {mutableIntStateOf(0)}
    val requested=listOf(Manifest.permission.READ_PHONE_STATE)+if(Build.VERSION.SDK_INT>=31)listOf(Manifest.permission.BLUETOOTH_CONNECT)else emptyList()
    val missing=remember(revision){requested.filter {context.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){revision++;model.dependencies.connectivity.start()}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("Индикаторы связи")
        Label("Только чтение состояния. Без поиска устройств, подключения к сетям и фонового трафика.",small=true,muted=true)
        if(missing.isNotEmpty())Control("Разрешить чтение индикаторов",Modifier.testTag("radio-permissions")){launcher.launch(missing.toTypedArray())}
        else Label("Необходимые разрешения предоставлены",small=true,muted=true)
    }
}
