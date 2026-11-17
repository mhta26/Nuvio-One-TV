package com.nuvio.tv.livetv.model

import com.nuvio.tv.livetv.startup.StartPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Only values consumed by the app shell. Player/guide settings must not rebuild navigation. */
internal data class AppMenuState(
    val showInSidebar: Boolean,
    val onDemandEnabled: Boolean,
    val onDemandInSidebar: Boolean,
    val recordingsInSidebar: Boolean,
    val calendarInMenu: Boolean,
    val sideMenuOrder: String,
    val sideMenuHidden: String,
    val sideMenuLine: Boolean,
    val sideMenuFit: Boolean,
    val startWithSports: String?,
    val startWithoutSports: String?
)
internal fun LiveTvSettings.appMenuState() = AppMenuState(
    showInSidebar, onDemandEnabled, onDemandInSidebar, recordingsInSidebar, calendarInMenu,
    sideMenuOrder, sideMenuHidden, sideMenuLine, sideMenuFit,
    StartPage.routeFor(this, true), StartPage.routeFor(this, false)
)
internal fun Flow<LiveTvSettings>.appMenuStates(): Flow<AppMenuState> = map { it.appMenuState() }.distinctUntilChanged()
