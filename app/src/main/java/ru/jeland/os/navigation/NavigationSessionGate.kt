package ru.jeland.os.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ferrostar stop cancels its location job but destroys the native session without joining it.
 * Keep the entire downstream location callback and session start/stop mutually exclusive.
 * The SDK consumes locations on IO, not on the UI thread; no UI work may await this gate.
 */
internal class NavigationSessionGate {
    private val mutex=Mutex()
    suspend fun <T> access(block:()->T):T=mutex.withLock {block()}
    fun <T> blocking(block:()->T):T=runBlocking {access(block)}
    fun <T> updates(source:Flow<T>):Flow<T> = flow {
        source.collect {value->mutex.withLock {emit(value)}}
    }
}
