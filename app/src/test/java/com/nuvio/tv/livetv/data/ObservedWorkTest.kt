@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.nuvio.tv.livetv.data

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservedWorkTest {
    @Test fun `hidden screen does no periodic work and resumes immediately on return`() = runTest {
        val subscribers = MutableStateFlow(0)
        var queries = 0
        backgroundScope.launchWhileObserved(subscribers) {
            while (true) { queries++; delay(30_000) }
        }
        runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(0, queries)
        subscribers.value = 1; runCurrent()
        assertEquals(1, queries)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, queries)
        subscribers.value = 0; runCurrent()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(2, queries)
        subscribers.value = 1; runCurrent()
        assertEquals(3, queries)
    }

    @Test fun `additional consumers do not duplicate or restart work`() = runTest {
        val subscribers = MutableStateFlow(1)
        var starts = 0
        var stops = 0
        backgroundScope.launchWhileObserved(subscribers) {
            starts++
            try { awaitCancellation() } finally { stops++ }
        }
        runCurrent()
        subscribers.value = 2; runCurrent()
        subscribers.value = 1; runCurrent()
        assertEquals(1, starts)
        assertEquals(0, stops)
        subscribers.value = 0; runCurrent()
        assertEquals(1, stops)
    }

    @Test fun `leaving cancels in flight prefetch and its child jobs`() = runTest {
        val subscribers = MutableStateFlow(1)
        var fetched = 0
        var cancelled = 0
        backgroundScope.launchWhileObserved(subscribers) {
            launch {
                try { delay(1_500); fetched++ } finally { cancelled++ }
            }
        }
        runCurrent()
        advanceTimeBy(500)
        subscribers.value = 0; runCurrent()
        advanceTimeBy(2_000); runCurrent()
        assertEquals(0, fetched)
        assertEquals(1, cancelled)
        subscribers.value = 1; runCurrent()
        advanceTimeBy(1_500); runCurrent()
        assertEquals(1, fetched)
    }
}
