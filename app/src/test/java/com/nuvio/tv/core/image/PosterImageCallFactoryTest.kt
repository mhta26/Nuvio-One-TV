package com.nuvio.tv.core.image

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class PosterImageCallFactoryTest {
    @Test fun `only poster generator hosts get longer whole call budget`() {
        val standard = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
        val factory = PosterImageCallFactory(standard)
        for (host in listOf("postersplus.cc", "cdn.postersplus.cc")) {
            val call = factory.newCall(Request.Builder().url("https://$host/poster").build())
            assertEquals(TimeUnit.SECONDS.toNanos(30), call.timeout().timeoutNanos())
        }
        for (host in listOf("image.tmdb.org", "postersplus.cc.evil.test", "fakepostersplus.cc")) {
            val call = factory.newCall(Request.Builder().url("https://$host/poster").build())
            assertEquals(TimeUnit.SECONDS.toNanos(8), call.timeout().timeoutNanos())
        }
        assertEquals(8_000, standard.callTimeoutMillis)
    }

    @Test fun `generated images can finish after static image timeout without changing standard client`() {
        MockWebServer().use { server ->
            val standard = OkHttpClient.Builder().readTimeout(20, TimeUnit.MILLISECONDS)
                .callTimeout(60, TimeUnit.MILLISECONDS).build()
            val factory = PosterImageCallFactory(standard) { it == server.hostName }
            server.enqueue(MockResponse().setBody("styled").setBodyDelay(150, TimeUnit.MILLISECONDS))
            factory.newCall(Request.Builder().url(server.url("/poster")).build()).execute().use {
                assertEquals("styled", it.body!!.string())
            }
            assertEquals("Nuvio One TV", server.takeRequest().getHeader("User-Agent"))
            assertEquals(20, standard.readTimeoutMillis)
            assertEquals(60, standard.callTimeoutMillis)
        }
    }

    @Test fun `explicit user agent is preserved`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("styled"))
            val factory = PosterImageCallFactory(OkHttpClient()) { true }
            factory.newCall(Request.Builder().url(server.url("/poster")).header("User-Agent", "Custom TV").build())
                .execute().close()
            assertEquals("Custom TV", server.takeRequest().getHeader("User-Agent"))
        }
    }
}
