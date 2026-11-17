package com.nuvio.tv.data.simkl

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SimklCachedLibraryArtworkTest {
    @Test fun `collecting poster ID hints does not trigger Simkl refresh`() = runTest {
        val sync = mockk<SimklSyncRepository>(relaxed = true)
        every { sync.projection } returns MutableStateFlow(SimklSnapshotProjection.Empty)
        val service = SimklLibraryService(sync, mockk(relaxed = true), mockk(relaxed = true))
        assertTrue(service.cachedItems.first().isEmpty())
        coVerify(exactly = 0) { sync.refresh(any()) }
    }
}
