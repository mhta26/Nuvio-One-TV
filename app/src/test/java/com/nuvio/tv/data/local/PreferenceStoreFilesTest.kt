package com.nuvio.tv.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceStoreFilesTest {
    @Test
    fun `all atomic shadow sidecars resolve to their primary store`() {
        for (suffix in listOf("", ".bak", ".bak.bak", ".bak.new", ".tmp")) {
            assertEquals("library_p2", preferenceStoreName("library_p2.preferences_pb$suffix"))
        }
    }

    @Test
    fun `profile cleanup includes backups and pending atomic writes`() {
        for (suffix in listOf("", ".bak", ".bak.bak", ".bak.new", ".tmp")) {
            assertTrue(preferenceFileBelongsToProfile("library_p2.preferences_pb$suffix", 2))
        }
        assertFalse(preferenceFileBelongsToProfile("library_p20.preferences_pb.bak", 2))
        assertFalse(preferenceFileBelongsToProfile("library.preferences_pb", 2))
        assertFalse(preferenceFileBelongsToProfile("library.preferences_pb", 1))
    }

    @Test
    fun `unrelated files are never treated as preference backups`() {
        for (file in listOf("poster_p2.jpg", ".preferences_pb", "library_p2.preferences_pb.export", "library_p2.json")) {
            assertNull(preferenceStoreName(file))
            assertFalse(preferenceFileBelongsToProfile(file, 2))
        }
    }
}
