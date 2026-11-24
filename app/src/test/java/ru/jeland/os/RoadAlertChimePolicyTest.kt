package ru.jeland.os

import org.junit.Assert.*
import org.junit.Test
import ru.jeland.os.core.RoadAlert
import ru.jeland.os.core.RoadAlertChimePolicy

class RoadAlertChimePolicyTest {
    private val alert=RoadAlert("strelka","Камера 60 км/ч","Ленина",1000,61_000)
    @Test fun waitsForDisplayAndSoundsOnceAcrossMultipleScreens() {
        val policy=RoadAlertChimePolicy()
        assertFalse(policy.shown())
        policy.update(alert)
        assertTrue(policy.shown())
        assertFalse(policy.shown()) // Cluster, map widget, recomposition or screen change.
    }
    @Test fun textAndTimestampUpdatesAndCancelRepostWithinTheGracePeriodStaySilent() {
        val policy=RoadAlertChimePolicy()
        policy.update(alert);assertTrue(policy.shown())
        policy.update(alert.copy(title="Переход",detail="100 м",postedAt=2000))
        assertFalse(policy.shown())
        policy.update(alert);assertFalse(policy.shown())
    }
    @Test fun removalOrExpiryAllowsTheNextAppearanceEvenWithTheSameTextAndKey() {
        val policy=RoadAlertChimePolicy()
        policy.update(alert);assertTrue(policy.shown())
        policy.update(null);assertFalse(policy.shown())
        policy.update(alert);assertTrue(policy.shown())
    }
    @Test fun restoredNotificationsStaySilentAndAnUnseenExpiredAlertIsNeverQueued() {
        val policy=RoadAlertChimePolicy()
        policy.update(alert,replay=true);assertFalse(policy.shown())
        policy.update(alert.copy(postedAt=2000));assertFalse(policy.shown())
        policy.update(null)
        policy.update(alert)
        policy.update(null);assertFalse(policy.shown())
        policy.update(alert);assertTrue(policy.shown())
    }
}
