package com.nuvio.tv.livetv.record

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingMenuStateTest {
    private val recording = LiveTvRecording(
        id = "1", channelKey = "channel", channelName = "Channel", channelLogo = null,
        title = "Show", startedAtMs = 1, stopAtMs = 1000, dir = "/recording"
    )

    @Test fun `500 progress updates do not invalidate navigation`() = runBlocking {
        val changes = buildList {
            add(emptyList<LiveTvRecording>())
            repeat(500) { add(listOf(recording.copy(sizeBytes = it.toLong(), durationSec = it.toDouble()))) }
            add(listOf(recording.copy(status = RecordingStatus.DONE)))
            add(listOf(recording.copy(status = RecordingStatus.FAILED)))
            add(emptyList())
        }.asFlow().recordingMenuStates().toList()
        assertEquals(listOf(
            RecordingMenuState(false, false), RecordingMenuState(true, true),
            RecordingMenuState(true, false), RecordingMenuState(false, false)
        ), changes)
    }

    @Test fun `recording indicator stays on until the last active recording finishes`() {
        val done = recording.copy(id = "2", status = RecordingStatus.DONE)
        assertEquals(RecordingMenuState(true, true), listOf(done, recording).recordingMenuState())
        assertEquals(RecordingMenuState(true, false), listOf(done).recordingMenuState())
    }
}
