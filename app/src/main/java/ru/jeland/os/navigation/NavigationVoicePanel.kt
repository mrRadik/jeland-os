package ru.jeland.os.navigation

import ru.jeland.os.localization.LocalizedText as Text

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.Jeland
import java.util.Locale

@Composable internal fun NavigationVoicePanel(voice:NavigationVoice,showLicense:()->Unit) {
    val enabled by voice.enabled.collectAsStateWithLifecycle()
    val volume by voice.volume.collectAsStateWithLifecycle()
    val speaker by voice.speaker.collectAsStateWithLifecycle()
    val speed by voice.speed.collectAsStateWithLifecycle()
    val accepted by voice.licenseAccepted.collectAsStateWithLifecycle()
    val status by voice.status.collectAsStateWithLifecycle()
    val colors=SliderDefaults.colors(thumbColor=Jeland.colors.accent,activeTrackColor=Jeland.colors.accent,
        inactiveTrackColor=Jeland.colors.outline,activeTickColor=Jeland.colors.panel,inactiveTickColor=Jeland.colors.muted)
    SettingsToggle("Голосовые подсказки","RuVoice · Silero · офлайн, отдельный APK из комплекта",enabled){voice.setEnabled(!enabled)}
    // Re-check when returning from the package installer, without restarting Jeland.
    var installed by remember {mutableStateOf(voice.installed())}
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {installed=voice.installed();onPauseOrDispose {}}
    if(!installed)Label("Установите ruvoice-tts-1.1.1.apk через START.cmd → установка APK. Системный голос менять не нужно.",small=true)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        NavigationSpeaker.entries.forEach {item->
            Control(item.title,Modifier.weight(1f).testTag("navigation-voice-${item.sid}"),selected=speaker==item){voice.setSpeaker(item)}
        }
    }
    Label("${speaker.title} голос · ${speaker.voiceName.removeSuffix("-ru").replaceFirstChar {it.uppercase()}}",small=true,muted=true)
    Label("Темп речи · ${String.format(Locale.ROOT,"%.2f",speed)}×",small=true)
    // Apply only after releasing the thumb, so dragging cannot repeatedly interrupt speech.
    var draft by remember(speed){mutableFloatStateOf(speed)}
    Slider(draft,{draft=it},valueRange=.8f..1.2f,steps=7,colors=colors,onValueChangeFinished={voice.setSpeed(draft)},
        modifier=Modifier.fillMaxWidth().testTag("navigation-voice-speed"))
    Label("Громкость голоса",small=true)
    Slider(volume,voice::setVolume,valueRange=0f..1f,colors=colors,modifier=Modifier.fillMaxWidth().testTag("navigation-voice-volume"))
    if(!accepted)Label("Перед первым запуском подтвердите условия голосовой модели.",small=true)
    Control(if(accepted)"Проверить голос"else"Подключить голос",Modifier.fillMaxWidth().testTag("navigation-voice-test")){
        if(accepted)voice.test()else showLicense()
    }
    Control("Условия и лицензии",Modifier.fillMaxWidth().testTag("navigation-voice-license"),onClick=showLicense)
    Label(status,small=true,muted=true)
}

@Composable internal fun NavigationVoiceLicensePanel(voice:NavigationVoice,onAccepted:()->Unit) {
    val context=LocalContext.current
    val root="voice/ruvoice"
    val files=listOf("LICENSE.txt","NOTICE.txt")
    val licenses=remember {files.joinToString("\n\n"){name->"$name\n\n"+context.assets.open("$root/$name").bufferedReader().use {it.readText()}}}
    Label("RuVoice · kost-t-human / Silero v5_5_ru")
    Label("Движок и основная модель: CC BY-NC-SA 4.0 — некоммерческое использование с указанием авторства. RuVoice устанавливается отдельно, работает офлайн. Jeland явно выбирает Xenia / Aidar и не меняет голос остальных приложений.",small=true)
    Label("Принимая условия, вы соглашаетесь с лицензией модели и ограничениями её использования, приведёнными ниже.",small=true)
    Control("Принимаю условия",Modifier.fillMaxWidth().testTag("navigation-voice-license-accept"),selected=true){voice.acceptLicense();onAccepted()}
    Label(licenses,small=true,muted=true)
}
