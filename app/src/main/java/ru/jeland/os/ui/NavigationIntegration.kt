package ru.jeland.os.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.jeland.os.core.*
import ru.jeland.os.ui.theme.*

/** Companion view, not a simulated map or an embedded foreign Activity. */
@Composable fun InstalledNavigationWidget(s:ExternalNavigationState,modifier:Modifier,onClick:()->Unit) {
    val c=Jeland.colors
    Panel(modifier.fillMaxHeight().testTag("navigation-widget").clickable(onClick=onClick)) {
        Column(Modifier.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(JIcons.Navigation,null,Modifier.size(26.dp),tint=c.accent)
                Text(s.selected?.title?:"Навигация",fontSize=Jeland.type.secondary,color=c.text,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Icon(JIcons.ChevronRight,null,Modifier.size(22.dp),tint=c.muted)
            }
            val g=s.guidance
            if(g!=null) {
                Text(g.title.ifBlank {g.text},color=c.text,fontSize=Jeland.type.body,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("navigation-guidance-title"))
                Text(if(g.title.isBlank())g.detail else listOf(g.text,g.detail).filter {it.isNotBlank()}.distinct().joinToString(" · "),color=c.muted,fontSize=Jeland.type.footnote,maxLines=2,overflow=TextOverflow.Ellipsis)
            } else {
                Text(when {s.selected==null->"Выберите навигатор в настройках";!s.selectedInstalled->"Приложение не установлено";!s.accessGranted->"Открыть и подключить подсказки";!s.listenerConnected->"Подключение подсказок…";else->"Открыть карту и маршрут"},
                    color=c.muted,fontSize=Jeland.type.secondary,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable fun InstalledNavigationScreen(model:MainViewModel,s:ExternalNavigationState,mapVisible:Boolean=true,onSettings:()->Unit={}) {
    val host by ru.jeland.os.navhost.NavHostConnection.state.collectAsState()
    if(host.connected&&s.selected!=null&&s.selectedInstalled) {
        Column(Modifier.fillMaxSize().testTag("installed-navigation-screen"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Навигация · ${s.selected.title}",color=Jeland.colors.text,fontSize=Jeland.type.body,modifier=Modifier.weight(1f))
                RoundControl(JIcons.Settings,"Настройки навигации",Modifier.testTag("navigation-settings"),onClick=onSettings)
                RoundControl(JIcons.Close,"Выключить встроенную карту",Modifier.testTag("navigation-host-close"),onClick=ru.jeland.os.navhost.NavHostConnection::stop)
            }
            Label("Экспериментальный режим · проверять на стоянке",small=true,muted=true)
            EmbeddedNavigator(s.selected,Modifier.fillMaxWidth().weight(1f),visible=mapVisible)
        }
        return
    }
    val r=model.dependencies.navigation;val c=Jeland.colors
    val fieldColors=OutlinedTextFieldDefaults.colors(focusedTextColor=c.text,unfocusedTextColor=c.text,
        cursorColor=c.accent,focusedBorderColor=c.accent,unfocusedBorderColor=c.outline,
        focusedLabelColor=c.accent,unfocusedLabelColor=c.muted,focusedPlaceholderColor=c.muted,unfocusedPlaceholderColor=c.muted)
    var editing by rememberSaveable {mutableStateOf<String?>(null)}
    var title by rememberSaveable {mutableStateOf("")}
    var point by rememberSaveable {mutableStateOf("")}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    fun edit(id:String,defaultTitle:String) {
        val p=s.places.firstOrNull {it.id==id};editing=id;title=p?.title?:defaultTitle
        point=p?.let {"${it.latitude}, ${it.longitude}"}.orEmpty();error=null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=18.dp).testTag("installed-navigation-screen"),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {SectionTitle("Навигация");Label(s.selected?.title?:"Выберите навигатор в настройках",small=true,muted=true)}
            RoundControl(JIcons.Settings,"Настройки навигации",Modifier.testTag("navigation-settings"),onClick=onSettings)
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Icon(JIcons.Navigation,null,Modifier.size(64.dp),tint=c.accent)
                    Column(Modifier.weight(1f)) {
                        Text(s.guidance?.title?.ifBlank {s.guidance.text}?:"Куда поедем?",color=c.text,fontSize=Jeland.type.heading,modifier=Modifier.testTag("navigation-screen-instruction"))
                        s.guidance?.let {g->Text(listOf(g.text,g.detail).filter {it.isNotBlank()&&it!=g.title}.distinct().joinToString(" · "),color=c.muted,fontSize=Jeland.type.body)}
                    }
                }
                Control("Открыть ${s.selected?.title?:"навигатор"}",Modifier.fillMaxWidth().testTag("navigation-open"),selected=true,enabled=s.selectedInstalled,onClick=model::openNavigator)
                Label("Поиск, карта и ведение открываются в выбранном приложении. Возврат в Jeland OS — кнопкой «Назад» или через список приложений.",small=true,muted=true)
            }
        }
        SectionTitle("Мои места","Координаты можно скопировать из карточки места в навигаторе.")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf("home" to "Домой","work" to "На работу").forEach {(id,label)->
                val place=s.places.firstOrNull {it.id==id}
                Panel(Modifier.weight(1f)) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(place?.title?:label,color=c.text,fontSize=Jeland.type.body)
                    Control(if(place==null)"Задать место"else"Маршрут",Modifier.fillMaxWidth().testTag("navigation-place-$id"),enabled=place==null||s.selectedInstalled) {if(place==null)edit(id,label)else model.openNavigationPlace(id)}
                    if(place!=null)Control("Изменить",Modifier.fillMaxWidth()) {edit(id,label)}
                }}
            }
        }
        s.places.filter {it.id!="home"&&it.id!="work"}.forEach {p->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Control(p.title,Modifier.weight(1f),enabled=s.selectedInstalled){model.openNavigationPlace(p.id)}
                RoundControl(JIcons.Edit,"Изменить ${p.title}"){edit(p.id,p.title)}
            }
        }
        Control("Добавить место",Modifier.fillMaxWidth().testTag("navigation-add-place"),enabled=s.places.size<12){edit(java.util.UUID.randomUUID().toString(),"")}
        if(editing!=null)Panel(Modifier.fillMaxWidth().testTag("navigation-place-editor")) {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                SectionTitle("Место назначения")
                OutlinedTextField(title,{title=it.take(80)},label={Text("Название")},colors=fieldColors,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("navigation-place-name"))
                OutlinedTextField(point,{point=it.take(100)},label={Text("Широта, долгота")},placeholder={Text("55.16, 61.40")},colors=fieldColors,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("navigation-place-coordinates"))
                error?.let {Text(it,color=c.warm,fontSize=Jeland.type.secondary)}
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Control("Сохранить",Modifier.weight(1f).testTag("navigation-place-save"),selected=true) {error=r.savePlace(editing!!,title,point);if(error==null)editing=null}
                    Control("Отмена",Modifier.weight(1f)){editing=null}
                    if(s.places.any {it.id==editing})RoundControl(JIcons.Delete,"Удалить место"){r.removePlace(editing!!);editing=null}
                }
            }
        }
        Label("Для экспериментальной карты внутри вкладки включите ADB-помощник через START → N. Без помощника навигатор открывается отдельно.",small=true,muted=true)
        if(s.selected==NavigationProvider.YANDEX)Label("Яндекс может ограничивать вызовы маршрутов по ссылкам без идентификатора разработчика. Обычное открытие приложения не передаёт координаты.",small=true,muted=true)
    }
}

@Composable fun NavigationPreferences(model:MainViewModel,s:ExternalNavigationState) {
    val r=model.dependencies.navigation;val c=Jeland.colors
    Panel(Modifier.fillMaxWidth().testTag("navigation-preferences")) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {SectionTitle("Навигация");Label("Приложение по умолчанию",small=true,muted=true)}
                RoundControl(JIcons.Refresh,"Обновить список навигаторов",Modifier.testTag("navigation-refresh"),onClick=r::refresh)
            }
            NavigationProvider.entries.forEach {p->
                val app=s.installations.firstOrNull {it.provider==p}
                Control(p.title,Modifier.fillMaxWidth().testTag("navigator-${p.name.lowercase()}"),selected=s.selected==p) {r.selectProvider(p)}
                Label(if(app?.installed==true)"Установлен · ${app.version}"else"Не установлен или отключён",small=true,muted=true)
            }
            val host by ru.jeland.os.navhost.NavHostConnection.state.collectAsState()
            SectionTitle("Встроенная карта",if(host.connected)"ADB-помощник подключён"else"Требуется запуск ENABLE-NAVIGATION.cmd с компьютера")
            Label("Без root. Помощник работает с правами ADB и передаёт касания только выбранному навигатору. После перезагрузки ГУ его нужно включить снова. Полноценность ведения проверяется отдельно.",small=true,muted=true)
            if(host.connected)Control("Отключить помощник",Modifier.fillMaxWidth().testTag("navigation-host-stop"),onClick=ru.jeland.os.navhost.NavHostConnection::stop)
            SectionTitle("Подсказки на главной",when { !s.accessGranted->"Требуется разрешение Android";!s.listenerConnected->"Доступ разрешён · ожидание подключения";s.guidance==null->"Подключено · ожидаем подсказку навигатора";else->"Получаем уведомления выбранного навигатора"})
            Label("Android даёт общий доступ к уведомлениям. Jeland OS обрабатывает только 2ГИС и Яндекс Навигатор; сообщения других приложений не читает и не сохраняет. Текст маршрута в журнал не записывается.",small=true,muted=true)
            Control(if(s.accessGranted)"Настройки доступа"else"Разрешить подсказки",Modifier.fillMaxWidth().testTag("navigation-notification-access"),onClick=model::navigationAccess)
            Label("Если версия навигатора не публикует маршрут в уведомлении, подсказки здесь недоступны. Без разрешения запуск и маршруты работают.",small=true,muted=true)
        }
    }
}
