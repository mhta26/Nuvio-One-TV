package com.nuvio.tv.data.simkl

import com.nuvio.tv.R
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tracking.TrackingMutationResult
import com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.LibrarySyncState
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.WatchProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SimklLocalReimportServiceTest {
    private val authScope = SimklAuthScope(1, 1L)
    private val profiles = mockk<ProfileManager> {
        every { activeProfileId } returns MutableStateFlow(1)
    }
    private val auth = mockk<SimklAuthStorage> {
        every { authorization() } returns SimklAuthorization(authScope, "test-token")
        every { isCurrent(authScope) } returns true
    }
    private val repository = mockk<SimklSyncRepository>(relaxed = true) {
        every { state } returns MutableStateFlow(SimklSyncState())
        every { currentEntryIndex() } returns null
    }
    private val mutations = mockk<SimklMutationService>(relaxed = true)
    private val library = mockk<LibraryPreferences>(relaxed = true)
    private val progress = mockk<WatchProgressPreferences>(relaxed = true)
    private val watched = mockk<WatchedItemsPreferences>(relaxed = true)
    private val settings = mockk<TraktSettingsDataStore>(relaxed = true)
    private val enrichment = mockk<ContinueWatchingEnrichmentCache>(relaxed = true)
    private val watchedState = mockk<WatchedSeriesStateHolder>(relaxed = true)
    private val item = SavedLibraryItem("tt1", "movie", "One", null, PosterShape.POSTER,
        null, null, null, null, emptyList(), null)
    private val localLibrary = LibrarySyncState(items = listOf(item))
    private val service = SimklLocalReimportService(profiles, auth, repository, mutations, library,
        progress, watched, settings, enrichment, watchedState)

    private fun prepare() {
        coEvery { library.getSyncState(1) } returns localLibrary
        coEvery { progress.getAllRawEntries(1) } returns emptyMap()
        coEvery { watched.getAllItems(1) } returns emptyList()
        coEvery { mutations.moveToList(any(), any(), authScope, false) } returns TrackingMutationResult(1)
    }

    @Test
    fun `missing server item prevents all local deletion`() = runTest {
        prepare()
        coEvery { repository.reloadFromServer(any()) } coAnswers {
            firstArg<suspend (SimklSnapshotProjection) -> Unit>()(SimklSnapshotProjection.Empty)
        }
        try {
            service.uploadAndReimport()
            fail("An unverified item must block deletion")
        } catch (error: SimklReimportException) {
            assertEquals(R.string.simkl_reimport_not_verified, error.messageRes)
        }
        coVerify(exactly = 0) { library.clearAfterVerifiedImport(any(), any()) }
        coVerify(exactly = 0) { progress.clearAfterVerifiedImport(any(), any()) }
        coVerify(exactly = 0) { watched.clearAfterVerifiedImport(any(), any()) }
        coVerify(exactly = 0) { settings.setLibrarySourceMode(any(), any()) }
    }

    @Test
    fun `unmatched upload prevents downloading and deleting local data`() = runTest {
        prepare()
        coEvery { mutations.moveToList(any(), any(), authScope, false) } returns TrackingMutationResult(1, 1)
        try {
            service.uploadAndReimport()
            fail("A rejected upload must stop reimport")
        } catch (error: SimklReimportException) {
            assertEquals(R.string.simkl_reimport_unmatched, error.messageRes)
        }
        coVerify(exactly = 0) { repository.reloadFromServer(any()) }
        coVerify(exactly = 0) { library.clearAfterVerifiedImport(any(), any()) }
    }

    @Test
    fun `local resume position stops before any remote writes`() = runTest {
        prepare()
        val resume = WatchProgress("tt1", "movie", "One", null, null, null, "tt1",
            null, null, null, 1234L, 10000L, 10L)
        coEvery { progress.getAllRawEntries(1) } returns mapOf("tt1" to resume)
        try {
            service.uploadAndReimport()
            fail("Exact local resume data must not be discarded")
        } catch (error: SimklReimportException) {
            assertEquals(R.string.simkl_reimport_resume_unsupported, error.messageRes)
        }
        coVerify(exactly = 0) { mutations.moveToList(any(), any(), any(), any()) }
        coVerify(exactly = 0) { mutations.addToHistory(any(), any(), any()) }
        coVerify(exactly = 0) { library.clearAfterVerifiedImport(any(), any()) }
    }

    @Test
    fun `verified fresh import selects Simkl before clearing the old local records`() = runTest {
        prepare()
        val fresh = SimklSnapshotProjection.create(SimklSyncSnapshot(isInitialized = true, entries = listOf(
            SimklLibraryEntry(mediaType = SimklMediaType.MOVIES, status = SimklListStatus.PLAN_TO_WATCH,
                movie = SimklMedia(title = "One", ids = mapOf("imdb" to JsonPrimitive("tt1"))))
        )))
        coEvery { repository.reloadFromServer(any()) } coAnswers {
            firstArg<suspend (SimklSnapshotProjection) -> Unit>()(fresh)
        }
        service.uploadAndReimport()
        coVerifyOrder {
            mutations.moveToList(any(), any(), authScope, false)
            repository.reloadFromServer(any())
            settings.setLibrarySourceMode(LibrarySourceMode.SIMKL, 1)
            settings.setWatchProgressSource(WatchProgressSource.SIMKL, 1)
            library.clearAfterVerifiedImport(localLibrary, 1)
            progress.clearAfterVerifiedImport(emptyMap(), 1)
            watched.clearAfterVerifiedImport(emptyList(), 1)
        }
    }
}
