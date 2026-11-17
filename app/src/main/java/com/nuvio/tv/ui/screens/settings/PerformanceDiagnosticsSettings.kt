@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.R

@Composable
internal fun PerformanceDiagnosticsSettings(viewModel: PerformanceDiagnosticsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val retrying by viewModel.retrying.collectAsStateWithLifecycle()
    val count by viewModel.failedCount.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.save(uri)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.saveDownloads() else viewModel.storageDenied()
    }
    val shareTitle = stringResource(R.string.performance_share)
    SettingsGroupCard(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.performance_title)) {
        SettingsToggleRow(title = stringResource(R.string.performance_record), subtitle = stringResource(R.string.performance_record_description),
            checked = enabled, onToggle = { viewModel.setEnabled(!enabled) })
        SettingsActionRow(title = stringResource(R.string.performance_save), subtitle = stringResource(R.string.performance_privacy),
            value = if (busy) stringResource(R.string.performance_exporting) else null, enabled = !busy,
            onClick = { if (!busy) { try { save.launch("Nuvio-One-TV-performance.json") } catch (_: Exception) { viewModel.unavailable() } } })
        SettingsActionRow(title = stringResource(R.string.performance_downloads), subtitle = stringResource(R.string.performance_downloads_description),
            enabled = !busy, onClick = {
                if (android.os.Build.VERSION.SDK_INT >= 29 || androidx.core.content.ContextCompat.checkSelfPermission(context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    viewModel.saveDownloads()
                } else {
                    try { storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                    catch (_: Exception) { viewModel.unavailable() }
                }
            })
        SettingsActionRow(title = shareTitle, subtitle = stringResource(R.string.performance_share_description), enabled = !busy, onClick = {
            viewModel.export { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("performance", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { context.startActivity(Intent.createChooser(intent, shareTitle)) } catch (_: android.content.ActivityNotFoundException) { viewModel.unavailable() }
            }
        })
        SettingsActionRow(title = stringResource(R.string.performance_clear), subtitle = stringResource(R.string.performance_clear_description), onClick = viewModel::clear)
        SettingsActionRow(title = stringResource(if (retrying) R.string.poster_retry_cancel else R.string.poster_retry_title),
            subtitle = stringResource(R.string.poster_retry_description, count), enabled = count > 0 || retrying, onClick = { if (count > 0 || retrying) viewModel.retry() })
        message?.let { Text(stringResource(it)) }
    }
}
