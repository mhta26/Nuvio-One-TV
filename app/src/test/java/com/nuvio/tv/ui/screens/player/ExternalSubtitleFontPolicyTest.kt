package com.nuvio.tv.ui.screens.player

import org.junit.Assert.*
import org.junit.Test

class ExternalSubtitleFontPolicyTest {
    @Test fun `ASS changes font fields and inline overrides while preserving events and effects`() {
        val original = """[Script Info]
PlayResX: 1920
[V4+ Styles]
Format: Name, Fontsize, Fontname, Bold
Style: Default,48,Arial,-1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\pos(10,20)\fnArial\k20\c&HFF0000&}مرحبا, world
"""
        val result = ExternalSubtitleFontPolicy.overrideAssFonts(original)
        assertTrue(result.contains("Style: Default,48,${ExternalSubtitleFontPolicy.FAMILY},-1"))
        assertTrue(result.contains("{\\pos(10,20)\\fn${ExternalSubtitleFontPolicy.FAMILY}\\k20\\c&HFF0000&}مرحبا, world"))
        assertTrue(result.contains("Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,"))
        assertTrue(result.contains("PlayResX: 1920"))
    }

    @Test fun `spoken text resembling a font command is untouched`() {
        val line = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Text \\fnArial, example"
        assertEquals(line, ExternalSubtitleFontPolicy.overrideAssFonts(line))
    }

    @Test fun `external text retains emphasis colors timings and positions`() {
        val original = "1\n00:00:01,000 --> 00:00:02,000\n<font color=\"red\" face=\"Arial\"><b>مرحبا</b></font>"
        assertEquals(original.replace(" face=\"Arial\"", ""), ExternalSubtitleFontPolicy.overrideTextFonts(original))
        val vtt = "WEBVTT\n\nSTYLE\n::cue { font-family: Arial; color: red; }\n\n00:01.000 --> 00:02.000 line:20%\nHello"
        assertEquals(vtt.replace("font-family: Arial;", ""), ExternalSubtitleFontPolicy.overrideTextFonts(vtt))
        val ttml = "<span tts:fontFamily='Arial' tts:color='red'>مرحبا</span>"
        assertEquals("<span tts:color='red'>مرحبا</span>", ExternalSubtitleFontPolicy.overrideTextFonts(ttml))
    }

    @Test fun `font attachment round trips every byte and every padding length`() {
        for (size in 1..260) {
            val bytes = ByteArray(size) { it.toByte() }
            val attachment = ExternalSubtitleFontPolicy.fontAttachment(bytes)
            val lines = attachment.lines().drop(2).filter { it.isNotEmpty() }
            assertTrue(lines.all { it.length <= 80 && it.all { c -> c.code in 33..96 } })
            val encoded = lines.joinToString("")
            val decoded = ArrayList<Byte>()
            var accumulator = 0
            var bits = 0
            for (char in encoded) {
                accumulator = (accumulator shl 6) or (char.code - 33)
                bits += 6
                if (bits >= 8) { bits -= 8; decoded += (accumulator shr bits).toByte() }
            }
            assertArrayEquals("size=$size", bytes, decoded.toByteArray())
        }
    }

    @Test fun `unrelated subtitle text is unchanged`() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nمرحبا بالعالم\nHello world!"
        assertEquals(text, ExternalSubtitleFontPolicy.overrideTextFonts(text))
    }
}
