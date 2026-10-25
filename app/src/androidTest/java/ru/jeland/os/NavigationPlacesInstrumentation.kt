package ru.jeland.os

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.MemorySettingsStore
import ru.jeland.os.navigation.*
import ru.jeland.os.ui.theme.*

class NavigationPlacesInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val home=NavigationPlace("Мира, 1",GeoPoint(55.16,61.4),"address","Челябинск","улица Мира, 1")
    private var selected:NavigationPlace?=null
    private fun show(places:NavigationPlacesRepository,dark:Boolean=true) {
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
            var query by remember {mutableStateOf(TextFieldValue("Мира 1"))}
            var saving by remember {mutableStateOf<NavigationPlace?>(null)}
            NavigationSearchPanel(query,{query=it},listOf(home),false,null,{selected=it},
                Modifier.fillMaxSize().background(Jeland.colors.background),places,{saving=it})
            saving?.let {place->NavigationFavoriteDialog(place,places){saving=null}}
        }}}
    }
    @Test fun submitAndChosenResultEnterHistoryAndCanBeChosenAgain() {
        val repo=NavigationPlacesRepository(MemorySettingsStore());show(repo)
        ui.onNodeWithTag("navigation-search-submit").performClick()
        ui.runOnIdle {assertEquals("Мира 1",repo.history.value.single().query);assertNull(repo.history.value.single().place)}
        ui.onNodeWithTag("navigation-search-tab-history").performClick()
        ui.onNodeWithTag("navigation-history-0").performClick()
        ui.onNodeWithTag("navigation-result-0").performClick()
        ui.runOnIdle {assertEquals(home,selected);assertEquals(home,repo.history.value.single().place);selected=null}
        ui.onNodeWithTag("navigation-search-tab-history").performClick()
        ui.onNodeWithTag("navigation-history-0").performClick()
        ui.runOnIdle {assertEquals(home,selected)}
    }
    @Test fun favoriteCanBeNamedRenamedSelectedAndRemovedInLightTheme() {
        val repo=NavigationPlacesRepository(MemorySettingsStore());show(repo,false)
        ui.onNodeWithTag("navigation-result-favorite-0").performClick()
        ui.onNodeWithTag("navigation-favorite-title").performTextReplacement("Дом")
        ui.onNodeWithTag("navigation-favorite-save").performClick()
        ui.runOnIdle {assertEquals("Дом",repo.favorites.value.single().title)}
        ui.onNodeWithTag("navigation-search-tab-favorites").performClick()
        ui.onNodeWithTag("navigation-favorite-edit-0").performClick()
        ui.onNodeWithTag("navigation-favorite-title").performTextReplacement("Квартира")
        ui.onNodeWithTag("navigation-favorite-save").performClick()
        ui.onNodeWithTag("navigation-favorite-0").performClick()
        ui.runOnIdle {assertEquals("Квартира",selected!!.name);assertEquals(home.point,selected!!.point);assertEquals(home.address,selected!!.address)}
        ui.onNodeWithTag("navigation-favorite-remove-0").performClick()
        ui.runOnIdle {assertTrue(repo.favorites.value.isEmpty());assertEquals(1,repo.history.value.size)}
    }
    @Test fun clearingHistoryNeedsConfirmationAndPreservesFavorites() {
        val repo=NavigationPlacesRepository(MemorySettingsStore());repo.rememberSelection("Мира 1",home);repo.saveFavorite(home,"Дом");show(repo)
        ui.onNodeWithTag("navigation-search-tab-history").performClick()
        ui.onNodeWithTag("navigation-history-clear").performClick()
        ui.runOnIdle {assertEquals(1,repo.history.value.size)}
        ui.onNodeWithTag("navigation-history-clear-confirm").performClick()
        ui.runOnIdle {assertTrue(repo.history.value.isEmpty());assertEquals("Дом",repo.favorites.value.single().title)}
    }
}
