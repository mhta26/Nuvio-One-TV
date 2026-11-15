package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.library.LibrarySyncLocalStore
import com.nuvio.tv.domain.model.LibraryDeltaApplyResult
import com.nuvio.tv.domain.model.LibraryDeltaEvent
import com.nuvio.tv.domain.model.LibrarySnapshotApplyResult
import com.nuvio.tv.domain.model.LibrarySyncKey
import com.nuvio.tv.domain.model.LibrarySyncReducer
import com.nuvio.tv.domain.model.LibrarySyncState
import com.nuvio.tv.domain.model.SavedLibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryPreferences @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) : LibrarySyncLocalStore {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val libraryItemsKey = stringSetPreferencesKey("library_items")
    private val sortOptionKey = stringPreferencesKey("library_sort_option")
    private val lastSelectedListKey = stringPreferencesKey("library_last_selected_list")
    private val lastSelectedTypeKey = stringPreferencesKey("library_last_selected_type")
    private val deltaCursorKey = longPreferencesKey("library_delta_cursor")
    private val deltaInitializedKey = booleanPreferencesKey("library_delta_initialized")
    private val pendingUpsertKeysKey = stringSetPreferencesKey("library_pending_upserts")
    private val pendingDeleteKeysKey = stringSetPreferencesKey("library_pending_deletes")
    private val mutationRevisionKey = longPreferencesKey("library_mutation_revision")

    private fun store(profileId: Int = profileManager.activeProfileId.value) =
        factory.get(profileId, FEATURE)

    val sortOption: Flow<String?> = profileManager.activeProfileId.flatMapLatest { profileId ->
        factory.get(profileId, FEATURE).data.map { preferences ->
            preferences[sortOptionKey]
        }
    }

    private val sharedLibraryItems =
        profileManager.activeProfileId.flatMapLatest { profileId ->
            // Sorting, tab selection and sync cursor writes do not change library contents.
            factory.get(profileId, FEATURE).data
                .map { preferences ->
                    Triple(preferences[libraryItemsKey].orEmpty(),
                        preferences[pendingUpsertKeysKey].orEmpty(),
                        preferences[pendingDeleteKeysKey].orEmpty())
                }
                .distinctUntilChanged()
                .map { (items, upserts, deletes) ->
                    profileId to LibrarySyncReducer.sanitize(
                        LibrarySyncState(
                            items = decodeRawSet(items, SavedLibraryItem::class.java),
                            pendingUpsertKeys = decodeRawSet(upserts, LibrarySyncKey::class.java),
                            pendingDeleteKeys = decodeRawSet(deletes, LibrarySyncKey::class.java)
                        )
                    ).items
                }
                .flowOn(Dispatchers.Default)
        }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val libraryItems: Flow<List<SavedLibraryItem>> = combine(
        profileManager.activeProfileId, sharedLibraryItems
    ) { activeId, (sourceId, items) ->
        if (activeId == sourceId) items else emptyList()
    }.distinctUntilChanged()

    /** Local cleanup after a verified Simkl import; never creates remote delete mutations. */
    suspend fun clearAfterVerifiedImport(expected: LibrarySyncState, profileId: Int) {
        store(profileId).edit { preferences ->
            check(preferences.toLibrarySyncState() == expected) { "Library changed during import; retry" }
            preferences.writeLibrarySyncState(LibrarySyncState())
        }
    }

    suspend fun setSortOption(key: String) {
        store().edit { preferences ->
            preferences[sortOptionKey] = key
        }
    }

    val lastSelectedList: Flow<String?> = profileManager.activeProfileId.flatMapLatest { profileId ->
        factory.get(profileId, FEATURE).data.map { preferences ->
            preferences[lastSelectedListKey]
        }
    }

    suspend fun setLastSelectedList(key: String) {
        store().edit { preferences ->
            preferences[lastSelectedListKey] = key
        }
    }

    val lastSelectedType: Flow<String?> = profileManager.activeProfileId.flatMapLatest { profileId ->
        factory.get(profileId, FEATURE).data.map { preferences ->
            preferences[lastSelectedTypeKey]
        }
    }

    suspend fun setLastSelectedType(key: String) {
        store().edit { preferences ->
            preferences[lastSelectedTypeKey] = key
        }
    }

    fun isInLibrary(itemId: String, itemType: String): Flow<Boolean> {
        return libraryItems.map { items ->
            items.any { item ->
                item.id == itemId && item.type.equals(itemType, ignoreCase = true)
            }
        }
    }

    suspend fun containsItem(
        itemId: String,
        itemType: String,
        profileId: Int = profileManager.activeProfileId.value
    ): Boolean {
        return getSyncState(profileId).items.any { item ->
            item.id == itemId && item.type.equals(itemType, ignoreCase = true)
        }
    }

    suspend fun addItem(
        item: SavedLibraryItem,
        profileId: Int = profileManager.activeProfileId.value
    ) {
        store(profileId).edit { preferences ->
            val updated = LibrarySyncReducer.upsertLocal(
                state = preferences.toLibrarySyncState(),
                item = item,
                nowEpochMs = System.currentTimeMillis()
            )
            preferences.writeLibrarySyncState(updated)
        }
    }

    suspend fun removeItem(
        itemId: String,
        itemType: String,
        profileId: Int = profileManager.activeProfileId.value
    ) {
        store(profileId).edit { preferences ->
            val updated = LibrarySyncReducer.deleteLocal(
                state = preferences.toLibrarySyncState(),
                contentId = itemId,
                contentType = itemType
            )
            preferences.writeLibrarySyncState(updated)
        }
    }

    suspend fun getAllItems(
        profileId: Int = profileManager.activeProfileId.value
    ): List<SavedLibraryItem> {
        return getSyncState(profileId).items
    }

    suspend fun updateLogo(
        id: String,
        type: String,
        logo: String,
        profileId: Int = profileManager.activeProfileId.value
    ) {
        store(profileId).edit { preferences ->
            val state = preferences.toLibrarySyncState()
            val updatedItems = state.items.map { item ->
                if (item.id == id && item.type.equals(type, ignoreCase = true)) {
                    item.copy(logo = logo)
                } else {
                    item
                }
            }
            preferences.writeLibrarySyncState(state.copy(items = updatedItems))
        }
    }

    override suspend fun getSyncState(profileId: Int): LibrarySyncState {
        return store(profileId).data.first().toLibrarySyncState()
    }

    override suspend fun applyRemoteSnapshot(
        profileId: Int,
        remoteItems: Collection<SavedLibraryItem>,
        cursorEventId: Long
    ): LibrarySnapshotApplyResult {
        lateinit var result: LibrarySnapshotApplyResult
        store(profileId).edit { preferences ->
            result = LibrarySyncReducer.applySnapshot(
                state = preferences.toLibrarySyncState(),
                remoteItems = remoteItems,
                cursorEventId = cursorEventId
            )
            preferences.writeLibrarySyncState(result.state)
        }
        return result
    }

    override suspend fun applyRemoteDelta(
        profileId: Int,
        events: Collection<LibraryDeltaEvent>
    ): LibraryDeltaApplyResult {
        lateinit var result: LibraryDeltaApplyResult
        store(profileId).edit { preferences ->
            result = LibrarySyncReducer.applyDelta(
                state = preferences.toLibrarySyncState(),
                events = events
            )
            preferences.writeLibrarySyncState(result.state)
        }
        return result
    }

    override suspend fun queueAllItemsForPush(profileId: Int): LibrarySyncState {
        lateinit var result: LibrarySyncState
        store(profileId).edit { preferences ->
            result = LibrarySyncReducer.queueAllItemsForPush(
                preferences.toLibrarySyncState()
            )
            preferences.writeLibrarySyncState(result)
        }
        return result
    }

    override suspend fun acknowledgePush(
        profileId: Int,
        expectedMutationRevision: Long
    ): Boolean {
        var acknowledged = false
        store(profileId).edit { preferences ->
            val updated = LibrarySyncReducer.acknowledgePush(
                state = preferences.toLibrarySyncState(),
                expectedMutationRevision = expectedMutationRevision
            )
            if (updated != null) {
                preferences.writeLibrarySyncState(updated)
                acknowledged = true
            }
        }
        return acknowledged
    }

    private fun Preferences.toLibrarySyncState(): LibrarySyncState {
        return LibrarySyncReducer.sanitize(
            LibrarySyncState(
                items = decodeSet(libraryItemsKey, SavedLibraryItem::class.java),
                deltaCursorEventId = this[deltaCursorKey] ?: 0L,
                deltaInitialized = this[deltaInitializedKey] ?: false,
                pendingUpsertKeys = decodeSet(pendingUpsertKeysKey, LibrarySyncKey::class.java),
                pendingDeleteKeys = decodeSet(pendingDeleteKeysKey, LibrarySyncKey::class.java),
                mutationRevision = this[mutationRevisionKey] ?: 0L
            )
        )
    }

    private fun MutablePreferences.writeLibrarySyncState(state: LibrarySyncState) {
        val sanitized = LibrarySyncReducer.sanitize(state)
        this[libraryItemsKey] = sanitized.items.map(gson::toJson).toSet()
        this[deltaCursorKey] = sanitized.deltaCursorEventId
        this[deltaInitializedKey] = sanitized.deltaInitialized
        this[pendingUpsertKeysKey] = sanitized.pendingUpsertKeys.map(gson::toJson).toSet()
        this[pendingDeleteKeysKey] = sanitized.pendingDeleteKeys.map(gson::toJson).toSet()
        this[mutationRevisionKey] = sanitized.mutationRevision
    }

    private fun <T> Preferences.decodeSet(
        key: Preferences.Key<Set<String>>,
        type: Class<T>
    ): List<T> {
        return decodeRawSet(this[key].orEmpty(), type)
    }

    private fun <T> decodeRawSet(raw: Set<String>, type: Class<T>): List<T> =
        raw.mapNotNull { value -> runCatching { gson.fromJson(value, type) }.getOrNull() }

    private companion object {
        const val FEATURE = "library_preferences"
    }
}
