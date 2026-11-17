package com.nuvio.tv.core.image

import org.junit.Assert.*
import org.junit.Test

class FailedPosterRegistryTest {
    @Test fun deduplicatesFailuresAndBoundsMemory() {
        val registry = FailedPosterRegistry(2)
        registry.failed("a"); registry.failed("a"); registry.failed("b"); registry.failed("c")
        assertEquals(2, registry.count.value)
        assertEquals(listOf("b", "c"), registry.batch().map { it.first })
    }
    @Test fun oldRetryCannotRemoveANewerFailure() {
        val registry = FailedPosterRegistry()
        registry.failed("a")
        val old = registry.batch().single()
        registry.failed("a")
        registry.resolved(old.first, old.second)
        assertEquals(1, registry.count.value)
        val current = registry.batch().single()
        registry.resolved(current.first, current.second)
        assertEquals(0, registry.count.value)
    }
    @Test fun successRemovesOnlyItsFailureAndBatchNeverReturnsGoodImages() {
        val registry = FailedPosterRegistry()
        registry.failed("a"); registry.failed("b"); registry.succeeded("a")
        assertEquals(listOf("b"), registry.batch().map { it.first })
        assertTrue(registry.batch(0).isEmpty())
        repeat(20) { registry.failed("$it") }
        assertEquals(10, registry.batch().size)
    }
}
