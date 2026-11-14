package com.metrolist.music.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.metrolist.music.eq.data.ParametricEQ
import com.metrolist.music.eq.data.ParametricEQBand
import com.metrolist.music.playback.audio.AudioLimiting
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/**
 * Custom audio processor for ExoPlayer that applies parametric EQ using biquad filters
 * Uses ParametricEQ format from AutoEQ project
 */
@UnstableApi
@SuppressWarnings("Deprecated")
class CustomEqualizerAudioProcessor : AudioProcessor {

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var isActive = false
    private var equalizerEnabled = false

    private var inputBuffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var filters: List<BiquadFilter> = emptyList()
    private var preampGain: Double = 1.0  // Linear preamp gain multiplier
    private var pendingProfile: ParametricEQ? = null

    companion object {
        private const val TAG = "CustomEqualizerAudioProcessor"
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    /**
     * Apply an EQ profile
     */
    @Synchronized
    fun applyProfile(parametricEQ: ParametricEQ) {
        if (sampleRate == 0) {
            // Audio processor not configured yet, store as pending
            Timber.tag(TAG)
                .d("Audio processor not configured yet. Storing profile as pending with ${parametricEQ.bands.size} bands")
            pendingProfile = parametricEQ
            return
        }

        // Convert preamp from dB to linear gain
        preampGain = 10.0.pow(parametricEQ.preamp / 20.0)

        createFilters(parametricEQ.bands)
        equalizerEnabled = true

        // Reset filter states to ensure clean transition
        filters.forEach { it.reset() }

        Timber.tag(TAG)
            .d("Applied EQ profile with ${filters.size} bands and ${parametricEQ.preamp} dB preamp")
    }

    /**
     * Disable the equalizer
     */
    @Synchronized
    fun disable() {
        equalizerEnabled = false
        filters = emptyList()
        preampGain = 1.0
        pendingProfile = null
        Timber.tag(TAG).d("Equalizer disabled")
    }

    /**
     * Check if equalizer is enabled
     */
    fun isEnabled(): Boolean = equalizerEnabled

    /**
     * Create biquad filters from ParametricEQ bands
     * Only creates filters for enabled bands below Nyquist frequency
     * Supports PK (peaking), LSC (low-shelf), and HSC (high-shelf) filter types
     */
    private fun createFilters(bands: List<ParametricEQBand>) {
        if (sampleRate == 0) {
            Timber.tag(TAG).w("Cannot create filters: sample rate not set")
            return
        }

        // Filter out disabled bands and frequencies above Nyquist limit
        filters = bands
            .filter { it.enabled && it.frequency < sampleRate / 2.0 }
            .map { band ->
                BiquadFilter(
                    sampleRate = sampleRate,
                    frequency = band.frequency,
                    gain = band.gain,
                    q = band.q,
                    filterType = band.filterType
                )
            }

        Timber.tag(TAG)
            .d("Created ${filters.size} biquad filters from ${bands.size} bands (PK/LSC/HSC)")
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        Timber.tag(TAG)
            .d("Configured: sampleRate=$sampleRate, channels=$channelCount, encoding=$encoding")

        // Apply pending profile if one exists
        pendingProfile?.let { profile ->
            preampGain = 10.0.pow(profile.preamp / 20.0)
            createFilters(profile.bands)
            equalizerEnabled = true
            pendingProfile = null
            Timber.tag(TAG)
                .d("Applied pending profile with ${filters.size} bands and ${profile.preamp} dB preamp")
        }

        // Support 16-bit and 32-bit float PCM mono/stereo
        if ((encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) || channelCount !in 1..2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        isActive = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!equalizerEnabled || filters.isEmpty()) {
            // Passthrough mode - directly use input as output
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return

            // Ensure output buffer is large enough
            if (outputBuffer.capacity() < remaining) {
                outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                outputBuffer.clear()
            }
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val inputSize = inputBuffer.remaining()
        if (inputSize == 0) {
            return
        }

        // Ensure we have our own output buffer (reuse if possible to avoid allocations)
        // Note: We MUST NOT use inputBuffer as outputBuffer if we modify it
        if (outputBuffer === EMPTY_BUFFER || outputBuffer === inputBuffer) {
            // Need new buffer - was empty or same as input
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else if (outputBuffer.capacity() < inputSize) {
            // Need larger buffer
            outputBuffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            // Reuse existing buffer (most common path)
            outputBuffer.clear()
        }

        // Process audio samples
        when (encoding) {
            C.ENCODING_PCM_16BIT -> processAudioBuffer16Bit(inputBuffer, outputBuffer)
            C.ENCODING_PCM_FLOAT -> processAudioBufferFloat(inputBuffer, outputBuffer)
            else -> outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
        // inputBuffer position is already updated by processAudioBuffer/put
    }

    /**
     * Process 16-bit PCM audio through all biquad filters with soft-knee peak limiting
     */
    private fun processAudioBuffer16Bit(input: ByteBuffer, output: ByteBuffer) {
        input.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = (input.remaining() / 2) / channelCount

        repeat(frameCount) {
            if (channelCount == 1) {
                var processed = input.getShort().toDouble() / 32768.0
                for (filter in filters) {
                    processed = filter.processSample(processed)
                }
                val limited = AudioLimiting.softLimit(processed * preampGain)
                val outputSample = (limited * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort()
                output.putShort(outputSample)
            } else {
                var processedLeft = input.getShort().toDouble() / 32768.0
                var processedRight = input.getShort().toDouble() / 32768.0
                for (filter in filters) {
                    val (left, right) = filter.processStereo(processedLeft, processedRight)
                    processedLeft = left
                    processedRight = right
                }
                val limitedLeft = AudioLimiting.softLimit(processedLeft * preampGain)
                val limitedRight = AudioLimiting.softLimit(processedRight * preampGain)
                output.putShort((limitedLeft * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                output.putShort((limitedRight * 32767.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
    }

    /**
     * Process 32-bit float PCM audio through all biquad filters with soft-knee peak limiting
     */
    private fun processAudioBufferFloat(input: ByteBuffer, output: ByteBuffer) {
        input.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = (input.remaining() / 4) / channelCount

        repeat(frameCount) {
            if (channelCount == 1) {
                var processed = input.getFloat().toDouble()
                for (filter in filters) {
                    processed = filter.processSample(processed)
                }
                val limited = AudioLimiting.softLimit(processed * preampGain)
                output.putFloat(limited.toFloat())
            } else {
                var processedLeft = input.getFloat().toDouble()
                var processedRight = input.getFloat().toDouble()
                for (filter in filters) {
                    val (left, right) = filter.processStereo(processedLeft, processedRight)
                    processedLeft = left
                    processedRight = right
                }
                val limitedLeft = AudioLimiting.softLimit(processedLeft * preampGain)
                val limitedRight = AudioLimiting.softLimit(processedRight * preampGain)
                output.putFloat(limitedLeft.toFloat())
                output.putFloat(limitedRight.toFloat())
            }
        }
    }

    override fun getOutput(): ByteBuffer {
        // Return output buffer ready for reading (already flipped in queueInput)
        val buffer = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return buffer
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer.remaining() == 0
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false

        // Reset filter states
        filters.forEach { it.reset() }
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        inputBuffer = EMPTY_BUFFER
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        isActive = false
        filters.forEach { it.reset() }
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }
}
