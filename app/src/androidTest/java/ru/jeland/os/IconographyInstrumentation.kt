package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class IconographyInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun portraitDemo() {
        ui.runOnIdle {container().car.useVehicle(false);container().theme.setMode(ThemeMode.DARK)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    private fun save(name:String,bitmap:Bitmap=ui.onRoot().captureToImage().asAndroidBitmap()) {
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-0.6.4").apply {mkdirs()}
        File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun standardAndAutomotivePictogramsHaveClearSilhouettesInBothThemes() {
        val icons=listOf("Главная" to JIcons.Home,"Музыка" to JIcons.MusicNote,"Навигация" to JIcons.Navigation,"Автомобиль" to JIcons.DirectionsCar,
            "Правила" to JIcons.Automations,"Приложения" to JIcons.Apps,"Настройки" to JIcons.Settings,"Вентилятор" to JIcons.Fan,
            "Питание" to JIcons.Power,"A/C" to JIcons.Snowflake,"Сиденье" to JIcons.AirlineSeatReclineNormal,"В лицо" to JIcons.AirFace,"В ноги" to JIcons.AirFeet,
            "Обдув стекла" to JIcons.Waves,"Заднее тепло" to JIcons.RearDefrost,"Лобовое тепло" to JIcons.WindshieldHeat,"Рециркуляция" to JIcons.Loop,
            "Обогрев руля" to JIcons.Steering,"Шторка" to JIcons.Shade,"Камера 360" to JIcons.Cameraswitch,"Зеркала" to JIcons.SwapHoriz,
            "Топливо" to JIcons.Fuel,"Погода" to JIcons.Cloud,"Нравится" to JIcons.Favorite,"Избранное" to JIcons.Star,"Play" to JIcons.PlayArrow,
            "Pause" to JIcons.Pause,"Далее" to JIcons.SkipNext,"Назад" to JIcons.SkipPrevious,"Поиск" to JIcons.Search,"Обновить" to JIcons.Refresh,
            "Wi-Fi" to JIcons.Wifi,"Bluetooth" to JIcons.Bluetooth,"Нет Wi-Fi" to JIcons.WifiOff,"Внимание" to JIcons.Warning,"Дверь" to JIcons.Door,
            "Капот" to JIcons.Hood,"Багажник" to JIcons.Trunk,"Удалить" to JIcons.Delete,"Изменить" to JIcons.Edit,"Луна" to JIcons.DarkMode,"Солнце" to JIcons.LightMode)
        for(dark in listOf(true,false)) {
            ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
                Column(Modifier.fillMaxWidth().background(Jeland.colors.panel).padding(16.dp)) {
                    icons.chunked(6).forEach {row->Row(Modifier.fillMaxWidth()) {row.forEach {(name,icon)->
                        Column(Modifier.weight(1f).height(80.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Icon(icon,null,Modifier.size(34.dp),tint=Jeland.colors.muted)
                            Text(name,color=Jeland.colors.text,fontSize=10.sp,maxLines=1,modifier=Modifier.padding(top=7.dp))
                        }
                    }}}
                }
            }}}
            ui.waitForIdle();save("icon-atlas-${if(dark)"dark"else"light"}")
        }
    }
    @Test fun fanActuallyMovesAndStopsWhenPowerOrDataIsMissing() {
        var state by mutableStateOf(ClimateState(power=true,fan=5))
        // Infinite animations are cancelled by the test policy when autoAdvance is on.
        // Use manual time BEFORE composing the animation, not after that cancellation.
        ui.mainClock.autoAdvance=false
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {Box(Modifier.size(160.dp).background(Jeland.colors.panel),contentAlignment=Alignment.Center) {
            ClimateFan(state,Modifier.size(80.dp).testTag("test-fan"))
        }}}}
        ui.mainClock.advanceTimeBy(32)
        ui.onNodeWithTag("test-fan").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Вращается"))
        val a=ui.onNodeWithTag("test-fan").captureToImage().asAndroidBitmap()
        ui.mainClock.advanceTimeBy(240)
        val b=ui.onNodeWithTag("test-fan").captureToImage().asAndroidBitmap()
        assertFalse("Fan must rotate rather than remain a static glyph",a.sameAs(b));save("fan-frame-a",a);save("fan-frame-b",b)
        ui.runOnIdle {state=state.copy(power=false)};ui.mainClock.advanceTimeBy(32)
        ui.onNodeWithTag("test-fan").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Остановлен"))
        val stopped=ui.onNodeWithTag("test-fan").captureToImage().asAndroidBitmap();ui.mainClock.advanceTimeBy(300)
        assertTrue(stopped.sameAs(ui.onNodeWithTag("test-fan").captureToImage().asAndroidBitmap()))
        ui.runOnIdle {state=state.copy(power=true,available=setOf(18))};ui.mainClock.advanceTimeBy(32)
        ui.onNodeWithTag("test-fan").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Остановлен"))
        ui.mainClock.autoAdvance=true
    }
    @Test fun dockTemperaturesAreIntegerAndCenteredBetweenButtons() {
        for(driver in listOf(true,false)) {
            val tag=if(driver)"driver"else"passenger"
            ui.onNodeWithTag("$tag-temp").assertTextEquals(climateDockTemperature(container().climate.state.value,driver))
            val text=ui.onNodeWithTag("$tag-temp").fetchSemanticsNode().boundsInRoot
            val minus=ui.onNodeWithTag("$tag-minus").fetchSemanticsNode().boundsInRoot
            val plus=ui.onNodeWithTag("$tag-plus").fetchSemanticsNode().boundsInRoot
            assertEquals((minus.center.x+plus.center.x)/2,text.center.x,1.1f)
        }
        save("home-integer-dock")
        ui.onNodeWithTag("expand-climate").performClick();save("climate-new-icons")
    }
}
