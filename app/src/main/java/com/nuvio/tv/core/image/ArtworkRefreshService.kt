package com.nuvio.tv.core.image

import android.content.Context
import coil3.SingletonImageLoader
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Discard derived artwork only. User library, watched history and playback positions stay intact. */
@Singleton
class ArtworkRefreshService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metaRepository: MetaRepository,
    private val tmdb: TmdbService,
    private val enrichment: ContinueWatchingEnrichmentCache
) {
    private val mutex = Mutex()

    suspend fun clearArtworkCaches() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            metaRepository.clearCache()
            tmdb.clearCache()
            enrichment.clearAll()
        }
    }
}
