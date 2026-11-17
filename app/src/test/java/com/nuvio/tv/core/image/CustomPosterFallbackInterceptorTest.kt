package com.nuvio.tv.core.image

import android.content.Context
import coil3.Image
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CustomPosterFallbackInterceptorTest {
    private val context = mockk<Context>(relaxed = true).also { ctx -> every { ctx.applicationContext } returns ctx }
    private val styled = "https://example.test/styled.jpg"
    private val raw = "https://example.test/raw.jpg"
    private fun request(fallback: String? = raw): ImageRequest = ImageRequest.Builder(context)
        .data(styled).memoryCacheKey("styled_custom_200x300").diskCacheKey("styled_disk")
        .placeholderMemoryCacheKey("styled_placeholder")
        .memoryCacheKeyExtras(buildMap {
            put("other", "keep")
            if (fallback != null) put(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY, fallback)
        }).build()

    private class Chain(
        override val request: ImageRequest,
        private val attempts: MutableList<ImageRequest>,
        private val succeeds: Boolean = false
    ) : Interceptor.Chain {
        override val size: Size = Size.ORIGINAL
        override fun withRequest(request: ImageRequest): Interceptor.Chain = Chain(request, attempts, succeeds)
        override fun withSize(size: Size): Interceptor.Chain = this
        override suspend fun proceed(): ImageResult {
            attempts += request
            return if (succeeds || request.data.toString().endsWith("raw.jpg")) {
                SuccessResult(mockk<Image>(), request, DataSource.NETWORK)
            } else ErrorResult(null, request, IOException("temporary provider error"))
        }
    }

    @Test fun `raw fallback never inherits styled memory disk or placeholder keys`() = runTest {
        val attempts = mutableListOf<ImageRequest>()
        val result = CustomPosterFallbackInterceptor().intercept(Chain(request(), attempts))
        assertTrue(result is SuccessResult)
        assertEquals(2, attempts.size)
        val fallback = attempts.last()
        assertEquals(raw, fallback.data)
        assertNull(fallback.memoryCacheKey)
        assertNull(fallback.diskCacheKey)
        assertNull(fallback.placeholderMemoryCacheKey)
        assertEquals("keep", fallback.memoryCacheKeyExtras["other"])
        assertFalse(fallback.memoryCacheKeyExtras.containsKey(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY))
        // The original styled request remains intact, so the next load can retry its provider.
        assertEquals("styled_custom_200x300", attempts.first().memoryCacheKey)
        assertEquals(styled, attempts.first().data)
    }

    @Test fun `successful styled request is not retried or replaced`() = runTest {
        val attempts = mutableListOf<ImageRequest>()
        val result = CustomPosterFallbackInterceptor().intercept(Chain(request(), attempts, succeeds = true))
        assertTrue(result is SuccessResult)
        assertEquals(1, attempts.size)
        assertEquals(styled, result.request.data)
    }

    @Test fun `missing fallback returns the provider error`() = runTest {
        val attempts = mutableListOf<ImageRequest>()
        assertTrue(CustomPosterFallbackInterceptor().intercept(Chain(request(null), attempts)) is ErrorResult)
        assertEquals(1, attempts.size)
    }

    @Test fun `same URL cannot trigger a redundant fallback request`() = runTest {
        val attempts = mutableListOf<ImageRequest>()
        assertTrue(CustomPosterFallbackInterceptor().intercept(Chain(request(styled), attempts)) is ErrorResult)
        assertEquals(1, attempts.size)
    }
}
