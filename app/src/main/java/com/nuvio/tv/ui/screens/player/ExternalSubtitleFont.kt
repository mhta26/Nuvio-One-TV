package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.nuvio.tv.R
import java.io.File

/** Loaded on demand at playback, with a single cached Typeface and font file. */
internal object ExternalSubtitleFont {
    @Volatile private var cached: Typeface? = null
    fun typeface(context: Context, bold: Boolean = false): Typeface {
        val base = cached ?: synchronized(this) {
            cached ?: requireNotNull(ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic_medium))
                .also { cached = it }
        }
        return if (bold) Typeface.create(base, Typeface.BOLD) else base
    }

    fun loadedTypeface(bold: Boolean): Typeface? = cached?.let {
        if (bold) Typeface.create(it, Typeface.BOLD) else it
    }

    fun bytes(context: Context): ByteArray = context.resources
        .openRawResource(R.font.ibm_plex_sans_arabic_medium).use { it.readBytes() }

    @Synchronized fun directory(context: Context): File {
        val dir = File(context.filesDir, "subtitle-fonts").also { it.mkdirs() }
        val font = File(dir, ExternalSubtitleFontPolicy.FILE_NAME)
        if (!font.isFile || font.length() == 0L) {
            val temp = File(dir, "font.tmp")
            temp.writeBytes(bytes(context))
            check(temp.renameTo(font)) { "Couldn't prepare subtitle font" }
        }
        return dir
    }
}
