package com.nuvio.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShadowCopyDataStoreTest {
    private val key = intPreferencesKey("progress")

    @Test
    fun `many collectors of existing preferences schedule only one initial backup`() = runTest {
        val source = FakeStore(preferencesOf(key to 10))
        var copies = 0
        val store = ShadowCopyDataStore(source, backgroundScope) { copies++ }
        repeat(100) { assertEquals(10, store.data.first()[key]) }
        runCurrent()
        assertEquals(1, copies)
    }

    @Test
    fun `reading empty defaults makes no backup jobs and first edit is still protected`() = runTest {
        val source = FakeStore(emptyPreferences())
        var copies = 0
        val store = ShadowCopyDataStore(source, backgroundScope) { copies++ }
        repeat(100) { store.data.first() }
        runCurrent()
        assertEquals(0, copies)
        store.updateData { preferencesOf(key to 20) }
        runCurrent()
        assertEquals(1, copies)
        assertEquals(20, store.data.first()[key])
    }

    @Test
    fun `edits during a slow backup are coalesced and the latest durable value is copied`() = runTest {
        val source = FakeStore(preferencesOf(key to 1))
        val gate = CompletableDeferred<Unit>()
        val copied = mutableListOf<Int?>()
        var count = 0
        val store = ShadowCopyDataStore(source, backgroundScope) {
            if (++count == 1) gate.await()
            copied += source.data.first()[key]
        }
        store.data.first()
        runCurrent()
        repeat(100) { n -> store.updateData { preferencesOf(key to n + 2) } }
        runCurrent()
        assertEquals(1, count)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, count)
        assertEquals(listOf(101, 101), copied)
    }

    @Test
    fun `backup failure does not lose primary preferences or prevent a subsequent retry`() = runTest {
        val source = FakeStore(preferencesOf(key to 1))
        var copies = 0
        val store = ShadowCopyDataStore(source, backgroundScope) {
            if (++copies == 1) throw java.io.IOException("disk unavailable")
        }
        store.data.first()
        runCurrent()
        store.updateData { preferencesOf(key to 2) }
        runCurrent()
        assertEquals(2, copies)
        assertEquals(2, source.data.first()[key])
    }

    @Test
    fun `clearing primary preferences also schedules a known empty backup`() = runTest {
        val source = FakeStore(preferencesOf(key to 1))
        val copies = mutableListOf<Preferences>()
        val store = ShadowCopyDataStore(source, backgroundScope) { copies += source.data.first() }
        store.data.first()
        runCurrent()
        store.updateData { emptyPreferences() }
        runCurrent()
        assertEquals(2, copies.size)
        assertEquals(emptyPreferences(), copies.last())
    }

    private class FakeStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(data.value).also { data.value = it }
    }
}
