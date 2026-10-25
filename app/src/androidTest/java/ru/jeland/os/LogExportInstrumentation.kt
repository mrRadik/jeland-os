package ru.jeland.os

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import ru.jeland.os.ui.MainViewModel
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class LogExportInstrumentation {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun saveButtonWritesRealZipToDownloadsWithoutCarConnectionOrPermissions() {
        val container=(ui.activity.application as JelandApplication).container
        ui.runOnIdle {container.car.useVehicle(false)}
        ui.waitUntil(5000){ui.onAllNodes(isRoot()).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.height>it.width}==true}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        var model:MainViewModel?=null
        ui.activityRule.scenario.onActivity {model=ViewModelProvider(it,MainViewModel.Factory(container))[MainViewModel::class.java]}
        ui.onNodeWithTag("nav-settings").performClick()
        ui.waitUntil(5000){ui.onAllNodesWithTag("save-os-logs").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("save-os-logs").performScrollTo().performClick()
        ui.waitUntil(10000){model!!.savedLogs.value!=null && !model!!.savingLogs.value}
        val saved=model!!.savedLogs.value!!
        try {
            ui.onNodeWithTag("os-logs-result").assertTextContains("Download/JelandOS/",substring=true)
            ui.onNodeWithTag("share-os-logs").assertExists()
            assertTrue(saved.logCount>0)
            val entries=linkedMapOf<String,String>()
            context.contentResolver.openInputStream(saved.uri)!!.use {input->ZipInputStream(input).use {zip->
                while(true){val entry=zip.nextEntry?:break;entries[entry.name]=zip.readBytes().toString(Charsets.UTF_8);zip.closeEntry()}
            }}
            val metadata=JSONObject(entries.getValue("diagnostics.json"))
            assertEquals("DEMO",metadata.getString("mode"));assertFalse(metadata.getBoolean("writesEnabled"))
            assertEquals(BuildConfig.VERSION_NAME,metadata.getString("appVersion"))
            assertTrue(entries.keys.any {it.startsWith("journals/JelandOS_vehicle_")})
            assertTrue(entries.filterKeys {it.endsWith(".log")}.values.any {it.contains("SESSION_BEGIN") && it.contains("EXPORT_REQUEST")})
            assertTrue(entries.keys.none {it.contains("preferences") || it.endsWith(".apk") || it.contains("..")})
            assertTrue(container.car.link.value.demo);assertFalse(container.car.link.value.writesEnabled)
            val folder=File(context.getExternalFilesDir(null),"qa-0.4.5").apply {mkdirs()}
            context.contentResolver.openInputStream(saved.uri)!!.use {input->File(folder,"export-test.zip").outputStream().use {input.copyTo(it)}}
            File(folder,"export-result.txt").writeText("PASS; journals=${saved.logCount}; ${saved.location}; no new vehicle connection")
        } finally {
            // Delete only the public test archive created by this test; preserve every source journal.
            context.contentResolver.delete(saved.uri,null,null)
        }
    }
}
