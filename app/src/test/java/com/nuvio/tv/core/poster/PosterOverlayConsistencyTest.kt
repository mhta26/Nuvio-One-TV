package com.nuvio.tv.core.poster

import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.screens.home.NextUpInfo
import org.junit.Assert.*
import org.junit.Test

class PosterOverlayConsistencyTest {
    private val pattern = "https://example.test/poster?tmdb={tmdb_id?}&imdb={imdb_id?}&id={id}&shape={shape}&sm=notch"
    private val raw = "https://example.test/raw.jpg"
    private val hints = CustomPosterUrlResolver.extractIds("kitsu:123", "tt26743760", 220542)

    @Test fun `continue watching preserves progress and fills cached tracking aliases`() {
        val progress = WatchProgress("kitsu:123:3:8", "series", "Show", raw, null, null,
            "kitsu:123:3:8", 3, 8, "Episode", 120_000, 1_200_000, 123L)
        val item = ContinueWatchingItem.InProgress(progress)
        val result = item.withCustomPosterUrl(pattern, hints) as ContinueWatchingItem.InProgress
        assertTrue(result.progress.poster!!.contains("tmdb=220542&imdb=tt26743760&id=kitsu:123&shape=poster"))
        assertEquals(progress.copy(poster = result.progress.poster), result.progress)
        assertEquals(raw, result.originalPoster)
        assertTrue(result.customLandscapePoster!!.contains("shape=landscape"))
    }

    @Test fun `upcoming posters normalize episode ID without changing release or episode data`() {
        val info = NextUpInfo(contentId = "tmdb:220542:3:8", contentType = "series", name = "Show",
            poster = raw, backdrop = null, logo = null, videoId = "tmdb:220542:3:8", season = 3, episode = 8,
            episodeTitle = "Episode", thumbnail = null, hasAired = false, released = "2026-12-01",
            lastWatched = 123L, sortTimestamp = 456L)
        val result = ContinueWatchingItem.NextUp(info).withCustomPosterUrl(pattern) as ContinueWatchingItem.NextUp
        assertTrue(result.info.poster!!.contains("tmdb=220542&imdb=&id=tmdb:220542&shape=poster"))
        assertEquals(info.copy(poster = result.info.poster), result.info)
        assertEquals(raw, result.originalPoster)
    }

    @Test fun `library uses stored TMDB aliases even when its canonical anime ID differs`() {
        val entry = LibraryEntry(id = "kitsu:123", type = "series", name = "Show", poster = raw,
            background = null, logo = null, description = null, releaseInfo = "2023", imdbRating = null,
            genres = emptyList(), addonBaseUrl = null, tmdbId = 220542)
        val result = entry.withCustomPosterUrl(pattern)
        assertTrue(result.poster!!.contains("tmdb=220542&imdb=&id=kitsu:123&shape=poster"))
        assertEquals(entry.id, result.id)
        assertEquals(entry.listKeys, result.listKeys)
        assertEquals(raw, result.rawPosterUrl)
    }

    @Test fun `library can reuse an existing metadata IMDb alias without modifying stored entry`() {
        val entry = LibraryEntry(id = "kitsu:123", type = "series", name = "Show", poster = raw,
            background = null, logo = null, description = null, releaseInfo = null, imdbRating = null,
            genres = emptyList(), addonBaseUrl = null)
        val result = listOf(entry).withCustomPosterUrls(pattern, mapOf(entry.id to hints)).single()
        assertTrue(result.poster!!.contains("tmdb=220542&imdb=tt26743760&id=kitsu:123"))
        assertNull(entry.imdbId)
        assertEquals(entry.id, result.id)
    }
}
