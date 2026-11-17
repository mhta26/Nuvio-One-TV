package com.nuvio.tv.core.diagnostics

import com.nuvio.tv.core.player.LastPlaybackDiagnostics
import org.json.JSONObject

/** Explicit allowlist: never serialize the playback snapshot itself (it holds stream headers/URLs). */
internal fun playbackPerformanceFields(d: LastPlaybackDiagnostics): JSONObject = JSONObject()
    .put("first_frame_ms", d.firstFrameMs).put("rebuffer_count", d.rebufferCount)
    .put("rebuffer_total_ms", d.rebufferTotalMs).put("buffer_engine", d.bufferEngineEnabled)
    .put("parallel_network", d.parallelNetworkEnabled)
    .put("outcome", when { (d.result.startsWith("Error") || d.result == "Stalker playback failed") -> "error"; d.result == "Played" -> "played"; else -> "pending" })
