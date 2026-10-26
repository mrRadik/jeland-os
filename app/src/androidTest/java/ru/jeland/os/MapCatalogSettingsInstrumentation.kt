package ru.jeland.os

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.Assert.*
import ru.jeland.os.navigation.*
import java.io.File
import java.security.MessageDigest

class MapCatalogSettingsInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val app get()=(ui.activity.application as JelandApplication).container
    private var originalSource=""
    private var overviewBackup:File?=null
    private var originalOverviewSource=""
    @Before fun prepare(){ui.runOnIdle {
        originalSource=app.navigation.mapCatalog.state.value.source
        app.navigation.mapCatalog.setSource(DEFAULT_MAP_SOURCE)
    }}
    @After fun restore(){
        ui.runOnIdle {app.navigation.mapCatalog.setSource(originalSource)}
        overviewBackup?.let {file->
            try {kotlinx.coroutines.runBlocking {app.navigation.maps.installOverview(android.net.Uri.fromFile(file),originalOverviewSource)}}
            finally {file.delete()}
        }
    }
    @Test fun settingsLoadPublishedMapsAndDownloadVerifiedOverview() {
        ui.waitUntil(30000){!app.navigation.preparing.value}
        assertFalse("Test runs without an active route",app.navigation.state.value.active)
        originalOverviewSource=app.navigation.maps.overviewSourceUrl.value
        if(app.navigation.maps.overviewAvailable.value)overviewBackup=File(ui.activity.cacheDir,"qa-overview-${java.util.UUID.randomUUID()}.pmtiles").also {
            app.navigation.maps.overviewFile().copyTo(it)
        }
        kotlinx.coroutines.runBlocking {
            app.navigation.maps.removeOverview()
        }
        ui.onNodeWithTag("nav-settings").performClick()
        ui.onNodeWithTag("support-project").assertDoesNotExist()
        ui.onNodeWithTag("support-project-open").assertDoesNotExist()
        ui.onNodeWithTag("map-catalog-source").performScrollTo().assertTextContains(DEFAULT_MAP_SOURCE)
        ui.onNodeWithTag("map-catalog-open").performScrollTo().performClick()
        ui.onNodeWithTag("map-catalog-dialog").assertIsDisplayed()
        ui.onNodeWithTag("map-catalog-source-in-list").assertIsDisplayed().assertTextContains(DEFAULT_MAP_SOURCE)
        ui.waitUntil(45000){app.navigation.mapCatalog.state.value.let {!it.loading&&(it.loaded||it.error.isNotBlank())}}
        val catalog=app.navigation.mapCatalog.state.value
        assertEquals("Public catalog error", "",catalog.error)
        assertTrue("Published packages appear",catalog.maps.any {it.id=="russia-overview"})
        val folder=File(ui.activity.getExternalFilesDir(null),"qa-map-catalog").apply {mkdirs()}
        ui.onNodeWithTag("map-catalog-dialog").captureToImage().asAndroidBitmap().let {bitmap->
            File(folder,"catalog.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        ui.onNodeWithTag("map-download-url").assertDoesNotExist()
        ui.onNodeWithTag("map-download-sha256").assertDoesNotExist()
        ui.onNodeWithTag("map-download-russia-overview").performScrollTo().performClick()
        ui.onAllNodesWithTag("map-download-mobile-confirm").fetchSemanticsNodes().firstOrNull()?.let {
            ui.onNodeWithTag("map-download-mobile-confirm").performClick()
        }
        ui.waitUntil(120000){app.navigation.downloads.state.value.let {it.installed||(!it.running&&it.ready==null&&it.sourceUrl.isNotBlank()&&!app.navigation.importing.value)}}
        val result=app.navigation.downloads.state.value
        assertTrue(result.status,result.installed)
        val expected=catalog.maps.single {it.id=="russia-overview"}
        val digest=MessageDigest.getInstance("SHA-256")
        app.navigation.maps.overviewFile().inputStream().use {input->val block=ByteArray(65536);while(true){val n=input.read(block);if(n<0)break;digest.update(block,0,n)}}
        assertEquals(expected.sha256,digest.digest().joinToString(""){"%02x".format(it)})
        assertFalse(app.navigation.downloads.state.value.autoInstall)
        assertEquals(expected.url,app.navigation.maps.overviewSourceUrl.value)
        ui.onNodeWithTag("map-delete-russia-overview").performScrollTo().assertIsEnabled()
        ui.onNodeWithTag("map-catalog-dialog").captureToImage().asAndroidBitmap().let {bitmap->
            File(folder,"installed.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        ui.onNodeWithTag("map-delete-russia-overview").performClick()
        ui.onNodeWithTag("map-delete-confirm").performClick()
        ui.waitUntil(15000){!app.navigation.importing.value&&!app.navigation.maps.overviewAvailable.value}
        assertFalse(app.navigation.maps.overviewFile().exists())
        ui.onNodeWithTag("map-download-russia-overview").assertIsEnabled()
        ui.onNodeWithTag("map-catalog-close").performClick()
        ui.onNodeWithTag("map-catalog-source").performScrollTo().performTextReplacement("https://example.org/maps")
        assertEquals("https://example.org/maps",app.navigation.mapCatalog.state.value.source)
        ui.onNodeWithTag("map-catalog-source").performTextReplacement("invalid-source")
        ui.onNodeWithTag("map-catalog-open").performScrollTo().performClick()
        ui.onNodeWithTag("map-delete-${app.navigation.maps.region.value!!.id}").assertIsDisplayed()
        ui.onNodeWithTag("map-catalog-close").performClick()
        ui.onNodeWithTag("map-catalog-source").performTextReplacement("https://github.com/mrRadik/jeland-os/releases/download/maps-2026-10-26/maps.json")
        // This is a direct JSON catalog, not a GitHub repository/release listing.
        ui.onNodeWithTag("map-catalog-open").performScrollTo().performClick()
        ui.waitUntil(45000){app.navigation.mapCatalog.state.value.let {!it.loading&&(it.loaded||it.error.isNotBlank())}}
        assertEquals("",app.navigation.mapCatalog.state.value.error)
        assertEquals(2,app.navigation.mapCatalog.state.value.maps.size)
        ui.onNodeWithTag("map-catalog-close").performClick()
    }
}
