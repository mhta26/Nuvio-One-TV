package com.nuvio.tv.core.image

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.ErrorResult
import coil3.request.ImageRequest

/**
 * Coil interceptor that detects failed custom poster loads and retries with the
 * original poster URL stored in [ImageRequest.memoryCacheKeyExtra] under the
 * [FALLBACK_URL_KEY] key.
 *
 * This runs globally in the ImageLoader pipeline, so every composable that loads
 * posters (ModernHomeRows, ContentCard, GridContentCard, ContinueWatching, etc.)
 * gets automatic fallback without per-composable code.
 */
class CustomPosterFallbackInterceptor : Interceptor {

    companion object {
        const val FALLBACK_URL_KEY = "custom_poster_fallback_url"
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val started = System.nanoTime()
        val result = chain.proceed()
        val fallbackUrl = chain.request.memoryCacheKeyExtras[FALLBACK_URL_KEY]
        if (!fallbackUrl.isNullOrBlank()) {
            val url = chain.request.data.toString()
            if (result is ErrorResult) FailedPosterRegistry.shared.failed(url)
            else FailedPosterRegistry.shared.succeeded(url)
            com.nuvio.tv.core.diagnostics.PerformanceDiagnostics.operation(
                com.nuvio.tv.core.diagnostics.PerformanceOperation.POSTER, started, result !is ErrorResult
            )
        }

        if (result is ErrorResult) {
            if (!fallbackUrl.isNullOrBlank() && fallbackUrl != chain.request.data.toString()) {
                val fallbackRequest = chain.request.newBuilder()
                    .data(fallbackUrl)
                    // A temporary raw fallback must never be cached under the styled URL's key.
                    .memoryCacheKey(null as String?)
                    .diskCacheKey(null)
                    .placeholderMemoryCacheKey(null as String?)
                    .memoryCacheKeyExtras(
                        chain.request.memoryCacheKeyExtras - FALLBACK_URL_KEY
                    )
                    .build()
                val fallbackStarted = System.nanoTime()
                return chain.withRequest(fallbackRequest).proceed().also {
                    com.nuvio.tv.core.diagnostics.PerformanceDiagnostics.operation(
                        com.nuvio.tv.core.diagnostics.PerformanceOperation.POSTER_FALLBACK, fallbackStarted, it !is ErrorResult
                    )
                }
            }
        }

        return result
    }
}
