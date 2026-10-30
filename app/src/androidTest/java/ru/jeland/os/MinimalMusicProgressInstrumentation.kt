package ru.jeland.os

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.ui.MediaWidget
import ru.jeland.os.ui.MainViewModel
import ru.jeland.os.ui.theme.JelandTheme

class MinimalMusicProgressInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()

    @Test fun liveTimelineFitsBothLayoutsAndThemesAndDoesNotInventUnknownProgress() {
        val app=(ui.activity.application as JelandApplication).container
        val model=MainViewModel(app)
        var dark by mutableStateOf(true)
        var width by mutableStateOf(260)
        var media by mutableStateOf(MediaState(live=true,liveTrack=Track("Track","Artist",240),position=60,source="CarPlay"))
        ui.activityRule.scenario.onActivity {it.setContent {
            JelandTheme(dark,DashboardStyle.MINIMAL) {
                Box(Modifier.width(width.dp).height(198.dp)) {
                    MediaWidget(media,model,Modifier.fillMaxSize(),compact=true)
                }
            }
        }}
        for(theme in listOf(true,false))for(size in listOf(260,440)) {
            ui.runOnIdle {dark=theme;width=size}
            ui.onNodeWithTag("minimal-music-progress").assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(.25f,0f..1f)))
            ui.onNodeWithTag("minimal-music-elapsed").assertTextEquals("1:00")
            ui.onNodeWithTag("minimal-music-duration").assertTextEquals("4:00")
            val widget=ui.onNodeWithTag("music-widget").fetchSemanticsNode().boundsInRoot
            for(tag in listOf("media-title","media-play","minimal-music-progress","minimal-music-elapsed","media-status")) {
                val bounds=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag must fit at $size dp: $bounds vs $widget",bounds.top>=widget.top&&bounds.bottom<=widget.bottom)
            }
        }
        ui.runOnIdle {media=media.copy(position=120)}
        ui.onNodeWithTag("minimal-music-progress")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,ProgressBarRangeInfo(.5f,0f..1f)))
        ui.runOnIdle {media=media.copy(positionKnown=false,liveTrack=Track("Radio","",0))}
        ui.onNodeWithTag("minimal-music-elapsed").assertTextEquals("—:—")
        ui.onNodeWithTag("minimal-music-duration").assertTextEquals("—:—")
        ui.onNodeWithTag("minimal-music-progress").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ProgressBarRangeInfo))
    }
}
