package ru.jeland.os

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.data.AndroidSettingsStore
import ru.jeland.os.data.updates.*
import ru.jeland.os.localization.AppLocalization
import ru.jeland.os.ui.WhatsNewHost
import ru.jeland.os.ui.theme.JelandTheme
import java.io.File

class UpdateNewsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun cleanupPreservesReleaseNotesAndModalAppearsOnlyOnceOffline() {
        val original=ui.activity
        val prefs=original.getSharedPreferences("update-news-123-test",Context.MODE_PRIVATE)
        val folder=File(original.cacheDir,"update-news-123-test").apply {mkdirs()}
        val context=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int)=prefs
            override fun getFilesDir()=folder
        }
        prefs.edit().clear().commit()
        val store=AndroidSettingsStore(context)
        val version=BuildConfig.VERSION_CODE.toLong()
        val root="https://github.com/mrRadik/jeland-os/releases/download/test/"
        val manifest=AppUpdateManifest(1,"ru.jeland.os",version,BuildConfig.VERSION_NAME,30,"JelandOS-test.apk",200,"a".repeat(64))
        val notes="# Изменения\n- Компактная панель приборки\n- Уведомления внутри островка"
        val candidate=AppUpdatePolicy.candidate(manifest,listOf(UpdateAsset(manifest.apkName,root+manifest.apkName,200)),"test",root+AppUpdatePolicy.MANIFEST,notes)
        val json=GitHubUpdateCodec.pending(candidate)
        assertEquals(notes,GitHubUpdateCodec.restore(json).releaseNotes)
        assertEquals("",GitHubUpdateCodec.restore(org.json.JSONObject(json).apply {remove("releaseNotes")}.toString()).releaseNotes)
        store.putDurable("updates.pending",json)
        val apk=File(folder,"updates/ready/JelandOS-$version.apk").apply {parentFile!!.mkdirs();writeText("test")}
        UpdateNewsTracker(store).arm(version-1,UpdateNews(version,BuildConfig.VERSION_NAME,notes))
        InstalledUpdateCleanup.run(context)
        assertFalse(apk.exists());assertEquals("",store.string("updates.pending",""))
        assertEquals(notes,UpdateNewsTracker(AndroidSettingsStore(context)).pending(version)!!.body)
        val repository=UpdateNewsRepository(AndroidSettingsStore(context),version)
        ui.runOnIdle {original.setContent {AppLocalization("ru") {JelandTheme(true) {Box(Modifier.fillMaxSize().testTag("news-host")){WhatsNewHost(repository)}}}}}
        ui.onNodeWithTag("whats-new-dialog").assertIsDisplayed()
        ui.onNodeWithText("Что нового").assertExists()
        ui.onNodeWithTag("whats-new-body").assertTextEquals("Изменения\n• Компактная панель приборки\n• Уведомления внутри островка")
        ui.waitForIdle()
        assertNull(UpdateNewsRepository(AndroidSettingsStore(context),version).notice.value)
        val image=ui.onNodeWithTag("whats-new-dialog").captureToImage().asAndroidBitmap()
        val output=File(original.getExternalFilesDir(null),"qa-cluster-123").apply {mkdirs()}
        File(output,"whats-new-ru.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
        ui.onNodeWithTag("whats-new-close").performClick()
        ui.onNodeWithTag("whats-new-dialog").assertDoesNotExist()
        InstalledUpdateCleanup.run(context)
        assertNull(UpdateNewsRepository(AndroidSettingsStore(context),version).notice.value)
        // A regular/manual APK install without an updater receipt must stay quiet.
        prefs.edit().clear().commit();store.putDurable("updates.pending",json)
        InstalledUpdateCleanup.run(context)
        assertNull(UpdateNewsRepository(AndroidSettingsStore(context),version).notice.value)
        // Empty release descriptions still have a localized, useful UI.
        UpdateNewsTracker(store).apply {arm(version-1,UpdateNews(version,BuildConfig.VERSION_NAME));captureInstalled(version)}
        val english=UpdateNewsRepository(store,version)
        ui.runOnIdle {original.setContent {AppLocalization("en") {JelandTheme(false) {WhatsNewHost(english)}}}}
        ui.onNodeWithText("What's new").assertExists()
        ui.onNodeWithText("Got it").assertExists()
        ui.onNodeWithTag("whats-new-close").performClick()
        prefs.edit().clear().commit()
    }
}
