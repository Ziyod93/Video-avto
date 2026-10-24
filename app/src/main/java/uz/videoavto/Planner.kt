package uz.videoavto

import android.net.Uri
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Ovoz tahliliga qarab rasmlarni sahnalarga bo'ladi va subtitr vaqtlarini hisoblaydi.
 */
object Planner {

    private val PAN_PATTERN = intArrayOf(-1, 1, 0, 1, -1, 0)
    private val SENTENCE_SPLIT = Regex("(?<=[.!?…])\\s+|\\n+")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Rasmlar ketma-ketligini saqlagan holda, almashish joylarini ovozdagi pauzalarga to'g'rilaydi.
     * Pauza topilmasa, vaqt teng bo'linadi.
     */
    fun planScenes(uris: List<Uri>, analysis: AudioAnalysis, tailMs: Long = 600L): List<Scene> {
        require(uris.isNotEmpty()) { "Rasm tanlanmagan." }
        val total = analysis.durationMs + tailMs
        val n = uris.size
        val sceneLen = total.toDouble() / n
        val minLen = min(1200.0, sceneLen * 0.5).toLong()

        val bounds = ArrayList<Long>()
        bounds += 0L
        var prev = 0L
        for (i in 1 until n) {
            val ideal = total * i / n
            val half = (sceneLen / 2).toLong()
            val lo = max(prev + minLen, ideal - half)
            val hi = min(total - (n - i) * minLen, ideal + half)

            var cut = ideal
            if (lo <= hi) {
                val best = analysis.pauses
                    .filter { it.mid in lo..hi }
                    .maxByOrNull { p -> p.length * (1.2 - abs(p.mid - ideal) / (sceneLen / 2)) }
                cut = best?.mid ?: ideal.coerceIn(lo, hi)
            }
            cut = max(cut, prev + 1)
            bounds += cut
            prev = cut
        }
        bounds += total

        // Himoya: agar biror sahna manfiy/nol bo'lib qolsa, teng bo'lamiz.
        val broken = (0 until n).any { bounds[it + 1] - bounds[it] <= 0 }
        if (broken) {
            for (i in 0..n) bounds[i] = total * i / n
        }

        return List(n) { i ->
            Scene(
                uri = uris[i],
                startMs = bounds[i],
                endMs = bounds[i + 1],
                zoomIn = i % 2 == 0,
                panDir = PAN_PATTERN[i % PAN_PATTERN.size],
            )
        }
    }

    /**
     * Matnni qisqa bo'laklarga ajratib, har bir bo'lakni gapirilgan vaqtga mutanosib joylaydi.
     * (Bu taxminiy moslashtirish: har bir so'z uzunligiga qarab vaqt taqsimlanadi.)
     */
    fun buildCues(script: String, analysis: AudioAnalysis, maxWords: Int): List<Cue> {
        if (analysis.voiced.isEmpty()) return emptyList()

        val chunks = ArrayList<Pair<String, Int>>() // matn, og'irlik
        for (sentence in script.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }) {
            val words = sentence.split(WHITESPACE).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            val parts = max(1, ceil(words.size / maxWords.toDouble()).toInt())
            val per = ceil(words.size / parts.toDouble()).toInt()
            for (group in words.chunked(per)) {
                var weight = group.sumOf { it.length + 1 }
                weight += when (group.last().last()) {
                    '.', '!', '?', '…' -> 8
                    ',', ';', ':', '-', '—' -> 3
                    else -> 0
                }
                chunks += group.joinToString(" ") to weight
            }
        }
        if (chunks.isEmpty()) return emptyList()

        val totalWeight = chunks.sumOf { it.second }.toDouble()
        val voicedTotal = analysis.voiced.sumOf { it.length }

        var cum = 0
        val raw = chunks.map { (text, w) ->
            val a = (cum / totalWeight * voicedTotal).toLong()
            cum += w
            val b = (cum / totalWeight * voicedTotal).toLong()
            Triple(text, voicedToReal(analysis.voiced, a, true), voicedToReal(analysis.voiced, b, false))
        }

        val limit = analysis.durationMs + 400
        return raw.mapIndexed { idx, (text, start, end) ->
            val nextStart = raw.getOrNull(idx + 1)?.second
            var e = if (nextStart != null && nextStart - end < 700) nextStart else end + 250
            e = min(e, limit)
            if (e - start < 350) e = start + 350
            Cue(start, e, text)
        }
    }

    private fun voicedToReal(voiced: List<Span>, t: Long, forStart: Boolean): Long {
        var rem = t
        for (s in voiced) {
            val inside = if (forStart) rem < s.length else rem <= s.length
            if (inside) return s.startMs + rem
            rem -= s.length
        }
        return voiced.last().endMs
    }
}
