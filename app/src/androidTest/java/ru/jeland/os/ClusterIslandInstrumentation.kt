package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.cluster.*
import java.io.File

class ClusterIslandInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun islandAnimatesBothWaysWithoutMovingInstrumentsAndFitsLongWarnings() {
        val panel=ClusterPanelState(speed=90.0,rpm=1600.0,gear="D7",outside=-12.5,indicators=emptyList())
        val warning=mutableStateOf<ClusterOemMessage?>(null)
        ui.runOnIdle {ui.activity.setContent {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1920f/720f).testTag("island-scene")) {
                ClusterArtwork(panel.copy(message=warning.value),ClusterDesign.CLASSIC,true,"20:35",Modifier.matchParentSize())
            }
        }}
        ui.waitForIdle()
        ui.onNodeWithTag("island-scene").assertExists()
        ui.mainClock.autoAdvance=false
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-cluster-123").apply {mkdirs()}
        fun capture(name:String):Bitmap {
            val image=ui.onNodeWithTag("island-scene").captureToImage().asAndroidBitmap()
            File(folder,"island-$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
            return image
        }
        val closed=capture("closed")
        ui.runOnIdle {warning.value=ClusterOemMessage(1,"Открыта передняя правая дверь","Front right door open",false)}
        ui.mainClock.advanceTimeByFrame();ui.mainClock.advanceTimeBy(100)
        val middle=capture("expanding")
        ui.mainClock.advanceTimeBy(400)
        val opened=capture("opened")
        assertFalse("Opening must animate, not jump to final bounds",middle.sameAs(opened))
        assertFalse(closed.sameAs(opened))
        ui.onNodeWithTag("cluster-header-gear").assertTextEquals("D7")
        ui.onNodeWithTag("cluster-header-outside").assertTextEquals(String.format(java.util.Locale.getDefault(),"%.1f °C",-12.5))
        fun unchangedOutsideCapsule(a:Bitmap,b:Bitmap) {
            for(y in 0 until a.height)for(x in 0 until a.width) {
                if(y>a.height*.34f||x<a.width*.28f||x>a.width*.72f) {
                    if(a.getPixel(x,y)!=b.getPixel(x,y))fail("Instrument moved at $x,$y")
                }
            }
        }
        unchangedOutsideCapsule(closed,opened)
        ui.runOnIdle {warning.value=ClusterOemMessage(2,"Неисправность тормозной системы. Остановите автомобиль в безопасном месте. Обратитесь в сервисный центр для проверки системы.","Brake system fault",true)}
        ui.mainClock.advanceTimeBy(450)
        val long=capture("long-critical")
        unchangedOutsideCapsule(closed,long)
        ui.runOnIdle {warning.value=null}
        ui.mainClock.advanceTimeByFrame();ui.mainClock.advanceTimeBy(100)
        val shrinking=capture("shrinking")
        assertFalse(shrinking.sameAs(closed))
        ui.mainClock.advanceTimeBy(450)
        val restored=capture("restored")
        assertTrue("Closing must restore the original compact scene",closed.sameAs(restored))
        ui.mainClock.autoAdvance=true
    }
}
