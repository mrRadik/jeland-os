package ru.jeland.os

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import ru.jeland.os.navigation.*

class MapCatalogInstrumentation {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun catalog(url:String="region.jelandnav.zip",sha:String="a".repeat(64))=JSONObject().put("format",1).put("maps",JSONArray().put(
        JSONObject().put("id","test-region").put("title","Тестовый регион").put("url",url).put("bytes",1024).put("sha256",sha))).toString()
    @Test fun customDirectoryResolvesRelativeFilesAndRejectsUnsafeMetadata() {
        val source=mapCatalogSource("https://example.org/maps/")
        assertEquals("https://example.org/maps/maps.json",source.url)
        assertFalse(mapCatalogSource("https://github.com/mrRadik/jeland-os/releases/download/maps/maps.json").github)
        val map=parseMapCatalog(catalog(),source).single()
        assertEquals("https://example.org/maps/region.jelandnav.zip",map.url)
        assertEquals("a".repeat(64),map.sha256)
        for(invalid in listOf("file:///tmp/map.zip","http://example.org/map.zip","https://user:password@example.org/map.zip")) {
            assertTrue(invalid,runCatching {parseMapCatalog(catalog(invalid),source)}.isFailure)
        }
        assertTrue(runCatching {parseMapCatalog(catalog(sha="wrong"),source)}.isFailure)
        assertTrue(runCatching {parseMapCatalog(catalog().replace("1024","536870913"),source)}.isFailure)
    }
    @Test fun githubUsesPublishedMapAssetsOnlyAndPicksLatestVersion() {
        fun release(date:String,label:String,draft:Boolean=false,pre:Boolean=false)=JSONObject().put("published_at",date)
            .put("draft",draft).put("prerelease",pre).put("assets",JSONArray().put(JSONObject()
                .put("name","region.jelandnav.zip").put("label",label).put("size",1024).put("state","uploaded")
                .put("browser_download_url","https://github.com/mrRadik/jeland-os/releases/download/maps/region.jelandnav.zip")
                .put("digest","sha256:"+"b".repeat(64)))
                .put(JSONObject().put("name","app.apk").put("size",500).put("state","uploaded")))
        val releases=JSONArray().put(release("2026-01-01","Старый")).put(release("2026-10-01","Актуальный"))
            .put(release("2026-10-02","Черновик",draft=true)).put(release("2026-10-03","Preview",pre=true))
        val maps=parseMapCatalog(releases.toString(),mapCatalogSource(DEFAULT_MAP_SOURCE))
        assertEquals(1,maps.size);assertEquals("Актуальный",maps.single().title)
        assertEquals("b".repeat(64),maps.single().sha256)
        assertEquals("https://api.github.com/repos/mrRadik/jeland-os/releases?per_page=100",mapCatalogSource("").url)
        assertTrue(mapCatalogSource("https://github.com/mrRadik/jeland-os/releases/tag/maps").url.endsWith("/tags/maps"))
    }
    @Test fun savedSourceSurvivesRecreationAndFailedRefreshKeepsList()=runBlocking {
        val name="qa-map-catalog-${java.util.UUID.randomUUID()}"
        var fail=false
        var repo=NavigationMapCatalog(context,name){if(fail)error("Нет сети")else catalog()}
        try {
            repo.setSource("https://example.org/maps")
            repo.refresh();withTimeout(5000){repo.state.first {it.loaded}}
            assertEquals(1,repo.state.value.maps.size)
            fail=true;repo.refresh();withTimeout(5000){repo.state.first {it.error.isNotBlank()}}
            assertEquals(1,repo.state.value.maps.size)
            repo.close();repo=NavigationMapCatalog(context,name){catalog()}
            assertEquals("https://example.org/maps",repo.state.value.source)
        }finally {repo.close();context.deleteSharedPreferences(name)}
    }
    @Test fun changingSourceCancelsOldRequestAndDoesNotShowItsMaps()=runBlocking {
        val name="qa-map-catalog-${java.util.UUID.randomUUID()}"
        val entered=CompletableDeferred<Unit>()
        val repo=NavigationMapCatalog(context,name){entered.complete(Unit);delay(5000);catalog()}
        try {
            repo.refresh();entered.await()
            repo.setSource("https://example.org/new")
            delay(100)
            assertFalse(repo.state.value.loading);assertTrue(repo.state.value.maps.isEmpty())
            assertEquals("https://example.org/new",repo.state.value.source)
        }finally {repo.close();context.deleteSharedPreferences(name)}
    }
}
