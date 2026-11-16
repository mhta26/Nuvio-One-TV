package com.nuvio.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/** Retains row focus identity and honors explicit invalidation of the owner cache. */
@Composable
internal fun <K> rememberCachedFocusRequester(
    cache: MutableMap<K, FocusRequester>,
    key: K
): FocusRequester {
    val cached = cache[key]
    return remember(cache, key, cached) {
        cached ?: FocusRequester().also { cache[key] = it }
    }
}
