package com.nuvio.tv.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.core.profile.ProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CachedNextUpItem(
    val contentId: String,
    val contentType: String,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val videoId: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String?,
    val episodeDescription: String? = null,
    val thumbnail: String?,
    val released: String? = null,
    val hasAired: Boolean = true,
    val airDateLabel: String? = null,
    val lastWatched: Long,
    val imdbRating: Float? = null,
    val genres: List<String> = emptyList(),
    val releaseInfo: String? = null,
    val sortTimestamp: Long,
    val releaseTimestamp: Long? = null,
    val isReleaseAlert: Boolean = false,
    val isNewSeasonRelease: Boolean = false,
    val seedSeason: Int? = null,
    val seedEpisode: Int? = null,
    val contentLanguage: String? = null,
    val ratingVisible: Boolean = true
)

data class CachedInProgressItem(
    val contentId: String,
    val contentType: String,
    val name: String,
    val poster: String?,
    val backdrop: String?,
    val logo: String?,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val position: Long,
    val duration: Long,
    val lastWatched: Long,
    val progressPercent: Float?,
    val episodeThumbnail: String? = null,
    val episodeDescription: String? = null,
    val episodeImdbRating: Float? = null,
    val genres: List<String> = emptyList(),
    val releaseInfo: String? = null,
    val contentLanguage: String? = null,
    val ratingVisible: Boolean = true
)

/**
 * Live TV fork: whether a cached item read back with every required value. Gson fills a missing
 * value with null even where Kotlin says it can't be, which crashed the app at start-up after an
 * update (the update kept the real field names; the old cache had the shortened ones).
 */
internal fun allPresent(vararg values: Any?): Boolean = values.all { it != null }

internal fun CachedNextUpItem.intact(): Boolean =
    allPresent(contentId, contentType, name, videoId, genres)

internal fun CachedInProgressItem.intact(): Boolean =
    allPresent(contentId, contentType, name, videoId, genres)

@Singleton
class ContinueWatchingEnrichmentCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val TAG = "CwEnrichCache"
        private const val THROTTLE_MS = 1_000L
    }

    private val gson = Gson()
    private val mutex = Mutex()
    private data class WriteState(val hash: Int, val writtenAtMs: Long)
    // Protected by mutex; identical items in two profiles still need two cache files.
    private val nextUpWrites = mutableMapOf<Int, WriteState>()
    private val inProgressWrites = mutableMapOf<Int, WriteState>()

    /** Incremented when cache is cleared; observers can collect to trigger refresh. */
    private val _cacheCleared = kotlinx.coroutines.flow.MutableStateFlow(0)
    val cacheCleared: kotlinx.coroutines.flow.StateFlow<Int> = _cacheCleared

    /** Incremented on every successful snapshot write; channel sync observes this. */
    private val _snapshotVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    val snapshotVersion: kotlinx.coroutines.flow.StateFlow<Int> = _snapshotVersion

    // --- Next Up snapshot cache ---

    private fun nextUpFile(profileId: Int): File {
        val dir = File(context.filesDir, "cw_enrichment")
        dir.mkdirs()
        return File(dir, "nextup_${profileId}.json")
    }

    suspend fun getNextUpSnapshot(profileId: Int = profileManager.activeProfileId.value): List<CachedNextUpItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = nextUpFile(profileId)
                if (!file.exists()) return@withContext emptyList()
                val items: List<CachedNextUpItem> =
                    gson.fromJson(file.readText(), object : TypeToken<List<CachedNextUpItem>>() {}.type)
                        ?: emptyList()
                // Live TV fork: a cache written by another build of the app (its field names
                // differ) reads back with missing values; drop it, it's rebuilt straight away.
                if (items.all { it.intact() }) items else { file.delete(); emptyList() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read next-up cache: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * @param force bypass throttle and content-change check (use at end of pipeline or for clears)
     */
    suspend fun saveNextUpSnapshot(allItems: List<CachedNextUpItem>, force: Boolean = false, profileId: Int = profileManager.activeProfileId.value) = withContext(Dispatchers.IO) {
        // Live TV fork: the Calendar's cards aren't kept (the Calendar puts them in again).
        val items = allItems.filterNot { com.nuvio.tv.livetv.calendar.CalendarHomeFeed.isInjected(it.contentId, it.videoId) }
        val contentHash = items.hashCode()
        mutex.withLock {
            val previous = nextUpWrites[profileId]
            if (!force && previous != null &&
                (contentHash == previous.hash || System.currentTimeMillis() - previous.writtenAtMs < THROTTLE_MS)) {
                return@withLock
            }
            try {
                val file = nextUpFile(profileId)
                atomicWrite(file, gson.toJson(items))
                nextUpWrites[profileId] = WriteState(contentHash, System.currentTimeMillis())
                _snapshotVersion.value++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write next-up cache: ${e.message}")
            }
        }
    }

    // --- In-progress snapshot cache ---

    private fun inProgressFile(profileId: Int): File {
        val dir = File(context.filesDir, "cw_enrichment")
        dir.mkdirs()
        return File(dir, "inprogress_${profileId}.json")
    }

    suspend fun getInProgressSnapshot(profileId: Int = profileManager.activeProfileId.value): List<CachedInProgressItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val file = inProgressFile(profileId)
                if (!file.exists()) return@withContext emptyList()
                val items: List<CachedInProgressItem> =
                    gson.fromJson(file.readText(), object : TypeToken<List<CachedInProgressItem>>() {}.type)
                        ?: emptyList()
                // Live TV fork: see getNextUpSnapshot.
                if (items.all { it.intact() }) items else { file.delete(); emptyList() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read in-progress cache: ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * @param force bypass throttle and content-change check (use at end of pipeline or for clears)
     */
    suspend fun saveInProgressSnapshot(allItems: List<CachedInProgressItem>, force: Boolean = false, profileId: Int = profileManager.activeProfileId.value) = withContext(Dispatchers.IO) {
        // Live TV fork: the Calendar's movie cards aren't kept (the Calendar puts them in again).
        val items = allItems.filterNot { com.nuvio.tv.livetv.calendar.CalendarHomeFeed.isInjectedMovie(it.contentId) }
        val contentHash = items.hashCode()
        mutex.withLock {
            val previous = inProgressWrites[profileId]
            if (!force && previous != null &&
                (contentHash == previous.hash || System.currentTimeMillis() - previous.writtenAtMs < THROTTLE_MS)) {
                return@withLock
            }
            try {
                val file = inProgressFile(profileId)
                atomicWrite(file, gson.toJson(items))
                inProgressWrites[profileId] = WriteState(contentHash, System.currentTimeMillis())
                _snapshotVersion.value++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write in-progress cache: ${e.message}")
            }
        }
    }

    /**
     * Deletes all CW enrichment cache files for the active profile.
     */
    suspend fun clearAll(profileId: Int = profileManager.activeProfileId.value) = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                nextUpFile(profileId).delete()
                inProgressFile(profileId).delete()
                nextUpWrites.remove(profileId)
                inProgressWrites.remove(profileId)
                Log.d(TAG, "Cleared CW enrichment cache for profile ${profileId}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear CW enrichment cache: ${e.message}")
            }
        }
        _cacheCleared.value++
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            // renameTo can fail on some filesystems; fall back to copy+delete
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

}
