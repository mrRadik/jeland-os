package ru.jeland.os.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.core.*
import ru.jeland.os.data.updates.UpdateNewsRepository
import ru.jeland.os.localization.LocalizedText as Text
import ru.jeland.os.localization.localized
import ru.jeland.os.ui.theme.Jeland

@Composable internal fun WhatsNewHost(repository:UpdateNewsRepository) {
    val news by repository.notice.collectAsStateWithLifecycle()
    val current=news?:return
    val context=LocalContext.current
    // Older updaters persisted the APK metadata without the release description.
    // Keep the matching release's text offline in the new APK as well.
    val releaseNotes=remember(context,current.versionCode,current.body) {
        plainReleaseNotes(current.body).ifBlank {
            runCatching {
                context.assets.open("updates/${current.versionCode}.md").bufferedReader().use {it.readText()}
            }.getOrDefault("").let(::plainReleaseNotes)
        }
    }
    val body=releaseNotes.ifBlank {
        if(current.versionCode==123L)listOf(
            "Добавлено окно с изменениями после обновления приложения.",
            "Приборная панель настраивается в мастере первого запуска.",
            "Компактная верхняя панель приборки плавно раскрывается для уведомлений.")
            .map {"• "+localized(it)}.joinToString("\n\n")
        else localized("Обновление установлено.")
    }
    Dialog(onDismissRequest=repository::dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Panel(Modifier.padding(20.dp).widthIn(max=760.dp).fillMaxWidth(.94f).heightIn(max=640.dp).testTag("whats-new-dialog")) {
            Column(Modifier.padding(26.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("Что нового",color=Jeland.colors.text,fontSize=Jeland.type.heading)
                Text("Jeland OS · "+current.versionName,color=Jeland.colors.accent,fontSize=Jeland.type.body)
                Text(body,Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("whats-new-body"),
                    color=Jeland.colors.text,fontSize=Jeland.type.body)
                Control("Понятно",Modifier.fillMaxWidth().testTag("whats-new-close"),selected=true,onClick=repository::dismiss)
            }
        }
        LaunchedEffect(current.versionCode){withFrameNanos {};repository.shown(current.versionCode)}
    }
}
