package com.nuvio.tv.ui.screens.player

/** Applied only to separately loaded subtitles; embedded tracks never enter this path. */
internal object ExternalSubtitleFontPolicy {
    // IBM Medium outlines plus missing symbol glyphs; renamed as required by the IBM OFL.
    const val FAMILY = "Nuvio Subtitle Arabic"
    const val FILE_NAME = "NuvioSubtitleArabic-Medium.ttf"
    private val inlineFont = Regex("""\\fn[^\\}]*""", RegexOption.IGNORE_CASE)

    private val overrideBlock = Regex("""\{[^}]*}""")

    fun overrideTextFonts(text: String): String {
        if (text.contains("[Script Info]", true)) return overrideAssFonts(text)
        // Restrict edits to markup and WebVTT STYLE blocks. Spoken text may itself mention CSS.
        val fontAttribute = Regex("(?i)\\s+(?:face|(?:tts:)?fontFamily)\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)")
        val cssFamily = Regex("(?i)font-family\\s*:[^;}\\r\\n]*;?")
        val markup = Regex("(?is)<[^>]+>").replace(text) { tag ->
            val attributes = if (tag.value.startsWith("<font", true)) {
                fontAttribute.replace(tag.value, "")
            } else {
                Regex("(?i)\\s+(?:tts:)?fontFamily\\s*=\\s*(?:\"[^\"]*\"|'[^']*')").replace(tag.value, "")
            }
            cssFamily.replace(attributes, "")
        }
        return Regex("(?ms)^STYLE[ \\t]*\\r?\\n.*?(?=\\r?\\n[ \\t]*\\r?\\n|\\z)")
            .replace(markup) { block -> cssFamily.replace(block.value, "") }
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
                    overrideBlock.replace(line) { block ->
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
