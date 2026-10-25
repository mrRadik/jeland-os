package ru.jeland.os.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.ui.Control
import ru.jeland.os.ui.Label
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JIcons

/** The HU may not ship an IME. Keep our keyboard inside the search sheet, not over results. */
@Composable internal fun NavigationSearchPanel(
    value:TextFieldValue,onValueChange:(TextFieldValue)->Unit,results:List<NavigationPlace>,
    searching:Boolean,error:String?,onChoose:(NavigationPlace)->Unit,modifier:Modifier=Modifier,
    places:NavigationPlacesRepository?=null,onFavorite:(NavigationPlace)->Unit={}
) {
    val c=Jeland.colors
    val history=places?.history?.collectAsStateWithLifecycle()?.value.orEmpty()
    val favorites=places?.favorites?.collectAsStateWithLifecycle()?.value.orEmpty()
    val savedError=places?.error?.collectAsStateWithLifecycle()?.value
    var tab by rememberSaveable {mutableStateOf("search")}
    var confirmClear by remember {mutableStateOf(false)}
    var keyboardVisible by rememberSaveable {mutableStateOf(true)}
    val systemKeyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit){systemKeyboard?.hide()}
    Column(modifier.padding(horizontal=16.dp).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value,onValueChange={tab="search";onValueChange(it.copy(text=it.text.take(160)))},singleLine=true,
            label={Text("Адрес или организация")},colors=navigationFieldColors(),
            keyboardOptions=KeyboardOptions(showKeyboardOnFocus=false),
            trailingIcon={if(value.text.isNotEmpty())IconButton(onClick={onValueChange(TextFieldValue())},modifier=Modifier.testTag("navigation-search-clear")) {
                Icon(JIcons.Close,"Очистить адрес",tint=c.muted)
            }},modifier=Modifier.fillMaxWidth().testTag("navigation-destination"))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(searching)"Ищем…"else if(value.text.length>=2)"Показано: ${results.size}"else"Введите адрес или название",
                color=c.muted,fontSize=Jeland.type.footnote,modifier=Modifier.weight(1f))
            if(places!=null)TextButton(onClick={places.rememberSearch(value.text);tab="search";keyboardVisible=false;systemKeyboard?.hide()},
                enabled=value.text.trim().length>=2,modifier=Modifier.testTag("navigation-search-submit")) {Text("Найти",color=c.accent)}
            TextButton(onClick={keyboardVisible=!(keyboardVisible&&tab=="search");if(keyboardVisible)tab="search";systemKeyboard?.hide()},modifier=Modifier.testTag("navigation-keyboard-toggle")) {
                Text(if(keyboardVisible)"Скрыть клавиатуру"else"Клавиатура",color=c.accent)
            }
        }
        if(places!=null)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            for((id,title) in listOf("search" to "Поиск","history" to "История (${history.size})","favorites" to "Избранное (${favorites.size})"))
                Control(title,Modifier.weight(1f).testTag("navigation-search-tab-$id"),selected=tab==id) {tab=id;if(id!="search"){keyboardVisible=false;systemKeyboard?.hide()}}
        }
        savedError?.let {Label(it,small=true)}
        if(tab=="history")TextButton(onClick={confirmClear=true},enabled=history.isNotEmpty()||savedError!=null,
            modifier=Modifier.testTag("navigation-history-clear")) {Text("Очистить историю",color=c.muted)}
        if(searching)LinearProgressIndicator(Modifier.fillMaxWidth(),color=c.accent)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("navigation-search-results"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
          when(tab) {
            "history"->{
                if(history.isEmpty())item {Label("История пока пуста. Она сохраняет запросы по кнопке «Найти» и выбранные результаты.",small=true,muted=true)}
                itemsIndexed(history,key={_,entry->entry.id}) {index,entry->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            if(entry.place!=null){places?.rememberSelection(entry.query,entry.place);onChoose(entry.place)}
                            else {onValueChange(TextFieldValue(entry.query,TextRange(entry.query.length)));places?.rememberSearch(entry.query);tab="search"}
                        }.padding(vertical=10.dp).testTag("navigation-history-$index")) {
                            Text(entry.place?.name?:entry.query,color=c.text,fontSize=Jeland.type.secondary)
                            entry.place?.let {Text(it.searchSubtitle,color=c.muted,fontSize=Jeland.type.footnote)}
                        }
                        entry.place?.let {place->IconButton(onClick={onFavorite(place)},modifier=Modifier.testTag("navigation-history-favorite-$index")) {Icon(JIcons.Star,"Сохранить точку",tint=c.accent)}}
                        IconButton(onClick={places?.removeHistory(entry.id)},modifier=Modifier.testTag("navigation-history-remove-$index")) {Icon(JIcons.Close,"Удалить запись истории",tint=c.muted)}
                    }
                    HorizontalDivider(color=c.outline)
                }
            }
            "favorites"->{
                if(favorites.isEmpty())item {Label("Избранных точек пока нет. Нажмите звезду у результата поиска или выбранной точки на карте.",small=true,muted=true)}
                itemsIndexed(favorites,key={_,entry->entry.id}) {index,entry->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {places?.rememberSelection(entry.title,entry.destination);onChoose(entry.destination)}
                            .padding(vertical=10.dp).testTag("navigation-favorite-$index")) {
                            Text(entry.title,color=c.text,fontSize=Jeland.type.secondary)
                            Text(entry.place.fullName,color=c.muted,fontSize=Jeland.type.footnote)
                        }
                        IconButton(onClick={onFavorite(entry.place)},modifier=Modifier.testTag("navigation-favorite-edit-$index")) {Icon(JIcons.Edit,"Переименовать точку",tint=c.accent)}
                        IconButton(onClick={places?.removeFavorite(entry.id)},modifier=Modifier.testTag("navigation-favorite-remove-$index")) {Icon(JIcons.Close,"Убрать из избранного",tint=c.muted)}
                    }
                    HorizontalDivider(color=c.outline)
                }
            }
            else->{
            error?.let {item {Label(it,small=true)}}
            if(!searching&&value.text.length>=2&&results.isEmpty())item {Label("Ничего не найдено в загруженном регионе. Можно выбрать точку на карте.",small=true,muted=true)}
            if(value.text.length<2)item {Label("Например: Мир 1 или Мира 1 Чебаркуль. Можно вводить часть улицы и номера дома.",small=true,muted=true)}
            itemsIndexed(results) {index,place->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                  Column(Modifier.weight(1f).clickable {places?.rememberSelection(value.text,place);onChoose(place)}.padding(vertical=10.dp).testTag("navigation-result-$index")) {
                    Text(place.name,color=c.text,fontSize=Jeland.type.secondary)
                    Text(place.searchSubtitle,color=c.muted,fontSize=Jeland.type.footnote,modifier=Modifier.testTag("navigation-result-locality-$index"))
                  }
                  if(places!=null)IconButton(onClick={onFavorite(place)},modifier=Modifier.testTag("navigation-result-favorite-$index")) {
                    Icon(JIcons.Star,"Сохранить точку",tint=if(favorites.any {it.id==navigationPlaceKey(place)})c.accent else c.muted)
                  }
                }
                HorizontalDivider(color=c.outline)
            }
            }
          }
        }
        if(keyboardVisible&&tab=="search")NavigationAddressKeyboard {key->onValueChange(editNavigationAddress(value,key))}
    }
    if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},containerColor=c.raised,
        title={Text("Очистить историю поиска?",color=c.text)},text={Text("Избранные точки останутся.",color=c.muted)},
        confirmButton={TextButton(onClick={places?.clearHistory();confirmClear=false},modifier=Modifier.testTag("navigation-history-clear-confirm")){Text("Очистить",color=c.accent)}},
        dismissButton={TextButton(onClick={confirmClear=false}){Text("Отмена",color=c.muted)}})
}

/** Selection/cursor-aware edits also preserve input from an attached physical keyboard. */
internal fun editNavigationAddress(value:TextFieldValue,key:String):TextFieldValue {
    val start=value.selection.min.coerceIn(0,value.text.length)
    val end=value.selection.max.coerceIn(start,value.text.length)
    val from=if(key=="⌫"&&start==end&&start>0) {
        if(start>1&&value.text[start-1].isLowSurrogate()&&value.text[start-2].isHighSurrogate())start-2 else start-1
    }else start
    val insert=if(key=="⌫")""else key
    val text=value.text.replaceRange(from,end,insert).take(160)
    return TextFieldValue(text,TextRange((from+insert.length).coerceAtMost(text.length)))
}

@Composable internal fun NavigationAddressKeyboard(onKey:(String)->Unit) {
    var english by rememberSaveable {mutableStateOf(false)}
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("navigation-address-keyboard")) {
        val keyHeight=(maxWidth/12).coerceIn(34.dp,48.dp)
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            val rows=if(english)listOf("1234567890","qwertyuiop","asdfghjkl","zxcvbnm")
                else listOf("1234567890","йцукенгшщзхъ","фывапролджэ","ячсмитьбюё")
            rows.forEach {row->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    row.forEach {letter->AddressKey(letter.toString(),Modifier.weight(1f).height(keyHeight)){onKey(letter.toString())}}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                AddressKey(if(english)"EN / РУС"else"РУС / EN",Modifier.weight(2f).height(keyHeight),"Сменить язык клавиатуры"){english=!english}
                AddressKey("/",Modifier.weight(1f).height(keyHeight)){onKey("/")}
                AddressKey("Пробел",Modifier.weight(5f).height(keyHeight)){onKey(" ")}
                AddressKey("-",Modifier.weight(1f).height(keyHeight)){onKey("-")}
                AddressKey("⌫",Modifier.weight(2f).height(keyHeight),"Удалить символ"){onKey("⌫")}
            }
        }
    }
}

@Composable private fun AddressKey(text:String,modifier:Modifier,description:String=text,onClick:()->Unit) {
    val c=Jeland.colors
    Box(modifier.clip(Jeland.control).background(c.raised).border(1.dp,c.outline,Jeland.control)
        .semantics {contentDescription=description}.testTag("navigation-key-$text").clickable(role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Text(text,color=c.text,fontSize=if(text.length>3)13.sp else 19.sp,maxLines=1)
    }
}
