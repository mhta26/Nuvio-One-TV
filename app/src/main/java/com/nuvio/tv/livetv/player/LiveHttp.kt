package com.nuvio.tv.livetv.player

import java.net.HttpURLConnection
import java.net.URL
import com.nuvio.tv.livetv.stalker.StalkerMediaPolicy

/**
 * Opens a provider link for recording, following redirects all the way, including between
 * http and https and to other servers (Android's built-in following stops at those, so a
 * provider that sends you on to its stream server answered "HTTP 302" and nothing was recorded).
 */
object LiveHttp {
    /** The connection after the last redirect (its [HttpURLConnection.getURL] is where it ended up). */
    fun open(url: String, headers: Map<String, String>, readTimeoutMs: Int = 20_000, policy: StalkerMediaPolicy? = null): HttpURLConnection {
        var current = url
        var secureContext = URL(url).protocol.equals("https", true)
        var hops = 0
        while (true) {
            val requestHeaders = if (policy == null) headers else StalkerMediaPolicy.withoutPortalHeaders(headers) + policy.headersFor(current, secureContext)
            val c = URL(current).openConnection() as HttpURLConnection
            policy?.observe(c)
            c.connectTimeout = 15_000
            c.readTimeout = readTimeoutMs
            c.instanceFollowRedirects = false
            requestHeaders.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (requestHeaders.keys.none { it.equals("User-Agent", true) }) {
                c.setRequestProperty("User-Agent", com.nuvio.tv.livetv.data.LiveTvRepository.DEFAULT_UA)
            }
            val code = try {
                c.responseCode
            } catch (error: Exception) {
                c.disconnect()
                throw error
            }
            if (code in setOf(301, 302, 303, 307, 308) && hops < 8) {
                val location = c.getHeaderField("Location")
                // Keep the configured connection when the server omits Location.
                if (location.isNullOrBlank()) return c
                c.disconnect()
                current = runCatching { URL(URL(current), location).toString() }.getOrDefault(location)
                if (URL(current).protocol.equals("https", true)) secureContext = true
                hops++
                continue
            }
            return c
        }
    }

    /** The text at [url], and where it was really served from (for links relative to it). */
    fun fetch(url: String, headers: Map<String, String>, policy: StalkerMediaPolicy? = null): Pair<String, String> {
        val c = open(url, headers, policy = policy)
        return readBoundedPlaylist(c)
    }
}
