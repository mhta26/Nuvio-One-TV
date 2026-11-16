package com.nuvio.tv.livetv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** UI prefetch belongs to visible consumers, not the lifetime of a retained ViewModel.
 * Cancelling the last consumer also cancels in-flight work; multiple consumers share one worker.
 * Recording, playback and scheduled guide refreshes must remain outside this worker.
 */
internal fun CoroutineScope.launchWhileObserved(
    subscriptionCount: StateFlow<Int>,
    work: suspend CoroutineScope.() -> Unit
): Job = launch {
    subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { observed ->
        if (observed) coroutineScope { work() }
    }
}
