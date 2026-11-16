package com.nuvio.tv.data.local

import android.content.Context
import com.nuvio.tv.core.profile.ProfileManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContinueWatchingEnrichmentCacheProfilesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val activeProfile = MutableStateFlow(1)

    private fun cache(): ContinueWatchingEnrichmentCache {
        val context = mockk<Context>()
        val profiles = mockk<ProfileManager>()
        every { context.filesDir } returns temporary.root
        every { profiles.activeProfileId } returns activeProfile
        return ContinueWatchingEnrichmentCache(context, profiles)
    }

    @Test
    fun `identical snapshots are persisted separately for each profile`() = runTest {
        val cache = cache()
        val next = listOf(nextItem())
        val progress = listOf(progressItem())
        cache.saveNextUpSnapshot(next)
        cache.saveInProgressSnapshot(progress)
        activeProfile.value = 2
        cache.saveNextUpSnapshot(next)
        cache.saveInProgressSnapshot(progress)
        assertEquals(next, cache.getNextUpSnapshot())
        assertEquals(progress, cache.getInProgressSnapshot())
        assertEquals(next, cache.getNextUpSnapshot(profileId = 1))
    }

    @Test
    fun `clearing permits an immediate rebuild of the same profile`() = runTest {
        val cache = cache()
        val items = listOf(progressItem())
        cache.saveInProgressSnapshot(items)
        cache.clearAll()
        cache.saveInProgressSnapshot(items)
        assertEquals(items, cache.getInProgressSnapshot())
    }

    @Test
    fun `explicit pipeline profile cannot write into the newly active profile`() = runTest {
        val cache = cache()
        activeProfile.value = 2
        cache.saveInProgressSnapshot(listOf(progressItem()), profileId = 1)
        assertTrue(cache.getInProgressSnapshot(profileId = 2).isEmpty())
        assertEquals(listOf(progressItem()), cache.getInProgressSnapshot(profileId = 1))
    }

    @Test
    fun `clearing a derived snapshot preserves other profiles and primary files`() = runTest {
        val cache = cache()
        val primary = java.io.File(temporary.root, "library.preferences_pb").apply { writeText("primary data") }
        cache.saveNextUpSnapshot(listOf(nextItem()), profileId = 1)
        cache.saveNextUpSnapshot(listOf(nextItem()), profileId = 2)
        cache.clearAll(profileId = 1)
        assertTrue(cache.getNextUpSnapshot(profileId = 1).isEmpty())
        assertEquals(listOf(nextItem()), cache.getNextUpSnapshot(profileId = 2))
        assertEquals("primary data", primary.readText())
    }

    private fun progressItem() = CachedInProgressItem(
        contentId = "tt0001", contentType = "movie", name = "Movie", poster = null,
        backdrop = null, logo = null, videoId = "tt0001", season = null, episode = null,
        episodeTitle = null, position = 1_000L, duration = 10_000L,
        lastWatched = 10L, progressPercent = 10f
    )

    private fun nextItem() = CachedNextUpItem(
        contentId = "tt0002", contentType = "series", name = "Series", poster = null,
        backdrop = null, logo = null, videoId = "tt0002:1:2", season = 1, episode = 2,
        episodeTitle = null, thumbnail = null, lastWatched = 10L, sortTimestamp = 10L
    )
}
