package ru.jeland.os

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.navigation.NavigationDownloads

class NavigationDownloadInstrumentation {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture="https://raw.githubusercontent.com/protomaps/PMTiles/main/spec/v3/spec.md"
    @Test fun realHttpsTransferSurvivesControllerRecreationAndRequiresInstall()=runBlocking<Unit> {
        val name="navigation-downloads-qa-${java.util.UUID.randomUUID()}"
        var downloads=NavigationDownloads(context,name)
        try {
            downloads.start(fixture,"region","",true)
            withTimeout(30000){downloads.state.first {it.systemOwned||!it.running}.also {assertTrue(it.status,it.systemOwned)}}
            downloads.close() // Android, not this coroutine, must own the transfer.
            downloads=NavigationDownloads(context,name)
            val result=withTimeout(60000){downloads.state.first {!it.running&&it.status.isNotBlank()}}
            assertNotNull(result.status,result.ready)
            assertTrue(result.received>1000)
            assertEquals(result.received,result.total)
            assertEquals(fixture,result.sourceUrl)
            assertFalse(result.autoInstall)
            context.contentResolver.openInputStream(result.ready!!).use {assertTrue(it!!.bufferedReader().readText().startsWith("# PMTiles"))}
            // It is deliberately not a map: a downloaded file is not automatically installed.
        }finally {downloads.cancel();downloads.close();context.deleteSharedPreferences(name)}
    }
    @Test fun wrongSha256CannotProduceInstallableDownload()=runBlocking<Unit> {
        val name="navigation-downloads-qa-${java.util.UUID.randomUUID()}"
        val downloads=NavigationDownloads(context,name)
        try {
            downloads.start(fixture,"region","0".repeat(64),true)
            val result=withTimeout(60000){downloads.state.first {!it.running&&it.status.isNotBlank()}}
            assertNull(result.ready)
            assertTrue(result.status,result.status.contains("сумма"))
        }finally {downloads.cancel();downloads.close();context.deleteSharedPreferences(name)}
    }
}
