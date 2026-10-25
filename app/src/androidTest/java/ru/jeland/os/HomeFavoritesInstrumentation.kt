package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.jeland.os.core.ThemeMode
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeFavoritesInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    private fun capture(name:String,widget:Boolean=false) {
        ui.waitForIdle();android.os.SystemClock.sleep(450)
        val bitmap=(if(widget)ui.onNodeWithTag("favorite-apps-widget")else ui.onRoot()).captureToImage().asAndroidBitmap()
        val size=ui.activity.resources.displayMetrics.let {"${it.widthPixels}x${it.heightPixels}"}
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa-0.4.3/$size").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun homeFavoritesHaveLargeIconsAndWorkingEmptySlotsWithoutScrolling() {
        ui.runOnIdle {container().car.useVehicle(false)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
        ui.waitUntil(10000){!container().apps.state.value.loading}
        val original=container().apps.state.value.favorites.toList()
        val preferences=container().preferences.state.value
        val installed=container().apps.state.value.installed
        check(installed.size>=2)
        try {
            ui.runOnIdle {
                original.forEach {container().apps.toggleFavorite(it)}
                installed.take(2).forEach {container().apps.toggleFavorite(it.id)}
                container().preferences.change(preferences.copy(showFavorites=true,showTires=true))
            }
            ui.onNodeWithTag("nav-home").performClick()
            for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
                ui.runOnIdle {container().theme.setMode(theme)}
                for(index in 0..1) {
                    ui.onNodeWithTag("favorite-home-icon-$index",useUnmergedTree=true).assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp).assertIsDisplayed()
                    ui.onNodeWithTag("favorite-home-title-$index",useUnmergedTree=true).assertTextEquals(installed[index].label)
                    val icon=ui.onNodeWithTag("favorite-home-icon-$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                    val title=ui.onNodeWithTag("favorite-home-title-$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                    assertTrue("Reference places the label below the icon",title.top>=icon.bottom)
                    assertTrue("Reference centres the icon over its label",kotlin.math.abs(title.center.x-icon.center.x)<3f)
                    ui.onNodeWithTag("favorite-home-card-$index").assertHasClickAction()
                }
                ui.onNodeWithTag("favorite-home-add-2",useUnmergedTree=true).assertWidthIsEqualTo(44.dp).assertIsDisplayed()
                val config=ui.onNodeWithTag("home-scroll").fetchSemanticsNode().config
                assertFalse("Fixed home layout has no scrolling",config.contains(SemanticsProperties.VerticalScrollAxisRange))
                capture("home-${theme.name.lowercase()}")
                capture("favorites-${theme.name.lowercase()}",true)
                ui.onNodeWithTag("favorite-home-card-2").performClick()
                ui.onNodeWithTag("popup-apps").assertIsDisplayed()
                ui.onNodeWithTag("climate-bar").assertIsDisplayed()
                ui.onNodeWithTag("close-popup").performClick()
            }
            // Hiding the tire card redistributes height: this used to flip favorites vertically.
            ui.runOnIdle {container().preferences.change(preferences.copy(showFavorites=true,showTires=false))}
            ui.waitForIdle()
            for(index in 0..1) {
                val icon=ui.onNodeWithTag("favorite-home-icon-$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                val title=ui.onNodeWithTag("favorite-home-title-$index",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertTrue("Reference icon/label layout remains stable",title.top>=icon.bottom)
            }
            capture("favorites-tall-cards",true)
            ui.runOnIdle {container().apps.state.value.favorites.toList().forEach {container().apps.toggleFavorite(it)}}
            for(index in 0..3)ui.onNodeWithTag("favorite-home-title-$index",useUnmergedTree=true).assertTextEquals("Добавить")
            capture("favorites-empty",true)
            ui.onNodeWithTag("favorite-home-card-0").performClick()
            ui.onNodeWithTag("popup-apps").assertIsDisplayed()
            ui.onNodeWithTag("close-popup").performClick()
        } finally {
            ui.runOnIdle {
                container().apps.state.value.favorites.toList().forEach {container().apps.toggleFavorite(it)}
                original.forEach {container().apps.toggleFavorite(it)}
                container().preferences.change(preferences)
                container().theme.setMode(ThemeMode.DARK)
            }
        }
    }
}
