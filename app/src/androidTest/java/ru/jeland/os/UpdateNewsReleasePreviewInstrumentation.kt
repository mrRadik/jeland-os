package ru.jeland.os

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.AndroidSettingsStore
import ru.jeland.os.data.updates.UpdateNewsRepository
import ru.jeland.os.localization.AppLocalization
import ru.jeland.os.ui.WhatsNewHost
import ru.jeland.os.ui.theme.Jeland
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

/** Uses the exact proposed GitHub release body and the production dialog. */
class UpdateNewsReleasePreviewInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun releaseNotesRenderInBothThemesAndAreShownOnce() {
        val activity=ui.activity
        val notes=InstrumentationRegistry.getInstrumentation().context.assets
            .open("release-notes-1.4.1.md").bufferedReader().use {it.readText()}
        val bundled=activity.assets.open("updates/135.md").bufferedReader().use {it.readText()}
        assertEquals(plainReleaseNotes(notes),plainReleaseNotes(bundled))
        val prefs=activity.getSharedPreferences("update-news-release-preview",Context.MODE_PRIVATE)
        val context=object:ContextWrapper(activity) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=prefs
        }
        val store=AndroidSettingsStore(context)
        val folder=File(activity.getExternalFilesDir(null),"qa-release-1.4.1").apply {mkdirs()}
        try {
            for(body in listOf(notes,"")) for(dark in listOf(true,false)) {
                prefs.edit().clear().commit()
                UpdateNewsTracker(store).apply {arm(134,UpdateNews(135,"1.4.1",body));captureInstalled(135)}
                val repository=UpdateNewsRepository(store,135)
                ui.runOnIdle {activity.setContent {
                    AppLocalization("ru") {JelandTheme(dark) {
                        Box(Modifier.fillMaxSize().background(Jeland.colors.background)) {WhatsNewHost(repository)}
                    }}
                }}
                ui.onNodeWithTag("whats-new-dialog").assertIsDisplayed()
                ui.onNodeWithText("Jeland OS · 1.4.1").assertIsDisplayed()
                ui.onNodeWithTag("whats-new-body").assertTextEquals(plainReleaseNotes(notes))
                ui.onNodeWithTag("whats-new-close").assertIsDisplayed()
                ui.waitForIdle()
                val image=ui.onNodeWithTag("whats-new-dialog").captureToImage().asAndroidBitmap()
                val theme=if(dark)"dark"else"light"
                val legacy=if(body.isBlank())"-legacy"else""
                File(folder,"whats-new-$theme$legacy.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG,100,it)
                }
                assertNull(UpdateNewsRepository(store,135).notice.value)
                ui.onNodeWithTag("whats-new-close").performClick()
                ui.onNodeWithTag("whats-new-dialog").assertDoesNotExist()
            }
        }finally {prefs.edit().clear().commit()}
    }
}
