package ru.jeland.os

import android.graphics.*
import android.graphics.drawable.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.data.LauncherIconRenderer
import java.io.File

@RunWith(AndroidJUnit4::class)
class IconFanInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun onlyLocalDemo() {ui.runOnIdle{container().car.useVehicle(false);container().theme.setMode(ThemeMode.DARK);if(!container().climate.state.value.power)container().climate.togglePower()}}
    private fun folder()=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa-0.4.1").apply {mkdirs()}
    private fun save(bitmap: Bitmap,name: String) {File(folder(),"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun capture(name: String) {ui.waitForIdle();android.os.SystemClock.sleep(450);save(ui.onRoot().captureToImage().asAndroidBitmap(),name)}
    private fun bounds(bitmap: Bitmap): Rect {
        var l=bitmap.width;var t=bitmap.height;var r=-1;var b=-1
        for(y in 0 until bitmap.height)for(x in 0 until bitmap.width)if(Color.alpha(bitmap.getPixel(x,y))>16){l=minOf(l,x);t=minOf(t,y);r=maxOf(r,x);b=maxOf(b,y)}
        return Rect(l,t,r+1,b+1)
    }
    @Test fun legacyWideTallPaddedAndAdaptiveKeepShapeAndNativeBackground() {
        val wide=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888).also {Canvas(it).drawColor(Color.CYAN)}
        val tall=Bitmap.createBitmap(80,160,Bitmap.Config.ARGB_8888).also {Canvas(it).drawColor(Color.MAGENTA)}
        val padded=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888).also {Canvas(it).drawRect(50f,50f,110f,110f,Paint().apply{color=Color.GREEN})}
        val res=ui.activity.resources
        val fixtures=listOf("wide" to BitmapDrawable(res,wide),"tall" to BitmapDrawable(res,tall),
            "padded" to BitmapDrawable(res,padded),"white-background" to ColorDrawable(Color.WHITE),
            "adaptive" to AdaptiveIconDrawable(ColorDrawable(Color.rgb(16,100,180)),InsetDrawable(ColorDrawable(Color.RED),30)),
            "vector" to res.getDrawable(R.drawable.ic_launcher,ui.activity.theme))
        val rendered=fixtures.map {(name,drawable)->
            drawable.setBounds(3,4,23,24);val before=Rect(drawable.bounds)
            val bitmap=LauncherIconRenderer.render(drawable)
            assertEquals(before,drawable.bounds);assertEquals(192,bitmap.width);assertEquals(192,bitmap.height)
            assertEquals(0,Color.alpha(bitmap.getPixel(0,0)))
            save(bitmap,"icon-$name");bitmap
        }
        val w=bounds(rendered[0]);val h=bounds(rendered[1]);val p=bounds(rendered[2])
        assertEquals(2.0,w.width().toDouble()/w.height(),.08)
        assertEquals(.5,h.width().toDouble()/h.height(),.08)
        assertTrue(p.width()>160 && p.height()>160)
        assertEquals(Color.WHITE,rendered[3].getPixel(96,96))
        assertTrue(runCatching {LauncherIconRenderer.render(ColorDrawable(Color.TRANSPARENT))}.isFailure)
    }
    @Test fun appCardsSearchFavoritesAndBothThemes() {
        ui.onNodeWithTag("nav-apps").performClick()
        ui.waitUntil(10000){!container().apps.state.value.loading}
        val app=container().apps.state.value.installed.first()
        val wasFavorite=app.id in container().apps.state.value.favorites
        ui.onNodeWithTag("app-search").performTextInput(app.label)
        ui.onNodeWithTag("app-icon-${app.id}",useUnmergedTree=true).assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(68f))
        ui.onNodeWithTag("favorite-${app.packageName}").performClick()
        ui.waitUntil(5000){(app.id in container().apps.state.value.favorites)!=wasFavorite}
        ui.runOnIdle {if((app.id in container().apps.state.value.favorites)!=wasFavorite)container().apps.toggleFavorite(app.id)}
        ui.onNodeWithTag("clear-app-search").performClick()
        // Close software keyboard before visual QA; no app/vehicle command is involved.
        ui.activityRule.scenario.onActivity {activity->
            val input=activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            input.hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
        }
        for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)){
            ui.runOnIdle {container().theme.setMode(theme)}
            capture("apps-${theme.name.lowercase()}")
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
        }
    }
    @Test fun fanSliderAndPlusReachTenInDemo() {
        ui.runOnIdle {container().climate.setFan(7)}
        ui.onNodeWithTag("expand-climate").performClick()
        repeat(3){ui.onNodeWithTag("fan-plus").performClick();ui.waitForIdle()}
        ui.onNodeWithTag("fan-value").assertTextEquals("10"," / 10")
        ui.onNodeWithTag("fan-plus").performClick();assertEquals(10,container().climate.state.value.fan)
        ui.onNodeWithTag("fan-level-9").performClick();ui.waitUntil(5000){container().climate.state.value.fan==9}
        ui.onNodeWithTag("fan-level-10").performClick();ui.waitUntil(5000){container().climate.state.value.fan==10}
        capture("climate-ten-steps")
        ui.onNodeWithTag("fan-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(8f)}
        ui.waitUntil(5000){container().climate.state.value.fan==8}
        ui.onNodeWithTag("fan-value").assertTextEquals("8"," / 10")
        capture("climate-slider-eight")
        ui.runOnIdle {container().climate.setFan(3)}
    }
}
