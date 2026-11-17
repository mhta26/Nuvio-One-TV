package com.nuvio.tv.core.diagnostics

import org.junit.Assert.*
import org.junit.Test

class PerformanceSessionTest {
    @Test fun disabledRecorderCollectsNoFramesOrEvents() {
        val session = PerformanceSession()
        session.navigate("home", 10)
        session.frame(150_000_000, true)
        session.operation(PerformanceOperation.POSTER, 100, 90, false)
        assertTrue(session.snapshot().events.isEmpty())
        assertTrue(session.snapshot().frames.isEmpty())
    }
    @Test fun recordingIsBoundedAndContainsNoRouteArguments() {
        val session = PerformanceSession(3).apply { setEnabled(true) }
        repeat(100) { session.navigate("player/https://secret.test/token?password=hidden", it.toLong()) }
        val snapshot = session.snapshot()
        assertEquals(3, snapshot.events.size)
        assertEquals(97L, snapshot.events.first().elapsedMs)
        assertEquals(PerformanceScreen.PLAYER, snapshot.events.first().screen)
        assertFalse(snapshot.toString().contains("secret"))
        assertFalse(snapshot.toString().contains("password"))
    }
    @Test fun countersAndBucketsRetainLongStallsWithoutRetainingEveryFrame() {
        val session = PerformanceSession().apply { setEnabled(true); navigate("library", 0) }
        listOf(8L, 16L, 33L, 50L, 100L, 250L, 500L).forEach { session.frame(it * 1_000_000, it > 33) }
        val frames = session.snapshot().frames.getValue(PerformanceScreen.LIBRARY)
        assertEquals(7L, frames.frames)
        assertEquals(4L, frames.jankyFrames)
        assertEquals(3L, frames.over50ms)
        assertEquals(2L, frames.over100ms)
        assertEquals(500.0, frames.maxMs, 0.001)
        assertEquals(List(7) { 1L }, frames.histogram)
    }
    @Test fun stopPreservesExportableDataAndClearResetsIt() {
        val session = PerformanceSession().apply { setEnabled(true); navigate("home", 0); frame(20_000_000, true) }
        session.setEnabled(false)
        session.frame(100_000_000, true)
        assertEquals(1L, session.snapshot().frames.getValue(PerformanceScreen.HOME).frames)
        session.clear()
        assertTrue(session.snapshot().frames.isEmpty())
        assertTrue(session.snapshot().events.isEmpty())
    }
    @Test fun unknownDestinationsAndNegativeTimingAreSafe() {
        val session = PerformanceSession().apply { setEnabled(true); navigate("token-secret/value", 0) }
        session.frame(-1, true)
        session.navigationFrame(2, -10)
        assertEquals(PerformanceScreen.OTHER, session.snapshot().events.last().screen)
        assertEquals(0L, session.snapshot().events.last().durationMs)
        assertTrue(session.snapshot().frames.isEmpty())
    }
    @Test fun exportSnapshotIsIndependentOfLaterChanges() {
        val session = PerformanceSession().apply { setEnabled(true); navigate("home", 0); frame(10_000_000, false) }
        val snapshot = session.snapshot()
        session.frame(200_000_000, true)
        session.clear()
        assertEquals(1L, snapshot.frames.getValue(PerformanceScreen.HOME).frames)
        assertEquals(1, snapshot.events.size)
    }
}
