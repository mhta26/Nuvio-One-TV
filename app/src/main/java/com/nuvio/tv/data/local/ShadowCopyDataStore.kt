package com.nuvio.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** One serialized, conflated backup worker; preference reads never create extra IO jobs. */
internal class ShadowCopyDataStore(
    private val delegate: DataStore<Preferences>,
    scope: CoroutineScope,
    private val writeShadowCopy: suspend () -> Unit
) : DataStore<Preferences> {
    private val firstReadObserved = AtomicBoolean(false)
    private val copyRequests = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch(start = CoroutineStart.LAZY) {
        for (ignored in copyRequests) {
            try {
                writeShadowCopy()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed derived backup must not fail the primary store. A later edit retries.
            }
        }
    }

    override val data = delegate.data.onEach { preferences ->
        if (firstReadObserved.compareAndSet(false, true) && preferences.asMap().isNotEmpty()) {
            requestCopy()
        }
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val result = delegate.updateData(transform)
        firstReadObserved.set(true)
        requestCopy()
        return result
    }

    private fun requestCopy() {
        copyRequests.trySend(Unit)
        worker.start()
    }
}
