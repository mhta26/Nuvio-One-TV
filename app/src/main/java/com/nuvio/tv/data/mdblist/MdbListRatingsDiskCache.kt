package com.nuvio.tv.data.mdblist

import com.nuvio.tv.domain.model.MDBListRatings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@OptIn(FlowPreview::class)
internal class MdbListRatingsDiskCache(
    private val cacheDirectory: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private data class Entry(val ratings: MDBListRatings, val storedAtMs: Long)

    private val mutex = Mutex()
    private val entries = ConcurrentHashMap<String, Entry>()
    @Volatile private var loaded = false
    private val revisions = MutableStateFlow(0L)
    private var persistedRevision = 0L

    init {
        // One debounced writer; bursts of rating updates do not create/cancel IO jobs.
        scope.launch(ioDispatcher) {
            revisions.filter { it > 0L }.debounce(SAVE_DEBOUNCE_MS).collect { persist() }
        }
    }

    private fun cacheFile(): File {
        cacheDirectory.mkdirs()
        return File(cacheDirectory, "ratings_v1.json")
    }

    suspend fun load() = withContext(ioDispatcher) {
        if (loaded) return@withContext
        mutex.withLock {
            if (loaded) return@withLock
            try {
                val file = cacheFile()
                if (file.exists()) {
                    val json = JSONObject(file.readText())
                    val cutoff = now() - TTL_MS
                    for (key in json.keys()) {
                        val obj = json.optJSONObject(key) ?: continue
                        val storedAt = obj.optLong("t", 0L)
                        if (storedAt < cutoff) continue
                        // A fresh network result arriving during load wins over disk data.
                        entries.putIfAbsent(key, Entry(parseRatings(obj), storedAt))
                    }
                }
            } catch (_: Exception) { }
            loaded = true
        }
    }

    suspend fun get(key: String): MDBListRatings? {
        if (!loaded) load()
        val entry = entries[key] ?: return null
        if (now() - entry.storedAtMs > TTL_MS) {
            // Do not remove a concurrent replacement with a newer rating.
            entries.remove(key, entry)
            return null
        }
        return entry.ratings
    }

    fun put(key: String, ratings: MDBListRatings) {
        entries[key] = Entry(ratings, now())
        revisions.update { it + 1L }
    }

    private suspend fun persist() {
        load()
        withContext(ioDispatcher) {
            mutex.withLock {
                val revision = revisions.value
                if (revision == persistedRevision) return@withLock
                try {
                    val json = JSONObject()
                    val cutoff = now() - TTL_MS
                    entries.forEach { (key, entry) ->
                        if (entry.storedAtMs < cutoff) entries.remove(key, entry)
                    }
                    for ((key, entry) in entries.toMap()) {
                        json.put(key, serializeEntry(entry))
                    }
                    val file = cacheFile()
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(json.toString())
                    if (!tmp.renameTo(file)) {
                        tmp.copyTo(file, overwrite = true)
                        tmp.delete()
                    }
                    // Newer puts remain dirty and the flow schedules a later write.
                    persistedRevision = revision
                } catch (_: Exception) { }
            }
        }
    }

    private fun serializeEntry(entry: Entry): JSONObject = JSONObject().apply {
        put("t", entry.storedAtMs)
        entry.ratings.trakt?.let { put("trakt", it) }
        entry.ratings.imdb?.let { put("imdb", it) }
        entry.ratings.tmdb?.let { put("tmdb", it) }
        entry.ratings.letterboxd?.let { put("letterboxd", it) }
        entry.ratings.tomatoes?.let { put("tomatoes", it) }
        entry.ratings.audience?.let { put("audience", it) }
        entry.ratings.metacritic?.let { put("metacritic", it) }
        entry.ratings.mal?.let { put("mal", it) }
        if (entry.ratings.tomatoesCertified) put("tc", true)
        if (entry.ratings.audienceCertified) put("ac", true)
    }

    private fun parseRatings(obj: JSONObject): MDBListRatings = MDBListRatings(
        trakt = obj.optDoubleOrNull("trakt"),
        imdb = obj.optDoubleOrNull("imdb"),
        tmdb = obj.optDoubleOrNull("tmdb"),
        letterboxd = obj.optDoubleOrNull("letterboxd"),
        tomatoes = obj.optDoubleOrNull("tomatoes"),
        audience = obj.optDoubleOrNull("audience"),
        metacritic = obj.optDoubleOrNull("metacritic"),
        mal = obj.optDoubleOrNull("mal"),
        tomatoesCertified = obj.optBoolean("tc", false),
        audienceCertified = obj.optBoolean("ac", false)
    )

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key).takeIf { !it.isNaN() } else null

    companion object {
        private const val TTL_MS = 12L * 60L * 60L * 1000L
        private const val SAVE_DEBOUNCE_MS = 2_000L
    }
}
