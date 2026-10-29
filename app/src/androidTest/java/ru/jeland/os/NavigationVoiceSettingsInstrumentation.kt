package ru.jeland.os

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.core.ThemeMode
import ru.jeland.os.navigation.NavigationSpeaker
import java.io.File

class NavigationVoiceSettingsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=(ui.activity.application as JelandApplication).container
    @Test fun speakerSelectionAndLicenseInBothThemes() {
        ui.runOnIdle {app.car.useVehicle(false);app.navigation.stop();app.navigation.voice.setEnabled(false)}
        ui.onNodeWithTag("nav-navigation").performClick()
        ui.onNodeWithTag("navigation-options").performClick()
        for(theme in listOf(ThemeMode.DARK,ThemeMode.LIGHT)) {
            ui.runOnIdle {app.theme.setMode(theme)}
            ui.onNodeWithTag("navigation-voice-1").performScrollTo().performClick()
            ui.runOnIdle {assertEquals(NavigationSpeaker.FEMALE,app.navigation.voice.speaker.value)}
            ui.onNodeWithTag("navigation-voice-7").performClick()
            ui.runOnIdle {assertEquals(NavigationSpeaker.MALE,app.navigation.voice.speaker.value)}
            ui.onNodeWithTag("navigation-voice-speed").assertIsDisplayed()
            ui.onNodeWithTag("climate-bar").assertIsDisplayed()
            val folder=File(ui.activity.getExternalFilesDir(null),"qa-${BuildConfig.VERSION_NAME.substringBefore('-')}").apply {mkdirs()}
            val descriptor=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p ${folder.absolutePath}/voice-${theme.name.lowercase()}.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {while(it.read()!=-1){}}
            assertTrue(File(folder,"voice-${theme.name.lowercase()}.png").length()>10000)
        }
        ui.onNodeWithTag("navigation-voice-license").performScrollTo().performClick()
        ui.onNodeWithTag("navigation-voice-license-accept").assertIsDisplayed()
        ui.onNodeWithText("LICENSE.txt",substring=true).assertExists()
        ui.onNodeWithContentDescription("Закрыть настройки карты").performClick()
        // Inspecting terms alone does not synthesize or change the selected voice.
        ui.runOnIdle {assertFalse(app.navigation.voice.playing.value)}
    }
}
