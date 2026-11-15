package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.core.image.ArtworkRefreshService
import com.nuvio.tv.R
import com.nuvio.tv.data.simkl.SimklLocalReimportService
import com.nuvio.tv.data.simkl.SimklReimportException
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.runtime.AppRestarter
import com.nuvio.tv.data.local.DeviceLocalPlayerPreferences
import com.nuvio.tv.data.local.ImagePerformancePreferences
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.SentrySettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdvancedSettingsUiState(
    val fastHorizontalNavigationEnabled: Boolean = false,
    val smoothBringIntoViewEnabled: Boolean = true,
    val composeHighlighterEnabled: Boolean = false,
    val playbackIssueReportsEnabled: Boolean = false,
    val playerStatsHudEnabled: Boolean = false,
    val rgb565Enabled: Boolean = true,
    val sentryEnabled: Boolean = true,
    val artworkRefreshing: Boolean = false,
    val artworkMessageRes: Int? = null,
    val reimportRunning: Boolean = false,
    val reimportMessageRes: Int? = null
)

sealed class AdvancedSettingsEvent {
    data class SetFastHorizontalNavigationEnabled(val enabled: Boolean) : AdvancedSettingsEvent()
    data class SetSmoothBringIntoViewEnabled(val enabled: Boolean) : AdvancedSettingsEvent()
    data class SetComposeHighlighterEnabled(val enabled: Boolean) : AdvancedSettingsEvent()
    data class SetPlaybackIssueReportsEnabled(val enabled: Boolean) : AdvancedSettingsEvent()
    data class SetPlayerStatsHudEnabled(val enabled: Boolean) : AdvancedSettingsEvent()
    data class SetRgb565Enabled(val enabled: Boolean) : AdvancedSettingsEvent()
    data class SetSentryEnabled(val enabled: Boolean) : AdvancedSettingsEvent()
}

@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val playerSettingsDataStore: PlayerSettingsDataStore,
    private val deviceLocalPlayerPreferences: DeviceLocalPlayerPreferences,
    private val sentrySettingsDataStore: SentrySettingsDataStore,
    private val imagePerformancePreferences: ImagePerformancePreferences,
    private val appRestarter: AppRestarter,
    private val simklReimport: SimklLocalReimportService,
    private val artworkRefresh: ArtworkRefreshService
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdvancedSettingsUiState())
    val uiState: StateFlow<AdvancedSettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(rgb565Enabled = imagePerformancePreferences.rgb565Enabled) }
        viewModelScope.launch {
            layoutPreferenceDataStore.fastHorizontalNavigationEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(fastHorizontalNavigationEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.smoothBringIntoViewEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(smoothBringIntoViewEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.composeHighlighterEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(composeHighlighterEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            playerSettingsDataStore.playerSettings.collectLatest { settings ->
                _uiState.update { it.copy(playbackIssueReportsEnabled = settings.playbackIssueReportsEnabled) }
            }
        }
        viewModelScope.launch {
            deviceLocalPlayerPreferences.playerStatsHudEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(playerStatsHudEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            sentrySettingsDataStore.enabled.collectLatest { enabled ->
                _uiState.update { it.copy(sentryEnabled = enabled) }
            }
        }
    }

    fun refreshArtwork() {
        if (_uiState.value.artworkRefreshing || _uiState.value.reimportRunning) return
        _uiState.update { it.copy(artworkRefreshing = true, artworkMessageRes = null) }
        viewModelScope.launch {
            try {
                artworkRefresh.clearArtworkCaches()
                _uiState.update { it.copy(artworkMessageRes = R.string.artwork_refresh_restart_manually) }
                appRestarter.restart()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(artworkMessageRes = R.string.artwork_refresh_failed) }
            } finally {
                _uiState.update { it.copy(artworkRefreshing = false) }
            }
        }
    }

    fun uploadAndReimportSimkl() {
        if (_uiState.value.reimportRunning || _uiState.value.artworkRefreshing) return
        _uiState.update { it.copy(reimportRunning = true, reimportMessageRes = null) }
        viewModelScope.launch {
            try {
                simklReimport.uploadAndReimport()
                _uiState.update { it.copy(reimportMessageRes = R.string.simkl_reimport_success) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(reimportMessageRes =
                    (error as? SimklReimportException)?.messageRes ?: R.string.simkl_reimport_failed) }
            } finally {
                _uiState.update { it.copy(reimportRunning = false) }
            }
        }
    }

    fun onEvent(event: AdvancedSettingsEvent) {
        when (event) {
            is AdvancedSettingsEvent.SetFastHorizontalNavigationEnabled -> {
                viewModelScope.launch {
                    layoutPreferenceDataStore.setFastHorizontalNavigationEnabled(event.enabled)
                }
            }
            is AdvancedSettingsEvent.SetSmoothBringIntoViewEnabled -> {
                viewModelScope.launch {
                    layoutPreferenceDataStore.setSmoothBringIntoViewEnabled(event.enabled)
                }
            }
            is AdvancedSettingsEvent.SetComposeHighlighterEnabled -> {
                viewModelScope.launch {
                    layoutPreferenceDataStore.setComposeHighlighterEnabled(event.enabled)
                }
            }
            is AdvancedSettingsEvent.SetPlaybackIssueReportsEnabled -> {
                viewModelScope.launch {
                    playerSettingsDataStore.setPlaybackIssueReportsEnabled(event.enabled)
                }
            }
            is AdvancedSettingsEvent.SetPlayerStatsHudEnabled -> {
                viewModelScope.launch {
                    deviceLocalPlayerPreferences.setPlayerStatsHudEnabled(event.enabled)
                }
            }
            is AdvancedSettingsEvent.SetRgb565Enabled -> {
                if (imagePerformancePreferences.setRgb565Enabled(event.enabled)) {
                    _uiState.update { it.copy(rgb565Enabled = event.enabled) }
                    appRestarter.restart()
                }
            }
            is AdvancedSettingsEvent.SetSentryEnabled -> {
                viewModelScope.launch {
                    sentrySettingsDataStore.setEnabled(event.enabled)
                }
            }
        }
    }
}
