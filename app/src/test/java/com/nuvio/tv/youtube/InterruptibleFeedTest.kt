package com.nuvio.tv.youtube

import io.reactivex.Observable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class InterruptibleFeedTest {
    @Test
    fun `feed batches retain their order`() = runBlocking {
        val received = mutableListOf<Int>()
        collectInterruptibleFeed({ Observable.just(1, 2, 3) }) { received += it }
        assertEquals(listOf(1, 2, 3), received)
    }

    @Test
    fun `cancelling a pending feed disposes the subscription`() = runBlocking {
        val subscribed = CompletableDeferred<Unit>()
        val disposed = AtomicBoolean(false)
        val received = mutableListOf<Int>()
        val job = launch {
            collectInterruptibleFeed({
                Observable.never<Int>()
                    .doOnSubscribe { subscribed.complete(Unit) }
                    .doOnDispose { disposed.set(true) }
            }) { received += it }
        }
        withTimeout(5_000) {
            subscribed.await()
            job.cancelAndJoin()
        }
        assertTrue(disposed.get())
        assertTrue(received.isEmpty())
    }

    @Test
    fun `ordinary errors propagate to the callers retry policy`() = runBlocking {
        val failure = IllegalStateException("network failure")
        val actual = runCatching { collectInterruptibleFeed({ Observable.error<Int>(failure) }) {} }.exceptionOrNull()
        assertTrue(actual is IllegalStateException)
        assertEquals(failure.message, actual?.message)
    }
}
