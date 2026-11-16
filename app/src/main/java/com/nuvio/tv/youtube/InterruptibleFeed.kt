package com.nuvio.tv.youtube

import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible

/** Cancelling navigation interrupts Rx's blocking queue and disposes its subscription. */
internal suspend fun <T : Any> collectInterruptibleFeed(source: () -> Observable<T>, onNext: (T) -> Unit) {
    val context = currentCoroutineContext()
    try {
        runInterruptible(Dispatchers.IO) {
            source().blockingForEach { value ->
                context.ensureActive()
                onNext(value)
            }
        }
    } catch (error: Exception) {
        // Rx wraps InterruptedException; preserve coroutine cancellation rather than retrying it.
        context.ensureActive()
        throw error
    }
}
