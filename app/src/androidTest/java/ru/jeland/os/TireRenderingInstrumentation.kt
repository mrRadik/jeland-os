package ru.jeland.os

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.jeland.os.core.*
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class TireRenderingInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun stableLocalDemo() {
        ui.runOnIdle {(ui.activity.application as JelandApplication).container.car.useVehicle(false)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
    }
    private fun show(vehicle:VehicleState,dark:Boolean) {
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
            Panel(Modifier.fillMaxWidth().padding(20.dp)) {
                Column(Modifier.padding(22.dp)){TireDetailsPopup(Wheel.FRONT_LEFT,vehicle)}
            }
        }}}
    }
    @Test fun wheelIsRoundAndReadableInBothThemes() {
        val asset=BitmapFactory.decodeResource(ui.activity.resources,R.drawable.wheel_realistic)
        assertNotNull(asset);assertTrue("Realistic wheel must preserve transparency",asset.hasAlpha())
        for(x in listOf(0,asset.width-1))for(y in listOf(0,asset.height-1))assertEquals("No rectangular background",0,Color.alpha(asset.getPixel(x,y)))
        asset.recycle()
        for(dark in listOf(true,false)) {
            show(VehicleState(),dark)
            ui.onNodeWithTag("tire-detail-pressure").assertTextEquals("2.4 бар")
            ui.onNodeWithTag("tire-detail-temperature").assertTextEquals("18 °C")
            val icon=ui.onNodeWithTag("tire-detail-icon").assertIsDisplayed().captureToImage().asAndroidBitmap()
            assertEquals(icon.width,icon.height)
            val root=InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            val folder=File(root,"qa-0.6.3").apply {mkdirs()}
            File(folder,"wheel-${if(dark)"dark"else"light"}.png").outputStream().use {icon.compress(Bitmap.CompressFormat.PNG,100,it)}
            File(folder,"tire-popup-${if(dark)"dark"else"light"}.png").outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
    }
    @Test fun livePressureNeverInventsTyreTemperatureOrReusesDemo() {
        val reducer=LiveVehicleReducer()
        reducer.accept(VdsKey(VerifiedVehicleProtocol.INFO,140),listOf(179,175,182,171),100)
        // These are named units/warnings in the OEM SDK, not temperature readings.
        reducer.accept(VdsKey(VerifiedVehicleProtocol.INFO,143),listOf(0),100)
        reducer.accept(VdsKey(VerifiedVehicleProtocol.INFO,144),listOf(21,12,16,12),100)
        val live=reducer.vehicle(101)
        assertTrue(live.tires.values.all {it.temperature==null})
        show(live,true)
        ui.onNodeWithTag("tire-detail-temperature").assertTextEquals("Нет данных")
        assertTrue(live.tires.getValue(Wheel.FRONT_LEFT).validBar!=null)
    }
}
