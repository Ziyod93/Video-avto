package uz.videoavto

import android.net.Uri

/** Vaqt oralig'i (millisekundlarda). */
data class Span(val startMs: Long, val endMs: Long) {
    val length: Long get() = endMs - startMs
    val mid: Long get() = (startMs + endMs) / 2
}

/** Ovoz tahlili natijasi: umumiy davomiylik, pauzalar va gapirilgan qismlar. */
data class AudioAnalysis(
    val durationMs: Long,
    val pauses: List<Span>,
    val voiced: List<Span>,
)

/** Bitta rasm ko'rinadigan sahna. */
data class Scene(
    val uri: Uri,
    val startMs: Long,
    val endMs: Long,
    val zoomIn: Boolean,
    val panDir: Int,
) {
    val durationMs: Long get() = endMs - startMs
}

/** Bitta subtitr qatori. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

enum class Orientation(val label: String) {
    LANDSCAPE("16:9 YouTube"),
    PORTRAIT("9:16 Shorts"),
}

enum class Quality(val label: String, val shortSide: Int, val bitrate: Int) {
    HD("720p", 720, 6_000_000),
    FULL_HD("1080p", 1080, 10_000_000),
}

data class RenderSettings(
    val orientation: Orientation,
    val quality: Quality,
    val subtitles: Boolean,
    val effects: Boolean,
) {
    val width: Int
        get() = if (orientation == Orientation.LANDSCAPE) quality.shortSide * 16 / 9 else quality.shortSide
    val height: Int
        get() = if (orientation == Orientation.LANDSCAPE) quality.shortSide else quality.shortSide * 16 / 9
}
