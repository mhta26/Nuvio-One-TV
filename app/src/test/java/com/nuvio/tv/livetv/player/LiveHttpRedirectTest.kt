package com.nuvio.tv.livetv.player

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveHttpRedirectTest {
    @Test
    fun `redirect without Location keeps configured timeouts and headers`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302))
            val connection = LiveHttp.open(server.url("/playlist").toString(), mapOf("User-Agent" to "TV-test", "X-Token" to "provider"), 1234)
            try {
                assertEquals(302, connection.responseCode)
                assertEquals(1234, connection.readTimeout)
                assertEquals(15_000, connection.connectTimeout)
                assertEquals("provider", server.takeRequest().getHeader("X-Token"))
            } finally {
                connection.disconnect()
            }
        }
    }
}
