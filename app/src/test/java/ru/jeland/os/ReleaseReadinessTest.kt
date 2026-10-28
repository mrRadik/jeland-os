package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*
import ru.jeland.os.localization.TranslationCatalog
import java.io.File
import java.nio.file.Files

class ReleaseReadinessTest {
    @Test fun fuelWarningOnlyBelowTenPercentAndOnlyWithValidReading() {
        assertTrue(FuelState(5.49).low)
        assertFalse(FuelState(5.5).low)
        assertFalse(FuelState(null).low)
        assertFalse(FuelState(Double.NaN).low)
        assertFalse(FuelState(55.0).low)
    }
    @Test fun installedCleanupKeepsNewerDownloadsAndOtherFiles() {
        val folder=Files.createTempDirectory("installed-update").toFile()
        try {
            val ready=File(folder,"updates/ready").apply {mkdirs()}
            File(ready,"JelandOS-95.apk").writeText("old")
            File(ready,"JelandOS-100.apk").writeText("installed")
            File(ready,"JelandOS-101.apk").writeText("next")
            File(ready,"other.apk").writeText("unrelated")
            val partial=File(folder,"updates/staging/update.part").apply {parentFile.mkdirs();writeText("next-partial")}
            val result=OwnUpdateApkCache.clearInstalled(folder,100)
            assertEquals(2,result.deleted)
            assertTrue(File(ready,"JelandOS-101.apk").exists())
            assertTrue(File(ready,"other.apk").exists())
            assertTrue(partial.exists())
            assertEquals(0,OwnUpdateApkCache.clearInstalled(folder,100).deleted)
        }finally {folder.deleteRecursively()}
    }
    @Test fun englishCatalogHasUniquePhrasesAndMatchingTemplateSlots() {
        val entries=File("src/main/assets/i18n/en.tsv").readLines().filter {it.contains('\t')}.map {it.split('\t',limit=2)}
        assertEquals("Duplicate translation key",entries.size,entries.map {it[0]}.distinct().size)
        val slots=Regex("\\{[0-9]+}")
        entries.forEach {(source,translation)->assertEquals(source,slots.findAll(source).map {it.value}.toSet(),slots.findAll(translation).map {it.value}.toSet())}
        val catalog=TranslationCatalog(entries.associate {it[0].replace("\\n","\n") to it[1].replace("\\n","\n")})
        assertEquals("Home",catalog.translate("Главная"))
        assertEquals("Installed 1.0.0",catalog.translate("Установлена 1.0.0"))
        assertEquals("30 L",catalog.translate("30 л"))
        assertEquals("5.9 km",catalog.translate("5.9 км"))
        assertEquals("Улица Худяково",catalog.translate("Улица Худяково"))
        assertEquals("Мёртвые звёзды",catalog.translate("Мёртвые звёзды"))
    }
}
