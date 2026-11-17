package com.nuvio.tv.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import com.nuvio.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Opt-in local recorder. Export is explicit; nothing here uploads or collects logcat. */
object PerformanceDiagnostics {
    val session = PerformanceSession()
    private var processStartedAt = 0L
    private val drawnAfterMs = AtomicLong(-1L)
    fun init(context: Context) {
        processStartedAt = SystemClock.elapsedRealtime()
        session.setEnabled(context.getSharedPreferences("performance_diagnostics", Context.MODE_PRIVATE).getBoolean("enabled", false))
    }
    fun setEnabled(context: Context, enabled: Boolean) {
        session.setEnabled(enabled)
        context.getSharedPreferences("performance_diagnostics", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
    }
    fun elapsedMs() = (SystemClock.elapsedRealtime() - processStartedAt).coerceAtLeast(0)
    fun markDrawn() { drawnAfterMs.compareAndSet(-1, elapsedMs()) }
    fun operation(kind: PerformanceOperation, startedAt: Long, success: Boolean) {
        if (session.enabled) session.operation(kind, elapsedMs(), (System.nanoTime() - startedAt) / 1_000_000L, success)
    }
    suspend fun export(context: Context, playback: com.nuvio.tv.core.player.LastPlaybackDiagnostics? = null): File = withContext(Dispatchers.IO) {
        val snapshot = session.snapshot()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        val report = JSONObject().apply {
            put("schema", 1)
            put("app", JSONObject().put("version", BuildConfig.VERSION_NAME).put("version_code", BuildConfig.VERSION_CODE))
            put("device", JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT).put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                .put("low_ram", am.isLowRamDevice).put("memory_class_mb", am.memoryClass)
                .put("screen_width", context.resources.displayMetrics.widthPixels).put("screen_height", context.resources.displayMetrics.heightPixels))
            put("last_persisted_playback", playback?.let(::playbackPerformanceFields) ?: JSONObject.NULL)
            put("recording_enabled", session.enabled)
            put("process_elapsed_ms", elapsedMs())
            put("application_to_fully_drawn_ms", drawnAfterMs.get().takeIf { it >= 0 } ?: JSONObject.NULL)
            put("memory_at_export", JSONObject().put("heap_used_bytes", runtime.totalMemory() - runtime.freeMemory())
                .put("heap_max_bytes", runtime.maxMemory()).put("process_pss_kb", Debug.getPss())
                .put("system_available_bytes", memory.availMem).put("system_total_bytes", memory.totalMem).put("system_low_memory", memory.lowMemory))
            put("frame_bucket_upper_ms", JSONArray(listOf(8, 16.7, 33.4, 50, 100, 250, "above_250")))
            put("screens", JSONObject().apply { snapshot.frames.forEach { (screen, f) ->
                put(screen.name, JSONObject().put("frames", f.frames).put("janky_frames", f.jankyFrames)
                    .put("over_50_ms", f.over50ms).put("over_100_ms", f.over100ms).put("max_ui_ms", f.maxMs)
                    .put("histogram", JSONArray(f.histogram)))
            } })
            put("recent_events", JSONArray().apply { snapshot.events.forEach { e ->
                put(JSONObject().put("elapsed_ms", e.elapsedMs).put("kind", e.kind).put("screen", e.screen.name)
                    .put("duration_ms", e.durationMs).put("success", e.success))
            } })
            put("notes", "JankStats UI durations; destination-to-frame is not content/network readiness. Startup is from Application.onCreate. Memory is sampled only at export. DPAD timing ends at next animation callback, not display presentation. Last playback metrics depend on the active engine. No logcat, URLs, credentials or content titles collected.")
        }
        val dir = File(context.cacheDir, "performance-reports").apply { mkdirs() }
        // Fixed filename bounds disk usage and avoids accumulating reports containing device details.
        File(dir, "Nuvio-One-TV-performance.json").apply { writeText(report.toString(2)) }
    }
}
