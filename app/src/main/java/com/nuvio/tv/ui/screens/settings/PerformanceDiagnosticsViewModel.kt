package com.nuvio.tv.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.diagnostics.PerformanceDiagnostics
import com.nuvio.tv.core.image.FailedPosterRegistry
import com.nuvio.tv.core.image.PosterRetryService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

@HiltViewModel
class PerformanceDiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerSettings: com.nuvio.tv.data.local.PlayerSettingsDataStore,
    private val retries: PosterRetryService
) : ViewModel() {
    private val _enabled = MutableStateFlow(PerformanceDiagnostics.session.enabled)
    val enabled = _enabled.asStateFlow()
    val failedCount = FailedPosterRegistry.shared.count
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _retrying = MutableStateFlow(false)
    val retrying = _retrying.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private var retryJob: Job? = null
    fun setEnabled(value: Boolean) {
        PerformanceDiagnostics.setEnabled(context, value)
        _enabled.value = value
    }
    fun clear() { PerformanceDiagnostics.session.clear(); _message.value = R.string.performance_cleared }
    fun retry() {
        if (_retrying.value) { retryJob?.cancel(); return }
        _retrying.value = true
        retryJob = viewModelScope.launch {
            try {
                val result = retries.retryFailed()
                _message.value = if (result.recovered == result.attempted) R.string.poster_retry_done else R.string.poster_retry_partial
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { _message.value = R.string.poster_retry_partial
            } finally { _retrying.value = false }
        }
    }
    fun save(uri: Uri) = export { file ->
        withContext(Dispatchers.IO) {
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output -> file.inputStream().use { it.copyTo(output) } }
        }
        _message.value = R.string.performance_saved
    }
    @androidx.annotation.RequiresApi(29)
    fun saveDownloads() = export { file ->
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "Nuvio-One-TV-performance-${System.currentTimeMillis()}.json")
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/Nuvio-One-TV")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            try {
                requireNotNull(resolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
                resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
        }
        _message.value = R.string.performance_downloads_saved
    }
    fun export(onReady: suspend (File) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val playback = withContext(Dispatchers.IO) {
                    try { withTimeoutOrNull(1500) { playerSettings.lastPlaybackDiagnostics.first() } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
                onReady(PerformanceDiagnostics.export(context, playback))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { _message.value = R.string.performance_export_failed
            } finally { _busy.value = false }
        }
    }
    fun unavailable() { _message.value = R.string.performance_picker_unavailable }
}
