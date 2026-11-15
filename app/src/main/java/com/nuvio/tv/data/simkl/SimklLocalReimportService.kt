package com.nuvio.tv.data.simkl

import com.nuvio.tv.R
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.tracking.TrackingHistoryItem
import com.nuvio.tv.core.tracking.TrackingListStatus
import com.nuvio.tv.core.tracking.TrackingMediaKind
import com.nuvio.tv.core.tracking.TrackingRefreshIntent
import com.nuvio.tv.core.tracking.TrackingMediaReference
import com.nuvio.tv.data.local.ContinueWatchingEnrichmentCache
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.local.WatchProgressSource
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.domain.model.LibrarySourceMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SimklReimportException(val messageRes: Int) : Exception()

/** Upload, verify a full independent server read, then discard the old local records.
 * No local backup is restored and no remote delete endpoint is called.
 */
@Singleton
class SimklLocalReimportService @Inject constructor(
    private val profiles: ProfileManager,
    private val auth: SimklAuthStorage,
    private val repository: SimklSyncRepository,
    private val mutations: SimklMutationService,
    private val library: LibraryPreferences,
    private val progress: WatchProgressPreferences,
    private val watched: WatchedItemsPreferences,
    private val settings: TraktSettingsDataStore,
    private val enrichment: ContinueWatchingEnrichmentCache,
    private val watchedState: WatchedSeriesStateHolder
) {
    private val mutex = Mutex()

    suspend fun uploadAndReimport() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val authorization = auth.authorization()
                ?: throw SimklReimportException(R.string.simkl_reimport_sign_in)
            val profileId = profiles.activeProfileId.value
            fun checkScope() {
                check(profileId == profiles.activeProfileId.value && auth.isCurrent(authorization.scope)) {
                    "Profile or Simkl account changed; retry"
                }
            }
            checkScope()
            repository.ensureLoaded()
            // Refresh remote memberships before deciding which local titles are new.
            repository.refresh(TrackingRefreshIntent.USER_INITIATED)
            if (repository.state.value.errorMessage != null) {
                throw SimklReimportException(R.string.simkl_reimport_failed)
            }
            checkScope()
            val localLibrary = library.getSyncState(profileId)
            val localProgress = progress.getAllRawEntries(profileId)
            val localWatched = watched.getAllItems(profileId)
            val snapshot = repository.state.value.snapshot
            val index = repository.currentEntryIndex()

            // Simkl's resume representation cannot round-trip exact local positions,
            // thresholds or excluded seasons. Refuse a destructive migration of those fields.
            if (localProgress.values.any {
                !it.isCompleted() || it.excludedNextUpSeasons.isNotEmpty() || it.completionThresholdOverride != null
            }) throw SimklReimportException(R.string.simkl_reimport_resume_unsupported)
            if (localLibrary.pendingDeleteKeys.isNotEmpty()) {
                throw SimklReimportException(R.string.simkl_reimport_pending_deletes)
            }

            fun reference(id: String, type: String, title: String, release: String? = null,
                          season: Int? = null, episode: Int? = null): TrackingMediaReference {
                val ref = snapshot.mediaReference(
                    contentId = id, contentType = type, title = title, releaseInfo = release,
                    season = season, episode = episode, entryIndex = index
                ).resolveAnimeEpisodeForSimkl()
                // A title-only match could identify a remake or a different show.
                if (!ref.ids.hasAny || (type !in setOf("movie", "film", "series", "tv", "show", "anime"))) {
                    throw SimklReimportException(R.string.simkl_reimport_unmatched)
                }
                return ref
            }
            val libraryRefs = localLibrary.items.map { item ->
                reference(item.id, item.type.lowercase(), item.name, item.releaseInfo)
            }
            val history = localWatched.map { item ->
                TrackingHistoryItem(reference(item.contentId, item.contentType.lowercase(), item.title,
                    item.releaseInfo, item.season, item.episode), item.watchedAt)
            } + localProgress.values.map { item ->
                TrackingHistoryItem(reference(item.contentId, item.contentType.lowercase(), item.name,
                    season = item.season, episode = item.episode), item.lastWatched)
            }
            if (history.any { it.media.kind != TrackingMediaKind.MOVIE && it.media.episode == null }) {
                throw SimklReimportException(R.string.simkl_reimport_unmatched)
            }
            // Do not move existing Simkl entries back to Plan to Watch.
            val newLibraryRefs = libraryRefs.filterIndexed { position, _ ->
                snapshot.entries.none { it.matchesSimklContentId(localLibrary.items[position].id) }
            }
            newLibraryRefs.chunked(100).forEach { batch ->
                checkScope()
                if (!mutations.moveToList(batch, TrackingListStatus.PLAN_TO_WATCH,
                        expectedAuthScope = authorization.scope, updateLocalSnapshot = false).isComplete) {
                    throw SimklReimportException(R.string.simkl_reimport_unmatched)
                }
            }
            history.distinctBy { Triple(it.media.stableKey, it.media.episode?.season, it.media.episode?.number) }
                .chunked(100).forEach { batch ->
                    checkScope()
                    if (!mutations.addToHistory(batch, expectedAuthScope = authorization.scope,
                            updateLocalSnapshot = false).isComplete) {
                        throw SimklReimportException(R.string.simkl_reimport_unmatched)
                    }
                }
            checkScope()
            repository.reloadFromServer { fresh ->
                checkScope()
                val libraryPresent = localLibrary.items.all { item ->
                    val type = if (item.type.lowercase() in setOf("tv", "show", "anime")) "series" else item.type
                    fresh.membershipKey(item.id, type) != null
                }
                val watchedPresent = localWatched.all { item ->
                    fresh.isWatched(item.contentId, null, item.season, item.episode)
                } && localProgress.values.all { item ->
                    fresh.isWatched(item.contentId, item.videoId, item.season, item.episode)
                }
                if (!libraryPresent || !watchedPresent) {
                    throw SimklReimportException(R.string.simkl_reimport_not_verified)
                }
                // A concurrent edit must survive this operation.
                check(library.getSyncState(profileId) == localLibrary &&
                    progress.getAllRawEntries(profileId) == localProgress &&
                    watched.getAllItems(profileId).toSet() == localWatched.toSet()) {
                    "Local data changed during import; retry"
                }
            }
            checkScope()
            settings.setLibrarySourceMode(LibrarySourceMode.SIMKL, profileId)
            settings.setWatchProgressSource(WatchProgressSource.SIMKL, profileId)
            checkScope()
            library.clearAfterVerifiedImport(localLibrary, profileId)
            checkScope()
            progress.clearAfterVerifiedImport(localProgress, profileId)
            checkScope()
            watched.clearAfterVerifiedImport(localWatched, profileId)
            checkScope()
            enrichment.saveInProgressSnapshot(emptyList(), force = true)
            enrichment.saveNextUpSnapshot(emptyList(), force = true)
            watchedState.update(emptySet())
        }
    }
}
