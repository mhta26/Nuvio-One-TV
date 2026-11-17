package com.nuvio.tv.core.image

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.nuvio.tv.core.diagnostics.PerformanceDiagnostics
import com.nuvio.tv.core.diagnostics.PerformanceOperation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PosterRetryResult(val attempted: Int, val recovered: Int)

/** Explicit, sequential retries. No timers or workers when the user is browsing/playing. */
@Singleton
class PosterRetryService @Inject constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    suspend fun retryFailed(): PosterRetryResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loader = SingletonImageLoader.get(context)
            var recovered = 0
            val batch = FailedPosterRegistry.shared.batch()
            for ((url, generation) in batch) {
                currentCoroutineContext().ensureActive()
                val started = System.nanoTime()
                val result = loader.execute(ImageRequest.Builder(context).data(url).size(500, 750)
                    .memoryCachePolicy(CachePolicy.WRITE_ONLY).build())
                val success = result is SuccessResult
                PerformanceDiagnostics.operation(PerformanceOperation.POSTER_RETRY, started, success)
                if (success) {
                    FailedPosterRegistry.shared.resolved(url, generation)
                    // Preserve the freshly downloaded disk entry; visible cards decode from it.
                    loader.memoryCache?.let { cache -> cache.keys.filter { it.key.contains(url) }.forEach(cache::remove) }
                    ImageInvalidationBus.notifyInvalidated(url)
                    recovered++
                }
            }
            PosterRetryResult(batch.size, recovered)
        }
    }
}
