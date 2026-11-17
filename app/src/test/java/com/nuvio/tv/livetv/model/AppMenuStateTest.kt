package com.nuvio.tv.livetv.model

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppMenuStateTest {
    @Test fun hundredsOfGuideAndPlayerChangesDoNotInvalidateTheShell() = runBlocking {
        val initial = LiveTvSettings()
        val states = buildList {
            add(initial)
            repeat(500) { add(initial.copy(infoBannerSeconds = it, showChannelNames = it % 2 == 0)) }
        }.asFlow().appMenuStates().toList()
        assertEquals(listOf(initial.appMenuState()), states)
    }
    @Test fun actualMenuChangesStillEmit() = runBlocking {
        val initial = LiveTvSettings()
        val hidden = initial.copy(showInSidebar = false)
        val reordered = hidden.copy(sideMenuOrder = "library,search")
        assertEquals(listOf(initial.appMenuState(), hidden.appMenuState(), reordered.appMenuState()),
            listOf(initial, hidden, hidden, reordered).asFlow().appMenuStates().toList())
    }
    @Test fun startupRouteRespectsSportsAndOnDemandVisibility() {
        val sports = LiveTvSettings(openOn = "SPORTS").appMenuState()
        assertEquals("sports", sports.startWithSports)
        assertNull(sports.startWithoutSports)
        val hidden = LiveTvSettings(openOn = "ON_DEMAND", onDemandEnabled = false).appMenuState()
        assertNull(hidden.startWithSports)
        assertNull(hidden.startWithoutSports)
    }
}
