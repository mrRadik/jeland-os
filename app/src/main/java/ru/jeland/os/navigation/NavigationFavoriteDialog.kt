package ru.jeland.os.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.Jeland

@Composable internal fun NavigationFavoriteDialog(place:NavigationPlace,places:NavigationPlacesRepository,onDismiss:()->Unit) {
    val favorites by places.favorites.collectAsStateWithLifecycle()
    val error by places.error.collectAsStateWithLifecycle()
    val existing=favorites.firstOrNull {it.id==navigationPlaceKey(place)}
    var title by remember(place) {mutableStateOf(TextFieldValue((existing?.title?:place.name).take(80),TextRange(0,(existing?.title?:place.name).take(80).length)))}
    Dialog(onDismissRequest=onDismiss) {
        Panel(Modifier.fillMaxWidth().testTag("navigation-favorite-dialog")) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                SectionTitle(if(existing==null)"Сохранить точку"else"Изменить название",place.fullName)
                OutlinedTextField(title,{title=it.copy(text=it.text.take(80))},singleLine=true,label={Text("Название точки")},
                    keyboardOptions=KeyboardOptions(showKeyboardOnFocus=false),colors=navigationFieldColors(),
                    modifier=Modifier.fillMaxWidth().testTag("navigation-favorite-title"))
                error?.let {Text(it,color=Jeland.colors.critical,style=Jeland.caption)}
                NavigationAddressKeyboard {key->title=editNavigationAddress(title,key).let {it.copy(text=it.text.take(80),selection=TextRange(it.selection.end.coerceAtMost(80)))}}
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Control("Отмена",Modifier.weight(1f),onClick=onDismiss)
                    Control("Сохранить",Modifier.weight(1f).testTag("navigation-favorite-save"),selected=true,enabled=title.text.isNotBlank()) {
                        if(places.saveFavorite(place,title.text))onDismiss()
                    }
                }
            }
        }
    }
}
