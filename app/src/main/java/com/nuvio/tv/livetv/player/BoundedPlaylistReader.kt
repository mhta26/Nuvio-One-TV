package com.nuvio.tv.livetv.player

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection

internal const val MAX_HLS_TEXT_BYTES = 4 * 1024 * 1024

/** Providers sometimes return a continuous video stream at a playlist URL. */
internal fun readBoundedPlaylist(connection: HttpURLConnection, maxBytes: Int = MAX_HLS_TEXT_BYTES): Pair<String, String> {
    require(maxBytes > 0)
    try {
        if (connection.contentLengthLong > maxBytes) throw IOException("Playlist response exceeds the size limit")
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Playlist read interrupted")
                val read = input.read(buffer)
                if (read < 0) break
                if (read > maxBytes - output.size()) throw IOException("Playlist response exceeds the size limit")
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name()) to connection.url.toString()
    } finally {
        connection.disconnect()
    }
}
