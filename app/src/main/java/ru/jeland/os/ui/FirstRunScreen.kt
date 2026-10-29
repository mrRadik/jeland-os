package ru.jeland.os.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.core.*
import ru.jeland.os.data.AppLanguage
import ru.jeland.os.navigation.NavigationMapSettings
import ru.jeland.os.navigation.NavigationVoicePanel
import ru.jeland.os.navigation.NavigationVoiceLicensePanel
import ru.jeland.os.ui.theme.Jeland

@Composable fun LanguageSettings(model:MainViewModel) {
    val setup by model.dependencies.firstRun.state.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SectionTitle("Язык приложения")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AppLanguage.entries.forEach {language->Control(language.title,Modifier.weight(1f).testTag("language-${language.tag}"),setup.language==language){model.dependencies.firstRun.language(language)}}
        }
    }
}

/** Switches reflect actual Android permissions, never a saved or simulated consent bit. */
@Composable fun PermissionSettings(model:MainViewModel) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var revision by remember {mutableIntStateOf(0)}
    var permissionMessage by remember {mutableStateOf("")}
    val special=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){revision++}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){revision++;model.dependencies.connectivity.start()}
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)revision++}
        owner.lifecycle.addObserver(observer);onDispose {owner.lifecycle.removeObserver(observer)}
    }
    val groups=buildList {
        add(Triple("Местоположение","Для определения позиции и ведения по маршруту",listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)))
        add(Triple("Состояние связи","Для индикатора мобильной сети",listOf(Manifest.permission.READ_PHONE_STATE)))
        if(Build.VERSION.SDK_INT>=31)add(Triple("Bluetooth","Для состояния подключения телефона",listOf(Manifest.permission.BLUETOOTH_CONNECT)))
        if(Build.VERSION.SDK_INT>=33)add(Triple("Уведомления","Для навигации и автозапуска",listOf(Manifest.permission.POST_NOTIFICATIONS)))
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionTitle("Разрешения")
        Label("Выберите нужные функции. Если права уже выданы установщиком, переключатели будут включены.",small=true,muted=true)
        groups.forEachIndexed {index,(title,detail,permissions)->
            val granted=remember(revision){permissions.all {context.checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED}}
            Row(Modifier.fillMaxWidth().heightIn(min=92.dp).testTag("permission-$index"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f)){Label(title);Label(if(granted)"Разрешено"else detail,small=true,muted=true)}
                Switch(granted,onCheckedChange={if(it)launcher.launch(permissions.toTypedArray())},enabled=!granted,
                    colors=SwitchDefaults.colors(checkedThumbColor=Jeland.colors.accent,checkedTrackColor=Jeland.colors.deep,
                        uncheckedThumbColor=Jeland.colors.muted,uncheckedTrackColor=Jeland.colors.raised,uncheckedBorderColor=Jeland.colors.outline,
                        disabledCheckedThumbColor=Jeland.colors.accent,disabledCheckedTrackColor=Jeland.colors.deep))
            }
        }
        val installGranted=remember(revision){context.packageManager.canRequestPackageInstalls()}
        Row(Modifier.fillMaxWidth().heightIn(min=92.dp).testTag("permission-updates"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f)){Label("Установка обновлений");Label(if(installGranted)"Разрешено"else"Чтобы устанавливать новые версии из приложения",small=true,muted=true)}
            Switch(installGranted,onCheckedChange={if(it)runCatching {
                special.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
            }.onFailure {permissionMessage="Разрешение можно выдать установщиком на компьютере"}},enabled=!installGranted,
                colors=SwitchDefaults.colors(disabledCheckedThumbColor=Jeland.colors.accent,disabledCheckedTrackColor=Jeland.colors.deep,
                    uncheckedThumbColor=Jeland.colors.muted,uncheckedTrackColor=Jeland.colors.raised,uncheckedBorderColor=Jeland.colors.outline))
        }
        if(permissionMessage.isNotBlank())Label(permissionMessage,small=true,muted=true)
    }
}

@Composable fun FirstRunScreen(s:UiState,model:MainViewModel) {
    var page by rememberSaveable {mutableIntStateOf(0)}
    val startup by model.startup.collectAsStateWithLifecycle()
    val steps=listOf("Язык","Оформление","Разрешения","Автозапуск","Навигация","Всё готово")
    val scroll=rememberScrollState()
    var voiceLicense by remember {mutableStateOf(false)}
    if(voiceLicense)Dialog(onDismissRequest={voiceLicense=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Panel(Modifier.fillMaxWidth(.9f).fillMaxHeight(.85f)){Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Control("Закрыть",Modifier.fillMaxWidth()){voiceLicense=false}
            NavigationVoiceLicensePanel(model.dependencies.navigation.voice){voiceLicense=false}
        }}
    }
    LaunchedEffect(page){scroll.scrollTo(0)}
    BackHandler(page>0){page--}
    Column(Modifier.fillMaxSize().background(Jeland.colors.background).padding(32.dp).testTag("first-run"),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){SectionTitle("Добро пожаловать в Jeland OS");Label("Настройте приложение под себя",small=true,muted=true)}
            Label("${page+1} / ${steps.size}",small=true,muted=true)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            steps.indices.forEach {index->Box(Modifier.weight(1f).height(4.dp).background(if(index<=page)Jeland.colors.accent else Jeland.colors.outline))}
        }
        Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
        Panel(Modifier.fillMaxWidth().heightIn(max=1000.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
                when(page) {
                    0->{LanguageSettings(model);Label("Язык можно изменить позже в настройках",small=true,muted=true)}
                    1->{
                        SectionTitle("Ваш стиль")
                        AppearancePicker(s,model,setup=true)
                    }
                    2->PermissionSettings(model)
                    3->{SectionTitle("Всегда под рукой");SystemStartupSettings(startup,model::setSystemStartup,model::openStartupPermission)}
                    4->{
                        SectionTitle("Навигация","Скачайте карты нужного региона. Настройки доступны и позже на вкладке навигации.")
                        NavigationMapSettings(model.dependencies.navigation)
                        NavigationVoicePanel(model.dependencies.navigation.voice){voiceLicense=true}
                    }
                    else->{
                        SectionTitle("Можно ехать","Jeland OS подключается к автомобилю автоматически")
                        Label("Проверьте основные функции на стоянке. Все выбранные настройки сохранены.",small=true)
                        Label("Карты и голос можно настроить позже в навигации",small=true,muted=true)
                    }
                }
            }
        }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            if(page>0)Control("Назад",Modifier.weight(1f).testTag("setup-back")){page--}
            Control(if(page==steps.lastIndex)"Начать пользоваться"else"Далее",Modifier.weight(1f).testTag("setup-next"),selected=true){if(page<steps.lastIndex)page++ else model.dependencies.firstRun.finish()}
        }
    }
}
