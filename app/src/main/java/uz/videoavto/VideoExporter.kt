package uz.videoavto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Rasmlar + ovoz + subtitrdan MP4 yig'adi (Media3 Transformer).
 * Faqat asosiy (Main) oqimdan chaqiring.
 */
class VideoExporter(private val context: Context) {

    suspend fun export(
        audioUri: Uri,
        scenes: List<Scene>,
        cues: List<Cue>,
        settings: RenderSettings,
        output: File,
        onProgress: (Int) -> Unit,
    ) = coroutineScope {
        val composition = buildComposition(audioUri, scenes, cues, settings)

        val holder = ProgressHolder()
        var transformerRef: Transformer? = null

        val poller = launch {
            while (isActive) {
                delay(500)
                val t = transformerRef ?: continue
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
            }
        }

        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(settings.quality.bitrate).build()
                    )
                    .build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    RuntimeException(
                                        "${exportException.errorCodeName}: ${exportException.message}",
                                        exportException,
                                    )
                                )
                            }
                        }
                    })
                    .build()

                transformerRef = transformer
                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }
                transformer.start(composition, output.absolutePath)
            }
        } finally {
            poller.cancel()
        }
    }

    private fun buildComposition(
        audioUri: Uri,
        scenes: List<Scene>,
        cues: List<Cue>,
        settings: RenderSettings,
    ): Composition {
        val w = settings.width
        val h = settings.height

        val videoItems = scenes.map { sc ->
            val mediaItem = MediaItem.Builder()
                .setUri(sc.uri)
                .setImageDurationMs(sc.durationMs)
                .build()

            val effects = ArrayList<Effect>()
            effects += Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            if (settings.effects) {
                effects += kenBurns(sc.durationMs * 1000L, sc.zoomIn, sc.panDir)
            }

            EditedMediaItem.Builder(mediaItem)
                .setFrameRate(30)
                .setEffects(Effects(emptyList(), effects))
                .build()
        }

        val videoSequence = EditedMediaItemSequence(videoItems)
        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(audioUri)).build()
        val audioSequence = EditedMediaItemSequence(listOf(audioItem))

        val compositionEffects =
            if (settings.subtitles && cues.isNotEmpty()) {
                val overlay = SubtitleOverlay(cues, w, h, settings.orientation == Orientation.PORTRAIT)
                Effects(emptyList(), listOf<Effect>(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay))))
            } else {
                Effects.EMPTY
            }

        return Composition.Builder(listOf(videoSequence, audioSequence))
            .setEffects(compositionEffects)
            .build()
    }

    /** Sekin yaqinlashish/uzoqlashish va yengil siljish (Ken Burns) effekti. */
    private fun kenBurns(durationUs: Long, zoomIn: Boolean, panDir: Int): MatrixTransformation =
        MatrixTransformation { presentationTimeUs ->
            val t = (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            val maxZoom = 1.18f
            val scale = if (zoomIn) 1f + (maxZoom - 1f) * eased else maxZoom - (maxZoom - 1f) * eased
            val m = Matrix()
            m.postScale(scale, scale)
            // Kadr chetlari ko'rinib qolmasligi uchun siljish (scale - 1) dan oshmaydi.
            m.postTranslate(panDir * (scale - 1f) * 0.7f, 0f)
            m
        }
}

/**
 * Butun kadr o'lchamidagi shaffof rasm ustiga subtitr chizadi.
 * Kadr o'lchamidagi bitmap ishlatilgani uchun joylashuv aniq bo'ladi.
 */
class SubtitleOverlay(
    private val cues: List<Cue>,
    private val width: Int,
    private val height: Int,
    private val portrait: Boolean,
) : BitmapOverlay() {

    private val empty: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private var lastCue: Cue? = null
    private var lastBitmap: Bitmap = empty

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val ms = presentationTimeUs / 1000
        val cue = cues.firstOrNull { ms >= it.startMs && ms < it.endMs } ?: return empty
        if (cue !== lastCue) {
            lastBitmap = render(cue.text)
            lastCue = cue
        }
        return lastBitmap
    }

    private fun render(text: String): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val textSize = width * if (portrait) 0.075f else 0.045f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            strokeJoin = Paint.Join.ROUND
        }
        val maxWidth = (width * 0.86f).toInt()
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, maxWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        val bottom = height * if (portrait) 0.78f else 0.92f
        canvas.save()
        canvas.translate((width - maxWidth) / 2f, bottom - layout.height)

        // Qora kontur
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = textSize * 0.2f
        paint.color = Color.BLACK
        layout.draw(canvas)

        // Oq matn
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        layout.draw(canvas)

        canvas.restore()
        return bmp
    }
}
