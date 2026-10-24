package uz.videoavto

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max

/** Rasmni o'qib (EXIF burilishini hisobga olib), kichraytirib, vaqtinchalik JPEG qilib saqlaydi. */
object ImagePrep {
    fun prepare(context: Context, uri: Uri, maxSide: Int, out: File): File {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxSide) {
                val k = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    max(1, (w * k).toInt()),
                    max(1, (h * k).toInt()),
                )
            }
        }
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return out
    }
}

object Storage {
    /** Tayyor videoni Galereya (Movies/VideoAvto) ga ko'chiradi. */
    fun saveToGallery(context: Context, file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VideoAvto_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoAvto")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Galereyaga yozib bo'lmadi.")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("Galereya faylini ochib bo'lmadi.")
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
