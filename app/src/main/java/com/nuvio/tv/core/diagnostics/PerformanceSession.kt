package com.nuvio.tv.core.diagnostics

/** Bounded, typed measurements only: no URLs, titles, account IDs or exception messages. */
enum class PerformanceScreen { HOME, LIBRARY, DETAILS, PLAYER, STREAMS, LIVE_TV, SETTINGS, SEARCH, DISCOVER, SPORTS, CALENDAR, MUSIC, YOUTUBE, RECORDINGS, ON_DEMAND, ACCOUNT, OTHER }
enum class PerformanceOperation { POSTER, POSTER_FALLBACK, POSTER_RETRY, DPAD_TO_NEXT_VSYNC }
data class FrameSummary(val frames: Long, val jankyFrames: Long, val over50ms: Long, val over100ms: Long, val maxMs: Double, val histogram: List<Long>)
data class PerformanceEvent(val elapsedMs: Long, val kind: String, val screen: PerformanceScreen, val durationMs: Long, val success: Boolean)
data class PerformanceSnapshot(val frames: Map<PerformanceScreen, FrameSummary>, val events: List<PerformanceEvent>)

internal fun performanceScreen(route: String?): PerformanceScreen = when (route?.substringBefore('/')?.substringBefore('?')?.lowercase()) {
    "home" -> PerformanceScreen.HOME
    "library" -> PerformanceScreen.LIBRARY
    "metadetails", "detail", "details" -> PerformanceScreen.DETAILS
    "player" -> PerformanceScreen.PLAYER
    "stream", "streams" -> PerformanceScreen.STREAMS
    "livetv", "live_tv" -> PerformanceScreen.LIVE_TV
    "discover" -> PerformanceScreen.DISCOVER
    "sports" -> PerformanceScreen.SPORTS
    "calendar" -> PerformanceScreen.CALENDAR
    "music" -> PerformanceScreen.MUSIC
    "youtube" -> PerformanceScreen.YOUTUBE
    "recordings" -> PerformanceScreen.RECORDINGS
    "on_demand" -> PerformanceScreen.ON_DEMAND
    "account", "auth_sign_in", "manage_profiles" -> PerformanceScreen.ACCOUNT
    "live_tv_player" -> PerformanceScreen.LIVE_TV
    "settings", "live_tv_settings", "playback_settings", "theme_settings", "layout_settings", "tmdb_settings" -> PerformanceScreen.SETTINGS
    "search" -> PerformanceScreen.SEARCH
    else -> PerformanceScreen.OTHER
}

class PerformanceSession(private val maxEvents: Int = 120) {
    private class Frames {
        var count = 0L; var jank = 0L; var over50 = 0L; var over100 = 0L; var max = 0.0
        val buckets = LongArray(7)
    }
    init { require(maxEvents > 0) }
    private val frames = mutableMapOf<PerformanceScreen, Frames>()
    private val events = ArrayDeque<PerformanceEvent>()
    @Volatile var enabled = false
        private set
    @Volatile var screen = PerformanceScreen.OTHER
        private set

    @Synchronized fun setEnabled(value: Boolean) { enabled = value }
    @Synchronized fun clear() { frames.clear(); events.clear() }
    @Synchronized fun navigate(route: String?, elapsedMs: Long) {
        screen = performanceScreen(route)
        if (enabled) add(PerformanceEvent(elapsedMs, "destination_committed", screen, 0, true))
    }
    @Synchronized fun navigationFrame(elapsedMs: Long, durationMs: Long) {
        if (enabled) add(PerformanceEvent(elapsedMs, "destination_to_next_frame", screen, durationMs.coerceAtLeast(0), true))
    }
    fun frame(durationNanos: Long, janky: Boolean) {
        if (!enabled || durationNanos < 0) return
        synchronized(this) {
            if (!enabled) return
            val f = frames.getOrPut(screen) { Frames() }
            val ms = durationNanos / 1_000_000.0
            f.count++; if (janky) f.jank++; if (ms > 50) f.over50++; if (ms > 100) f.over100++
            f.max = maxOf(f.max, ms)
            val bucket = when { ms <= 8 -> 0; ms <= 16.7 -> 1; ms <= 33.4 -> 2; ms <= 50 -> 3; ms <= 100 -> 4; ms <= 250 -> 5; else -> 6 }
            f.buckets[bucket]++
        }
    }
    @Synchronized fun operation(kind: PerformanceOperation, elapsedMs: Long, durationMs: Long, success: Boolean) {
        if (enabled) add(PerformanceEvent(elapsedMs, kind.name, screen, durationMs.coerceAtLeast(0), success))
    }
    private fun add(event: PerformanceEvent) {
        if (events.size >= maxEvents) events.removeFirst()
        events.addLast(event)
    }
    @Synchronized fun snapshot() = PerformanceSnapshot(
        frames.mapValues { (_, f) -> FrameSummary(f.count, f.jank, f.over50, f.over100, f.max, f.buckets.toList()) }, events.toList()
    )
}
