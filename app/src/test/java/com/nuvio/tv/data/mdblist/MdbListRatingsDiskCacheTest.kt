package com.nuvio.tv.data.mdblist

import com.nuvio.tv.domain.model.MDBListRatings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class MdbListRatingsDiskCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `put before initial load preserves cached ratings and uses the new rating for the same key`() = runTest {
        val directory = temporary.newFolder()
        val file = java.io.File(directory, "ratings_v1.json")
        file.writeText("""{"old":{"t":1000,"imdb":7.0},"same":{"t":1000,"imdb":2.0}}""")
        val cache = MdbListRatingsDiskCache(directory, backgroundScope, { 1000L }, StandardTestDispatcher(testScheduler))
        cache.put("same", MDBListRatings(imdb = 9.0))
        cache.put("new", MDBListRatings(imdb = 8.0))
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        val saved = JSONObject(file.readText())
        assertEquals(3, saved.length())
        assertEquals(7.0, saved.getJSONObject("old").getDouble("imdb"), 0.0)
        assertEquals(9.0, saved.getJSONObject("same").getDouble("imdb"), 0.0)
        assertEquals(8.0, saved.getJSONObject("new").getDouble("imdb"), 0.0)
    }

    @Test
    fun `a burst of updates is debounced and the latest value survives recreation`() = runTest {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = MdbListRatingsDiskCache(directory, backgroundScope, { 1000L }, dispatcher)
        repeat(100) { cache.put("movie", MDBListRatings(imdb = it.toDouble())) }
        runCurrent()
        assertFalse(java.io.File(directory, "ratings_v1.json").exists())
        advanceTimeBy(2_000)
        runCurrent()
        val restored = MdbListRatingsDiskCache(directory, backgroundScope, { 1000L }, dispatcher)
        assertEquals(99.0, restored.get("movie")!!.imdb!!, 0.0)
    }

    @Test
    fun `parallel network results do not lose map entries`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = MdbListRatingsDiskCache(temporary.newFolder(), backgroundScope, { 1000L }, dispatcher)
        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(8) { worker ->
                    launch {
                        repeat(100) { index -> cache.put("$worker:$index", MDBListRatings(imdb = 8.0)) }
                    }
                }
            }
        }
        repeat(8) { worker ->
            repeat(100) { index -> assertEquals(8.0, cache.get("$worker:$index")!!.imdb!!, 0.0) }
        }
    }

    @Test
    fun `expiring an old result cannot delete a concurrent fresh replacement`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var time = 0L
        var replaceDuringExpiryCheck = false
        lateinit var cache: MdbListRatingsDiskCache
        cache = MdbListRatingsDiskCache(temporary.newFolder(), backgroundScope, {
            if (replaceDuringExpiryCheck) {
                replaceDuringExpiryCheck = false
                cache.put("movie", MDBListRatings(imdb = 9.0))
            }
            time
        }, dispatcher)
        cache.put("movie", MDBListRatings(imdb = 1.0))
        cache.load()
        time = 13L * 60 * 60 * 1000
        replaceDuringExpiryCheck = true
        assertNull(cache.get("movie"))
        assertEquals(9.0, cache.get("movie")!!.imdb!!, 0.0)
    }
}
