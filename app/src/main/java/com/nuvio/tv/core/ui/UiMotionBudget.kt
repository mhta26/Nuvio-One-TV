package com.nuvio.tv.core.ui

/** A large Java heap does not imply enough physical RAM to compose two settings panes at once. */
internal fun reduceSettingsMotion(physicalRamBytes: Long, lowHeapTier: Boolean, lowRamDevice: Boolean): Boolean =
    lowHeapTier || lowRamDevice || physicalRamBytes in 1L..(3L * 1024 * 1024 * 1024)
