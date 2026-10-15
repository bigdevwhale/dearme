package app.dearme.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Photos and voice notes live as plain files in the app's private folder; the database keeps their names. */
object Media {
    /** Long side of a stored photo: sharp on a phone, light enough for decades of capsules. */
    private const val MAX_SIDE = 1920

    fun dir(context: Context): File = File(context.filesDir, "media").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun newName(ext: String): String = UUID.randomUUID().toString().replace("-", "") + "." + ext

    /** Rotates, mirrors (selfies), shrinks and saves a camera frame; returns the file name. */
    fun saveShot(context: Context, raw: Bitmap, rotation: Int, mirror: Boolean): String {
        val m = Matrix()
        if (rotation != 0) m.postRotate(rotation.toFloat())
        if (mirror) m.postScale(-1f, 1f)
        val turned = if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        return saveBitmap(context, turned)
    }

    fun saveBitmap(context: Context, bmp: Bitmap): String {
        val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val name = newName("jpg")
        file(context, name).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return name
    }

    /** Copies a picture chosen in the gallery; null when it can't be read. */
    fun importImage(context: Context, uri: Uri): String? = runCatching {
        val bmp = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val s = info.size
                val k = MAX_SIDE.toFloat() / max(s.width, s.height)
                if (k < 1f) d.setTargetSize((s.width * k).toInt(), (s.height * k).toInt())
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        }
        saveBitmap(context, bmp)
    }.getOrNull()

    fun delete(context: Context, names: List<String>) {
        names.forEach { file(context, it).delete() }
    }

    /** Files no capsule points to any more (an abandoned draft, a crash mid-save). */
    fun sweep(context: Context, keep: Set<String>, olderThan: Long) {
        dir(context).listFiles()?.forEach { f ->
            if (f.name !in keep && f.lastModified() < olderThan) f.delete()
        }
    }
}
