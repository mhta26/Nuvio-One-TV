package com.nuvio.tv.livetv.branding

import com.nuvio.tv.R

/**
 * Nuvio One branding. The fork's own logo images sit next to Nuvio's (they don't replace
 * them), so Nuvio's artwork stays untouched and future Nuvio updates merge cleanly.
 */
object LiveTvBranding {
    const val APP_NAME = "Nuvio One TV"
    const val TAGLINE = "Your world. One screen."

    /** The Nuvio One version of a theme's Nuvio wordmark. */
    fun iptvWordmark(nuvioWordmark: Int): Int = R.drawable.livetv_wordmark_iptv
}
