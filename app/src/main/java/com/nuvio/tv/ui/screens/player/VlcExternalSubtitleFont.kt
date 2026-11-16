@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser

/** VLC has no per-track font setter. Give only external text tracks a self-contained ASS
 * font attachment instead of changing the VLC instance's default (which affects embedded tracks).
 * Existing ASS positioning, effects, timings and styles stay intact, apart from font names.
 */
internal object VlcExternalSubtitleFont {
    fun prepare(context: Context, text: String, url: String): String? {
        val styled = if (text.contains("[Script Info]", true)) {
            ExternalSubtitleFontPolicy.overrideAssFonts(text)
        } else {
            val format = Format.Builder().setSampleMimeType(PlayerSubtitleUtils.mimeTypeFromUrl(url)).build()
            val factory = DefaultSubtitleParserFactory()
            if (!factory.supportsFormat(format)) return null
            val events = StringBuilder()
            val bytes = text.toByteArray(Charsets.UTF_8)
            factory.create(format).parse(bytes, 0, bytes.size, SubtitleParser.OutputOptions.allCues()) { timed ->
                if (timed.durationUs <= 0 || timed.durationUs == C.TIME_UNSET) return@parse
                for (cue in timed.cues) {
                    val value = cue.text ?: continue
                    events.append("Dialogue: 0,").append(timestamp(timed.startTimeUs))
                        .append(',').append(timestamp(timed.startTimeUs + timed.durationUs))
                        .append(",Default,,0,0,0,,").append(position(cue))
                        .append(styledText(value)).append('\n')
                }
            }
            if (events.isEmpty()) return null
            """[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080
WrapStyle: 0
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,${ExternalSubtitleFontPolicy.FAMILY},48,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,2,50,50,50,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
""" + events
        }
        // Font data must precede Events so VLC passes it in the ASS codec header.
        val event = Regex("(?im)^\\[Events]\\s*$").find(styled) ?: return null
        return styled.substring(0, event.range.first) +
            ExternalSubtitleFontPolicy.fontAttachment(ExternalSubtitleFont.bytes(context)) +
            styled.substring(event.range.first)
    }

    private fun timestamp(us: Long): String {
        val cs = (us / 10_000).coerceAtLeast(0)
        return "${cs / 360_000}:${(cs / 6_000 % 60).toString().padStart(2, '0')}:${(cs / 100 % 60).toString().padStart(2, '0')}.${(cs % 100).toString().padStart(2, '0')}"
    }

    private fun position(cue: Cue): String {
        val x = if (cue.position != Cue.DIMEN_UNSET) cue.position.coerceIn(0f, 1f) else 0.5f
        val y = if (cue.line != Cue.DIMEN_UNSET && cue.lineType == Cue.LINE_TYPE_FRACTION) cue.line.coerceIn(0f, 1f) else 0.95f
        val horizontal = when (cue.positionAnchor) { Cue.ANCHOR_TYPE_START -> 1; Cue.ANCHOR_TYPE_END -> 3; else -> 2 }
        val vertical = when (cue.lineAnchor) { Cue.ANCHOR_TYPE_START -> 6; Cue.ANCHOR_TYPE_MIDDLE -> 3; else -> 0 }
        return "{\\an${horizontal + vertical}\\pos(${(x * 1920).toInt()},${(y * 1080).toInt()})}"
    }

    /** Preserve bold/italic/underline and color spans while overriding the font alone. */
    private fun styledText(text: CharSequence): String = buildString {
        val spanned = text as? Spanned
        var previous = ""
        var i = 0
        while (i < text.length) {
            val next = spanned?.nextSpanTransition(i, text.length, Any::class.java) ?: text.length
            val spans = spanned?.getSpans(i, i + 1, Any::class.java).orEmpty()
            val flags = spans.filterIsInstance<StyleSpan>().fold(0) { a, s -> a or s.style }
            val color = spans.filterIsInstance<ForegroundColorSpan>().lastOrNull()?.foregroundColor ?: Color.WHITE
            val style = "\\b${if (flags and Typeface.BOLD != 0) 1 else 0}\\i${if (flags and Typeface.ITALIC != 0) 1 else 0}\\u${if (spans.any { it is UnderlineSpan }) 1 else 0}\\c&H${"%02X%02X%02X".format(Color.blue(color), Color.green(color), Color.red(color))}&"
            if (style != previous) { append('{').append(style).append('}'); previous = style }
            while (i < next) {
                when (val c = text[i++]) {
                    '\n' -> append("\\N")
                    '{' -> append("\\{")
                    '}' -> append("\\}")
                    '\\' -> append("\\\\")
                    else -> append(c)
                }
            }
        }
    }
}
