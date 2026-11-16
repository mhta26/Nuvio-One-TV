package com.nuvio.tv.livetv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class BoundedPlaylistReaderTest {
    @Test
    fun `valid playlist retains final URL and closes stream and connection`() {
        val connection = FakeConnection("#EXTM3U".toByteArray())
        val (text, url) = readBoundedPlaylist(connection)
        assertEquals("#EXTM3U", text)
        assertEquals(connection.url.toString(), url)
        assertTrue(connection.streamClosed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `unknown length video response is bounded and all resources are closed`() {
        val connection = FakeConnection(ByteArray(128))
        assertTrue(runCatching { readBoundedPlaylist(connection, maxBytes = 32) }.exceptionOrNull() is IOException)
        assertTrue(connection.streamClosed)
        assertTrue(connection.disconnected)
    }

    @Test
    fun `oversized declared body is rejected before it is opened`() {
        val connection = FakeConnection(ByteArray(128), declaredLength = 128)
        assertTrue(runCatching { readBoundedPlaylist(connection, maxBytes = 32) }.exceptionOrNull() is IOException)
        assertFalse(connection.streamOpened)
        assertTrue(connection.disconnected)
    }

    private class FakeConnection(private val bytes: ByteArray, private val declaredLength: Long = -1) :
        HttpURLConnection(URL("https://example.org/final.m3u8")) {
        var disconnected = false
        var streamOpened = false
        var streamClosed = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getContentLengthLong() = declaredLength
        override fun getInputStream(): java.io.InputStream {
            streamOpened = true
            return object : ByteArrayInputStream(bytes) {
                override fun close() { streamClosed = true; super.close() }
            }
        }
    }
}
