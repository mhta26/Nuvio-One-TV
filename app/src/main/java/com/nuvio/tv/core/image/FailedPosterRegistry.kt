package com.nuvio.tv.core.image

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session-only retry list. Never persisted or included in exported diagnostics. */
class FailedPosterRegistry(private val capacity: Int = 128) {
    private val entries = LinkedHashMap<String, Long>()
    private var generation = 0L
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()
    init { require(capacity > 0) }
    @Synchronized fun failed(url: String) {
        entries.remove(url)
        entries[url] = ++generation
        while (entries.size > capacity) entries.remove(entries.keys.first())
        _count.value = entries.size
    }
    @Synchronized fun succeeded(url: String) { entries.remove(url); _count.value = entries.size }
    @Synchronized fun resolved(url: String, generation: Long) {
        if (entries[url] == generation) succeeded(url)
    }
    @Synchronized fun batch(limit: Int = 10): List<Pair<String, Long>> = entries.entries.take(limit.coerceAtLeast(0)).map { it.key to it.value }
    companion object { val shared = FailedPosterRegistry() }
}
