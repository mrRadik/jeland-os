package ru.jeland.os.navigation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class NavigationSessionGateTest {
    @Test fun destructionWaitsForTheWholeLocationCallback()=runBlocking {
        val gate=NavigationSessionGate()
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        var destroyed=false
        val location=launch {gate.updates(flowOf(1)).collect {
            entered.complete(Unit);release.await()
            assertFalse("Session must remain alive throughout the SDK callback",destroyed)
        }}
        entered.await()
        val stop=async {gate.access {destroyed=true}}
        yield()
        assertFalse(stop.isCompleted)
        release.complete(Unit)
        location.join();stop.await()
        assertTrue(destroyed)
    }
    @Test fun canceledLocationCollectorReleasesGate()=runBlocking {
        val gate=NavigationSessionGate()
        val entered=CompletableDeferred<Unit>()
        val location=launch {gate.updates(flowOf(1)).collect {entered.complete(Unit);awaitCancellation()}}
        entered.await();location.cancelAndJoin()
        withTimeout(1000){gate.access {assertTrue(true)}}
    }
}
