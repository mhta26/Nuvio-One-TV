package com.nuvio.tv.ui.screens.player

import android.content.Context
import androidx.media3.common.PlaybackException
import com.nuvio.tv.data.local.InternalPlayerEngine
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SoftwareVideoRecoveryTest {
    private val controller = mockk<PlayerRuntimeController>(relaxed = true)
    private val state = MutableStateFlow(PlayerUiState())
    private var url = "https://fixture.invalid/video"
    private var softwareUrl: String? = null
    private val headers = mapOf("X-Fixture" to "test")
    private val error = PlaybackException("fixture decoder failure", null, PlaybackException.ERROR_CODE_DECODING_FAILED)

    @Before fun prepare() {
        mockkStatic("com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerInitializationKt")
        mockkStatic("com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerLifecycleKt")
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "Buffering"
        every { controller.context } returns context
        every { controller._uiState } returns state
        every { controller.currentInternalPlayerEngine } returns InternalPlayerEngine.EXOPLAYER
        every { controller.currentStreamUrl } answers { url }
        every { controller.currentHeaders } returns headers
        every { controller.forceSoftwareVideoDecoderForStreamUrl } answers { softwareUrl }
        every { controller.forceSoftwareVideoDecoderForStreamUrl = any() } answers { softwareUrl = firstArg() }
        every { controller.releasePlayer(any(), any()) } just Runs
        every { controller.initializePlayer(any(), any(), any(), any(), any()) } just Runs
    }
    @After fun cleanUp() {
        unmockkStatic("com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerInitializationKt")
        unmockkStatic("com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerLifecycleKt")
    }
    @Test fun softwareRetryPreservesEngineFailoverPauseAndPosition() {
        assertTrue(controller.trySoftwareVideoDecoderFallback(error, 42_000, true, true))
        assertEquals(42_000L, state.value.pendingSeekPosition)
        verify(exactly = 1) { controller.initializePlayer(url, headers, InternalPlayerEngine.EXOPLAYER, true, true) }
        verify(exactly = 1) { controller.releasePlayer(false, false) }
    }
    @Test fun disabledOrAlreadySpentEngineFailoverStaysDisabled() {
        assertTrue(controller.trySoftwareVideoDecoderFallback(error, 0, false, false))
        verify { controller.initializePlayer(url, headers, InternalPlayerEngine.EXOPLAYER, false, false) }
    }
    @Test fun retryIsBoundedPerStreamAndDifferentStreamStartsFresh() {
        assertTrue(controller.trySoftwareVideoDecoderFallback(error, 0, false, true))
        assertFalse(controller.trySoftwareVideoDecoderFallback(error, 0, false, true))
        url = "https://fixture.invalid/second"
        assertTrue(controller.trySoftwareVideoDecoderFallback(error, 0, false, true))
        verify(exactly = 2) { controller.initializePlayer(any(), any(), any(), any(), any()) }
    }
    @Test fun nonVideoErrorsNeverRebuildOnSoftware() {
        assertFalse(controller.trySoftwareVideoDecoderFallback(
            PlaybackException("fixture network", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED), 0, false, true))
        verify(exactly = 0) { controller.initializePlayer(any(), any(), any(), any(), any()) }
    }
    @Test fun audioDecoderFailureDoesNotTriggerVideoRebuild() {
        val audioError = androidx.media3.exoplayer.ExoPlaybackException.createForRenderer(
            RuntimeException("fixture audio"), "audio", 0,
            androidx.media3.common.Format.Builder().setSampleMimeType("audio/ac3").build(),
            androidx.media3.common.C.FORMAT_HANDLED, false, PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertFalse(controller.trySoftwareVideoDecoderFallback(audioError, 0, false, true))
        verify(exactly = 0) { controller.initializePlayer(any(), any(), any(), any(), any()) }
    }
    @Test fun videoRendererFailureStillRetries() {
        val videoError = androidx.media3.exoplayer.ExoPlaybackException.createForRenderer(
            RuntimeException("fixture video"), "video", 0,
            androidx.media3.common.Format.Builder().setSampleMimeType("video/avc").build(),
            androidx.media3.common.C.FORMAT_HANDLED, false, PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertTrue(controller.trySoftwareVideoDecoderFallback(videoError, 0, false, true))
        verify(exactly = 1) { controller.initializePlayer(any(), any(), any(), any(), any()) }
    }
    @Test fun softwareRetryDisablesTunnelingWithoutChangingNormalAttempts() {
        assertTrue(videoAttemptAllowsTunneling(true, false, false))
        assertFalse(videoAttemptAllowsTunneling(true, false, true))
        assertTrue(videoAttemptAllowsTunneling(true, false, false))
        assertFalse(videoAttemptAllowsTunneling(false, false, false))
        assertFalse(videoAttemptAllowsTunneling(true, true, false))
    }
}
