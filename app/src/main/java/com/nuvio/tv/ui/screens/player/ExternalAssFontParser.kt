@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.nuvio.tv.ui.screens.player

import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser

/** Only wraps libass's full-file external parser, never its embedded sample parser. */
internal class ExternalAssFontParser(
    private val delegate: SubtitleParser,
    private val registerFont: () -> Unit
) : SubtitleParser {
    override fun parse(data: ByteArray, offset: Int, length: Int,
        outputOptions: SubtitleParser.OutputOptions, output: Consumer<CuesWithTiming>) {
        registerFont()
        val text = com.nuvio.tv.core.player.SubtitleCharsetDetector.decode(data.copyOfRange(offset, offset + length))
        val styled = ExternalSubtitleFontPolicy.overrideAssFonts(text).toByteArray(Charsets.UTF_8)
        delegate.parse(styled, 0, styled.size, outputOptions, output)
    }
    override fun reset() = delegate.reset()
    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior
}
