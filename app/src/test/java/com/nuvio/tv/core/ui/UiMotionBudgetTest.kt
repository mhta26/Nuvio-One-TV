package com.nuvio.tv.core.ui

import org.junit.Assert.*
import org.junit.Test

class UiMotionBudgetTest {
    @Test fun largeHeapDoesNotEnableOverlappingSettingsPanesOnTwoGbTv() {
        assertTrue(reduceSettingsMotion(2L * 1024 * 1024 * 1024, false, false))
        assertTrue(reduceSettingsMotion(3L * 1024 * 1024 * 1024, false, false))
    }
    @Test fun highMemoryKeepsMotionUnlessSystemOrHeapIsConstrained() {
        val ram = 8L * 1024 * 1024 * 1024
        assertFalse(reduceSettingsMotion(ram, false, false))
        assertTrue(reduceSettingsMotion(ram, true, false))
        assertTrue(reduceSettingsMotion(ram, false, true))
        assertFalse(reduceSettingsMotion(0, false, false))
    }
}
