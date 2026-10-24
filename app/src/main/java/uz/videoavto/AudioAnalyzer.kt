package uz.videoavto

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Ovoz faylini PCM ga aylantirib, har 20 ms uchun ovoz kuchini hisoblaydi,
 * so'ng pauzalarni (jimlik oralig'ini) topadi.
 */
object AudioAnalyzer {
    private const val FRAME_MS = 20
    private const val MIN_PAUSE_MS = 280

    suspend fun analyze(context: Context, uri: Uri): AudioAnalysis {
        val energies = decodeEnergies(context, uri)
        return detect(energies)
    }

    private suspend fun decodeEnergies(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            require(trackIndex >= 0 && format != null) { "Faylda ovoz topilmadi." }
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = intOr(format, MediaFormat.KEY_SAMPLE_RATE, 44100)
            var channels = intOr(format, MediaFormat.KEY_CHANNEL_COUNT, 1)
            var pcmFloat = false
            var samplesPerFrame = max(1, sampleRate * channels * FRAME_MS / 1000)

            val energies = ArrayList<Float>()
            var acc = 0.0
            var cnt = 0

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                currentCoroutineContext().ensureActive()

                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val out = decoder.getOutputBuffer(outIdx)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            out.order(ByteOrder.nativeOrder())
                            if (pcmFloat) {
                                val fb = out.asFloatBuffer()
                                while (fb.hasRemaining()) {
                                    val s = fb.get().toDouble()
                                    acc += s * s
                                    cnt++
                                    if (cnt >= samplesPerFrame) {
                                        energies.add(sqrt(acc / cnt).toFloat())
                                        acc = 0.0
                                        cnt = 0
                                    }
                                }
                            } else {
                                val sb = out.asShortBuffer()
                                while (sb.hasRemaining()) {
                                    val s = sb.get() / 32768.0
                                    acc += s * s
                                    cnt++
                                    if (cnt >= samplesPerFrame) {
                                        energies.add(sqrt(acc / cnt).toFloat())
                                        acc = 0.0
                                        cnt = 0
                                    }
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }

                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = decoder.outputFormat
                        sampleRate = intOr(nf, MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = intOr(nf, MediaFormat.KEY_CHANNEL_COUNT, channels)
                        samplesPerFrame = max(1, sampleRate * channels * FRAME_MS / 1000)
                        pcmFloat = nf.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            nf.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4 // ENCODING_PCM_FLOAT
                    }
                }
            }
            if (cnt > 0) energies.add(sqrt(acc / cnt).toFloat())
            return energies.toFloatArray()
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // ignore
            }
            codec?.release()
            extractor.release()
        }
    }

    private fun intOr(f: MediaFormat, key: String, default: Int): Int =
        if (f.containsKey(key)) f.getInteger(key) else default

    private fun detect(e: FloatArray): AudioAnalysis {
        val n = e.size
        if (n == 0) return AudioAnalysis(0, emptyList(), emptyList())
        val durationMs = n.toLong() * FRAME_MS

        val sorted = e.copyOf().also { it.sort() }
        val floor = sorted[(n * 0.10).toInt().coerceIn(0, n - 1)]
        val speech = sorted[(n * 0.90).toInt().coerceIn(0, n - 1)]
        val threshold = max(floor * 2f, floor + (speech - floor) * 0.10f)

        val silent = BooleanArray(n) { e[it] < threshold }
        val minFrames = MIN_PAUSE_MS / FRAME_MS

        var voiceStart = 0L
        var voiceEnd = durationMs
        val pauses = ArrayList<Span>()

        var i = 0
        while (i < n) {
            if (!silent[i]) {
                i++
                continue
            }
            var j = i
            while (j < n && silent[j]) j++
            val sMs = i.toLong() * FRAME_MS
            val eMs = j.toLong() * FRAME_MS
            when {
                i == 0 -> voiceStart = eMs
                j == n -> voiceEnd = sMs
                j - i >= minFrames -> pauses += Span(sMs, eMs)
            }
            i = j
        }

        if (voiceEnd <= voiceStart) {
            // Hammasi jim bo'lib chiqdi — butun faylni gapirilgan deb hisoblaymiz.
            return AudioAnalysis(durationMs, emptyList(), listOf(Span(0, durationMs)))
        }

        val voiced = ArrayList<Span>()
        var cursor = voiceStart
        for (p in pauses) {
            if (p.startMs > cursor) voiced += Span(cursor, p.startMs)
            cursor = p.endMs
        }
        if (voiceEnd > cursor) voiced += Span(cursor, voiceEnd)

        return AudioAnalysis(durationMs, pauses, voiced)
    }
}
