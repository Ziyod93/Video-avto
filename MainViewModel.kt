package uz.videoavto

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Status {
    data object Idle : Status
    data class Working(val message: String, val progress: Int? = null) : Status
    data class Done(val uri: Uri, val summary: String) : Status
    data class Error(val message: String) : Status
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var audioUri by mutableStateOf<Uri?>(null)
        private set
    var audioName by mutableStateOf("")
        private set
    val images = mutableStateListOf<Uri>()

    var script by mutableStateOf("")
    var orientation by mutableStateOf(Orientation.LANDSCAPE)
    var quality by mutableStateOf(Quality.HD)
    var subtitles by mutableStateOf(true)
    var effects by mutableStateOf(true)

    var status by mutableStateOf<Status>(Status.Idle)
        private set

    private var job: Job? = null

    val canStart: Boolean
        get() = audioUri != null && images.isNotEmpty() && status !is Status.Working

    fun setAudio(uri: Uri) {
        audioUri = uri
        audioName = queryName(uri) ?: "Ovoz fayli"
    }

    fun addImages(uris: List<Uri>) {
        images.addAll(uris)
    }

    fun moveImage(index: Int, delta: Int) {
        val to = index + delta
        if (index !in images.indices || to !in images.indices) return
        val item = images.removeAt(index)
        images.add(to, item)
    }

    fun removeImage(index: Int) {
        if (index in images.indices) images.removeAt(index)
    }

    fun clearImages() = images.clear()

    fun dismissStatus() {
        if (status !is Status.Working) status = Status.Idle
    }

    fun cancel() {
        job?.cancel()
    }

    fun start() {
        val audio = audioUri ?: return
        if (images.isEmpty() || status is Status.Working) return

        val imageList = images.toList()
        val settings = RenderSettings(orientation, quality, subtitles, effects)
        val scriptText = script
        val ctx = getApplication<Application>()

        job = viewModelScope.launch {
            val temp = mutableListOf<File>()
            try {
                status = Status.Working("Ovoz tahlil qilinmoqda…")
                val analysis = withContext(Dispatchers.Default) { AudioAnalyzer.analyze(ctx, audio) }
                require(analysis.durationMs > 1000) { "Ovoz fayli juda qisqa yoki o'qib bo'lmadi." }

                val maxSide = maxOf(settings.width, settings.height)
                val prepared = ArrayList<Uri>()
                imageList.forEachIndexed { i, uri ->
                    status = Status.Working("Rasmlar tayyorlanmoqda… ${i + 1}/${imageList.size}")
                    val f = withContext(Dispatchers.IO) {
                        ImagePrep.prepare(ctx, uri, maxSide, File(ctx.cacheDir, "img_$i.jpg"))
                    }
                    temp += f
                    prepared += Uri.fromFile(f)
                }

                val scenes = Planner.planScenes(prepared, analysis)
                val maxWords = if (settings.orientation == Orientation.PORTRAIT) 4 else 6
                val cues = if (settings.subtitles && scriptText.isNotBlank()) {
                    Planner.buildCues(scriptText, analysis, maxWords)
                } else {
                    emptyList()
                }

                val out = File(ctx.cacheDir, "video_${System.currentTimeMillis()}.mp4")
                temp += out
                status = Status.Working("Video yig'ilmoqda…", 0)
                VideoExporter(ctx).export(audio, scenes, cues, settings, out) { p ->
                    status = Status.Working("Video yig'ilmoqda…", p)
                }

                status = Status.Working("Galereyaga saqlanmoqda…")
                val saved = withContext(Dispatchers.IO) { Storage.saveToGallery(ctx, out) }

                val secs = (analysis.durationMs + 600) / 1000
                status = Status.Done(
                    saved,
                    "Davomiyligi ~$secs soniya, ${scenes.size} ta sahna, ${cues.size} ta subtitr, " +
                        "${analysis.pauses.size} ta pauza topildi.",
                )
            } catch (e: CancellationException) {
                status = Status.Idle
                throw e
            } catch (e: Throwable) {
                status = Status.Error(e.message ?: e.toString())
            } finally {
                temp.forEach { it.delete() }
            }
        }
    }

    private fun queryName(uri: Uri): String? = try {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }
}
