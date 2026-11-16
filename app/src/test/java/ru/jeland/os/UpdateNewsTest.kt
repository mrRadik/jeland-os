package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class UpdateNewsTest {
    private val release=UpdateNews(123,"1.3.0-pp17","# Изменения\n- Новая панель\n- Настройки")
    @Test fun ordinaryInstallsAndRestartsDoNotCreateNews() {
        val tracker=UpdateNewsTracker(MemorySettingsStore())
        for(version in listOf(0L,122L,123L,124L)){tracker.captureInstalled(version);assertNull(tracker.pending(version))}
    }
    @Test fun onlySuccessfulExactUpdateShowsOnceAcrossProcesses() {
        val store=MemorySettingsStore()
        UpdateNewsTracker(store).arm(122,release)
        UpdateNewsTracker(store).apply {captureInstalled(122);assertNull(pending(122))}
        UpdateNewsTracker(store).apply {captureInstalled(123);assertEquals(release,pending(123))}
        UpdateNewsTracker(store).apply {captureInstalled(123);assertEquals(release,pending(123));shown(124);assertEquals(release,pending(123));shown(123)}
        UpdateNewsTracker(store).apply {captureInstalled(123);assertNull(pending(123));arm(123,release.copy(versionCode=124,versionName="next"));captureInstalled(124);assertEquals(124L,pending(124)!!.versionCode)}
    }
    @Test fun cancelledOrSupersededInstallationCannotAnnounceWrongRelease() {
        val store=MemorySettingsStore();val tracker=UpdateNewsTracker(store)
        tracker.arm(122,release);tracker.cancel();tracker.captureInstalled(123);assertNull(tracker.pending(123))
        tracker.arm(122,release);tracker.captureInstalled(124);assertNull(tracker.pending(124));assertNull(tracker.pending(123))
        tracker.arm(122,release);tracker.captureInstalled(123);tracker.captureInstalled(124);assertNull(tracker.pending(124))
    }
    @Test fun malformedAndTruncatedReceiptsNeverProduceDialog() {
        val store=MemorySettingsStore();val tracker=UpdateNewsTracker(store)
        for(raw in listOf("", "122\n123", "122\n122\nx\nx", "-1\n123\nx\nx", "122\n123\n\nx", "a\n123\nx\nx")) {
            store.put(UpdateNewsTracker.ATTEMPT,raw);store.put(UpdateNewsTracker.READY,raw)
            tracker.captureInstalled(123);assertNull(raw,tracker.pending(123))
        }
    }
    @Test fun receiptsAreDurableBeforeInstallerMayKillProcess() {
        val values=mutableMapOf<String,String>()
        val store=object:DurableSettingsStore {
            override fun string(key:String,default:String)=values[key]?:default
            override fun put(key:String,value:String){fail("Receipt must use synchronous persistence")}
            override fun putDurable(key:String,value:String){values[key]=value}
        }
        UpdateNewsTracker(store).apply {arm(122,release);captureInstalled(123);assertEquals(release,pending(123));shown(123)}
    }
    @Test fun releaseNotesAreBoundedAndReadableWithoutMarkdownControls() {
        assertEquals("Изменения\n• Новая панель\n• Настройки",plainReleaseNotes(release.body))
        assertEquals("Ссылка и текст",plainReleaseNotes("[Ссылка](https://example.org) и **текст**"))
        val store=MemorySettingsStore();val tracker=UpdateNewsTracker(store)
        tracker.arm(122,release.copy(body="x".repeat(13000)));tracker.captureInstalled(123)
        assertEquals(12000,tracker.pending(123)!!.body.length)
    }
}
