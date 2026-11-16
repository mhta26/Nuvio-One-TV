package com.nuvio.tv.ui.screens.player

/** Applied only to separately loaded subtitles; embedded tracks never enter this path. */
internal object ExternalSubtitleFontPolicy {
    // The static Medium TTF's legacy family name (name table ID 1), used by libass.
    const val FAMILY = "IBM Plex Sans Arabic Medm"
    const val FILE_NAME = "IBMPlexSansArabic-Medium.ttf"
    private val inlineFont = Regex("""\\fn[^\\}]*""", RegexOption.IGNORE_CASE)

    fun overrideTextFonts(text: String): String {
        if (text.contains("[Script Info]", true)) return overrideAssFonts(text)
        // Retain colors, emphasis and cue positions; remove only competing font-family hints.
        return Regex("(?is)<font\\b[^>]*>").replace(text) { tag ->
            Regex("(?i)\\s+face\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)").replace(tag.value, "")
        }.replace(Regex("(?i)font-family\\s*:[^;}\\r\\n]*;?"), "")
            .replace(Regex("(?i)\\s+(?:tts:)?fontFamily\\s*=\\s*(?:\"[^\"]*\"|'[^']*')"), "")
    }

    fun overrideAssFonts(text: String): String {
        var inStyles = false
        var fontIndex = 1
        return text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[") -> {
                    inStyles = trimmed.equals("[V4+ Styles]", true) || trimmed.equals("[V4 Styles]", true)
                    fontIndex = 1
                    line
                }
                inStyles && trimmed.startsWith("Format:", true) -> {
                    fontIndex = trimmed.substringAfter(':').split(',').indexOfFirst { it.trim().equals("Fontname", true) }
                    line
                }
                inStyles && trimmed.startsWith("Style:", true) && fontIndex >= 0 -> {
                    val fields = trimmed.substringAfter(':').split(',').toMutableList()
                    if (fontIndex < fields.size) fields[fontIndex] = FAMILY
                    line.substringBefore(':') + ":" + fields.joinToString(",")
                }
                trimmed.startsWith("Dialogue:", true) -> {
                    // Font overrides only occur inside ASS override blocks, never in spoken text.
                    Regex("""\{[^}]*}""").replace(line) { block ->
                        inlineFont.replace(block.value) { "\\fn$FAMILY" }
                    }
                }
                else -> line
            }
        }
    }

    /** ASS embedded-font encoding: 6-bit groups + 33, no padding, at most 80 chars per line. */
    fun fontAttachment(bytes: ByteArray): String = buildString {
        append("[Fonts]\nfontname: ").append(FILE_NAME).append('\n')
        var column = 0
        fun emit(value: Int) {
            append((value + 33).toChar())
            if (++column == 80) { append('\n'); column = 0 }
        }
        var i = 0
        while (i < bytes.size) {
            val a = bytes[i++].toInt() and 255
            val b = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
            val c = if (i < bytes.size) bytes[i++].toInt() and 255 else -1
            emit(a shr 2)
            emit(((a and 3) shl 4) or (if (b >= 0) b shr 4 else 0))
            if (b >= 0) emit(((b and 15) shl 2) or (if (c >= 0) c shr 6 else 0))
            if (c >= 0) emit(c and 63)
        }
        if (column != 0) append('\n')
        append('\n')
    }
}
