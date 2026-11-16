package com.nuvio.tv.data.local

private val preferenceSidecars = setOf("", ".bak", ".bak.bak", ".bak.new", ".tmp")

/** Primary preference file and only the known DataStore/AtomicFile sidecars. */
internal fun preferenceStoreName(fileName: String): String? {
    val marker = ".preferences_pb"
    val end = fileName.indexOf(marker)
    if (end <= 0) return null
    val sidecar = fileName.substring(end + marker.length)
    if (sidecar !in preferenceSidecars) return null
    return fileName.substring(0, end)
}

internal fun preferenceFileBelongsToProfile(fileName: String, profileId: Int): Boolean =
    profileId > 1 && preferenceStoreName(fileName)?.endsWith("_p$profileId") == true
