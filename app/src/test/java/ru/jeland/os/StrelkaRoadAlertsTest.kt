package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.*

class StrelkaRoadAlertsTest {
    // Notification envelope in official Strelka 25.01.1s: Alert/10017, channel 1038.
    private fun camera()=StrelkaNotice(StrelkaNoticePolicy.PACKAGE,"1038",10017,"Alert","real-alert-key","Камера 60 км/ч","ул. Ленина",1_000_000)
    @Test fun onlyTheVerifiedCameraEnvelopeIsAccepted() {
        val n=camera();assertNotNull(StrelkaNoticePolicy.alert(n,1000,1_000_000))
        for(other in listOf(n.copy(packageName="ru.chat"),n.copy(packageName="com.ivolk.estrelka"),n.copy(channel="1037"),n.copy(id=1039),n.copy(tag="Main"),n.copy(title=" "),n.copy(foreground=true),n.copy(summary=true)))
            assertNull(StrelkaNoticePolicy.alert(other,1000,1_000_000))
    }
    @Test fun serviceAndDatabaseUpdateNotificationsAreNotCameraWarnings() {
        val status=camera().copy(id=1095,channel="1037",foreground=true)
        assertTrue(StrelkaNoticePolicy.running(status));assertNull(StrelkaNoticePolicy.alert(status,0,1_000_000))
        assertFalse(StrelkaNoticePolicy.running(status.copy(packageName="ru.chat")))
        assertFalse(StrelkaNoticePolicy.running(status.copy(id=1037)))
        assertFalse(StrelkaNoticePolicy.running(status.copy(channel="1038")))
    }
    @Test fun expiryUsesElapsedTimeAndDuplicateCallbacksDoNotRenewOldWarnings() {
        val inbox=RoadAlertInbox();val n=camera()
        inbox.accept(n,1000,1_000_000);val first=inbox.current!!
        inbox.accept(n,31_000,1_030_000);assertEquals(first,inbox.current)
        inbox.expire(61_000);assertNull(inbox.current)
    }
    @Test fun replayAfterSleepOrProcessRestartPreservesRemainingLifetime() {
        val n=camera()
        val fresh=StrelkaNoticePolicy.alert(n,5000,1_040_000,true)!!
        assertEquals(25_000L,fresh.expiresAt)
        assertNull(StrelkaNoticePolicy.alert(n,5000,1_060_000,true))
        assertNull(StrelkaNoticePolicy.alert(n,5000,900_000,true))
        assertNotNull(StrelkaNoticePolicy.alert(n,5000,900_000,false)) // A newly delivered event after an OEM clock rollback.
    }
    @Test fun anOlderRemovalCannotClearAnotherCameraAndTextIsNotInterpretedAsCoordinates() {
        val inbox=RoadAlertInbox();val first=camera();val second=first.copy(key="next",postedAt=1_001_000,title="Пешеходный переход")
        inbox.accept(first,0,1_000_000);inbox.accept(second,1000,1_001_000)
        inbox.remove(first.key);assertEquals(second.title,inbox.current!!.title)
        inbox.remove(second.key);assertNull(inbox.current)
    }
}
