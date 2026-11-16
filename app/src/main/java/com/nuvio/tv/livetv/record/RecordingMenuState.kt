package com.nuvio.tv.livetv.record

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class RecordingMenuState(val hasRecordings: Boolean, val isRecording: Boolean)

internal fun List<LiveTvRecording>.recordingMenuState() =
    RecordingMenuState(isNotEmpty(), any { it.isRecording })

/** Only structural recording changes affect navigation; byte and duration progress do not. */
internal fun Flow<List<LiveTvRecording>>.recordingMenuStates(): Flow<RecordingMenuState> =
    map { it.recordingMenuState() }.distinctUntilChanged()
