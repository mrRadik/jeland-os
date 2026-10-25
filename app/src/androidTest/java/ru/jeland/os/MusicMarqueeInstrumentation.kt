package ru.jeland.os

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.*
import ru.jeland.os.data.MockMediaRepository
import ru.jeland.os.ui.*
import ru.jeland.os.ui.theme.JelandTheme

class MusicMarqueeInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun metadataIsSingleLineMovesOnOverflowAndKeepsCardHeight() {
        ui.waitUntil(10000){ui.onRoot().fetchSemanticsNode().boundsInRoot.let {it.height>it.width}}
        val app=(ui.activity.application as JelandApplication).container
        val model=MainViewModel(app)
        val repo=MockMediaRepository(MemorySettingsStore())
        var sample by mutableStateOf(MediaState(live=true,liveTrack=Track("Коротко","Артист",200)))
        ui.activityRule.scenario.onActivity {it.setContent {JelandTheme(true) {
            Box(Modifier.width(360.dp)){MediaWidget(sample,model,repository=repo)}
        }}}
        val height=ui.onNodeWithTag("music-widget").fetchSemanticsNode().boundsInRoot.height
        ui.mainClock.autoAdvance=false
        try {
            ui.runOnIdle {sample=sample.copy(liveTrack=Track("Очень длинное название композиции, которое должно плавно прокручиваться целиком",
                "Исполнитель с очень длинным названием и приглашённые музыканты",200))}
            ui.mainClock.advanceTimeBy(32)
            for(tag in listOf("media-title","media-artist")) {
                val layouts=mutableListOf<TextLayoutResult>()
                ui.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
                assertEquals(1,layouts.single().lineCount)
                assertFalse(layouts.single().isLineEllipsized(0))
            }
            val before=ui.onNodeWithTag("media-title").captureToImage().asAndroidBitmap()
            ui.mainClock.advanceTimeBy(3000)
            val after=ui.onNodeWithTag("media-title").captureToImage().asAndroidBitmap()
            assertFalse("Overflowing title visibly scrolls",before.sameAs(after))
            assertEquals("Metadata cannot increase card height",height,ui.onNodeWithTag("music-widget").fetchSemanticsNode().boundsInRoot.height,1f)
            ui.runOnIdle {sample=sample.copy(liveTrack=Track("Новый","Артист",200))}
            ui.mainClock.advanceTimeBy(32)
            ui.onNodeWithTag("media-title").assertTextEquals("Новый")
        } finally {ui.mainClock.autoAdvance=true}
    }
}
