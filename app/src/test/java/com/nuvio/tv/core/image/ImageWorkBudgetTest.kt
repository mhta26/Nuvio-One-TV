package com.nuvio.tv.core.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageWorkBudgetTest {
    @Test
    fun `two GB TV with a large Java heap still limits image work`() {
        val budget = imageWorkBudgetFor(2L * 1024 * 1024 * 1024, lowHeapTier = false, lowRamDevice = false)
        assertEquals(8, budget.maxRequests)
        assertEquals(4, budget.maxRequestsPerHost)
        assertEquals(2, budget.decodeParallelism)
    }

    @Test
    fun `three GB TV also reserves capacity for navigation`() {
        val budget = imageWorkBudgetFor(3L * 1024 * 1024 * 1024, lowHeapTier = false, lowRamDevice = false)
        assertEquals(8, budget.maxRequests)
        assertEquals(2, budget.decodeParallelism)
    }

    @Test
    fun `Android low RAM flag is respected even with more reported RAM`() {
        val budget = imageWorkBudgetFor(4L * 1024 * 1024 * 1024, lowHeapTier = false, lowRamDevice = true)
        assertEquals(8, budget.maxRequests)
        assertEquals(2, budget.decodeParallelism)
    }

    @Test
    fun `unknown physical memory still respects a small heap`() {
        val budget = imageWorkBudgetFor(0L, lowHeapTier = true, lowRamDevice = false)
        assertEquals(8, budget.maxRequests)
        assertEquals(2, budget.decodeParallelism)
    }

    @Test
    fun `larger devices retain their existing concurrency`() {
        val budget = imageWorkBudgetFor(8L * 1024 * 1024 * 1024, lowHeapTier = false, lowRamDevice = false)
        assertEquals(32, budget.maxRequests)
        assertEquals(16, budget.maxRequestsPerHost)
        assertEquals(4, budget.decodeParallelism)
    }
}
