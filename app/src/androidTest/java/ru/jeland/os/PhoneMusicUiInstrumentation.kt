package ru.jeland.os

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.data.MockMediaRepository
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.*
import java.io.File

class PhoneMusicUiInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun container()=(ui.activity.application as JelandApplication).container
    @Before fun isolate(){
        ui.runOnIdle {container().car.useVehicle(false)}
        ui.waitUntil(10000){ui.onRoot().fetchSemanticsNode().boundsInRoot.let {it.height>it.width}}
    }
    @Test fun livePlayerShowsActualTrackAndUsesFeedbackInBothThemes() {
        val calls=mutableListOf<String>();val delegate=MockMediaRepository(MemorySettingsStore())
        val repository=object:MediaRepository by delegate {
            override fun togglePlay(){calls+="toggle"};override fun next(){calls+="next"};override fun previous(){calls+="previous"}
        }
        var dark by mutableStateOf(true)
        var sample by mutableStateOf(MediaState(live=true,liveTrack=Track("The Best Is yet to Come","All Good Things",249),
            playing=true,source="CarPlay",position=80,status="CarPlay · управление телефоном"))
        val model=MainViewModel(container())
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(dark) {
            Box(Modifier.fillMaxWidth().padding(20.dp)){MediaWidget(sample,model,Modifier.height(340.dp),expanded=true,repository=repository)}
        }}}
        for(theme in listOf(true,false)) {
            ui.runOnIdle {dark=theme;sample=sample.copy(playing=true)}
            ui.onNodeWithTag("media-title").assertTextEquals("The Best Is yet to Come")
            ui.onNodeWithTag("media-status").assertTextEquals("CarPlay")
            ui.onNodeWithTag("media-seek").assertIsNotEnabled()
            ui.onNodeWithTag("media-play").assertIsEnabled().performClick()
            ui.onNodeWithContentDescription("Пауза").assertExists() // no optimistic playback flip
            ui.runOnIdle {sample=sample.copy(playing=false)}
            ui.onNodeWithContentDescription("Воспроизвести").assertExists()
            ui.onNodeWithTag("media-next").performClick();ui.onNodeWithTag("media-previous").performClick()
            val file=File(ui.activity.getExternalFilesDir(null),"qa-0.6.8/music-${if(theme)"dark"else"light"}.png").apply {parentFile!!.mkdirs()}
            file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        assertEquals(listOf("toggle","next","previous","toggle","next","previous"),calls)
        ui.runOnIdle {sample=sample.copy(controlsEnabled=false)}
        ui.onNodeWithTag("media-play").assertIsNotEnabled();ui.onNodeWithTag("media-next").assertIsNotEnabled();ui.onNodeWithTag("media-previous").assertIsNotEnabled()
    }
    @Test fun liveMusicScreenHasOnlyReviewedRoutesNotDemoTracksOrFakeVolume() {
        val model=MainViewModel(container())
        val sample=UiState(media=MediaState(live=true,controlsEnabled=false,position=0,positionKnown=false,status="Bluetooth · запустите музыку штатно"))
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true){MusicScreen(sample,model)}}}
        ui.onNodeWithTag("media-title").assertTextEquals("Музыка")
        ui.onNodeWithTag("music-active-source").assertTextEquals("Bluetooth")
        ui.onNodeWithText("Запустите музыку в штатном приложении").assertDoesNotExist()
        ui.onNodeWithTag("media-source-CarPlay").assertDoesNotExist()
        ui.onNodeWithTag("media-source-Bluetooth").assertDoesNotExist()
        ui.onNodeWithTag("media-source-USB").assertDoesNotExist();ui.onNodeWithTag("media-source-Радио").assertDoesNotExist()
        ui.onNodeWithTag("media-volume").assertDoesNotExist();ui.onNodeWithTag("media-track-0").assertDoesNotExist()
        ui.onNodeWithTag("media-play").assertIsNotEnabled()
    }
}
