package com.nuvio.tv.core.diagnostics

import com.nuvio.tv.core.player.LastPlaybackDiagnostics
import org.junit.Assert.*
import org.junit.Test

class PerformanceReportFieldsTest {
    @Test fun playbackExportAllowlistExcludesSecretsEvenInsideErrors() {
        val d = LastPlaybackDiagnostics(host = "private.test", streamUrl = "https://private.test/?token=SECRET",
            headersJson = "Authorization: SECRET", result = "Error: SECRET https://private.test", vodCacheStats = "SECRET",
            videoCodec = "SECRET", firstFrameMs = 1234, rebufferCount = 2, rebufferTotalMs = 5000)
        val json = playbackPerformanceFields(d)
        assertFalse(json.toString().contains("SECRET"))
        assertFalse(json.toString().contains("private.test"))
        assertEquals("error", json.getString("outcome"))
        assertEquals(1234L, json.getLong("first_frame_ms"))
        assertEquals(2, json.getInt("rebuffer_count"))
        assertEquals(5000L, json.getLong("rebuffer_total_ms"))
        assertEquals(6, json.length())
    }
    @Test fun redactedStalkerFailureStillReportsAnError() {
        assertEquals("error", playbackPerformanceFields(LastPlaybackDiagnostics(result = "Stalker playback failed")).getString("outcome"))
    }
    @Test fun unknownOutcomeNeverExportsFreeText() {
        assertEquals("pending", playbackPerformanceFields(LastPlaybackDiagnostics(result = "account@example.test")).getString("outcome"))
        assertEquals("played", playbackPerformanceFields(LastPlaybackDiagnostics(result = "Played")).getString("outcome"))
    }
}
