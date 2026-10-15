package app.dearme.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import app.dearme.R
import app.dearme.data.Capsule
import app.dearme.data.Media
import app.dearme.data.Photo
import app.dearme.ui.Words
import java.io.File
import kotlin.math.max

/**
 * Story-sized pictures (1080×1920) for sharing: a sealed letter, an opened one, and "then & now".
 * Drawn with plain Canvas so they look the same on every phone.
 */
object ShareCards {
    private const val W = 1080
    private const val H = 1920

    private const val PAPER = 0xFFF5EFE4.toInt()
    private const val INK = 0xFF1E2433.toInt()
    private const val MUTED = 0xFF6A6455.toInt()
    private const val WAX = 0xFFB3342F.toInt()
    private const val WAX_DARK = 0xFF7E1F1C.toInt()
    private const val ENVELOPE = 0xFFF1E4CC.toInt()
    private const val ENVELOPE_SHADE = 0xFFE2D0AE.toInt()
    private const val BLUE = 0xFF2F5C93.toInt()

    private class Fonts(context: Context) {
        val lora: Typeface = ResourcesCompat.getFont(context, R.font.lora_regular) ?: Typeface.SERIF
        val loraMedium: Typeface = ResourcesCompat.getFont(context, R.font.lora_medium) ?: Typeface.SERIF
        val inter: Typeface = ResourcesCompat.getFont(context, R.font.inter_medium) ?: Typeface.SANS_SERIF
        val hand: Typeface = ResourcesCompat.getFont(context, R.font.caveat) ?: Typeface.SERIF
    }

    private fun base(): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(PAPER)
        return b to c
    }

    private fun paint(tf: Typeface, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = color; textAlign = align }

    private fun footer(c: Canvas, f: Fonts, context: Context) {
        c.drawText(context.getString(R.string.share_footer), W / 2f, H - 110f, paint(f.inter, 34f, MUTED).apply { letterSpacing = 0.12f })
    }

    /** "I sealed a letter to myself. It opens on 14 October 2056." */
    fun sealed(context: Context, capsule: Capsule): Bitmap {
        val f = Fonts(context)
        val (b, c) = base()
        c.drawText(context.getString(R.string.share_dear_me), W / 2f, 360f, paint(f.hand, 120f, BLUE))
        drawEnvelope(c, RectF(140f, 520f, W - 140f, 520f + (W - 280f) / 1.5f))
        c.drawText(context.getString(R.string.share_sealed_title), W / 2f, 1240f, paint(f.loraMedium, 64f, INK))
        c.drawText(context.getString(R.string.share_opens_on), W / 2f, 1360f, paint(f.inter, 40f, MUTED))
        c.drawText(Words.longDate(context, capsule.openAt), W / 2f, 1460f, paint(f.loraMedium, 76f, WAX))
        c.drawText(Words.inSpan(context, capsule.createdAt, capsule.openAt), W / 2f, 1550f, paint(f.inter, 40f, MUTED))
        footer(c, f, context)
        return b
    }

    /** An opened letter: the photo, a few lines of the text, and how long it travelled. */
    fun opened(context: Context, capsule: Capsule, photo: Photo?): Bitmap {
        val f = Fonts(context)
        val (b, c) = base()
        var y = 170f
        c.drawText(context.getString(R.string.share_from_me, Words.span(context, capsule.createdAt, capsule.openedAt ?: capsule.openAt)), W / 2f, y, paint(f.hand, 84f, BLUE))
        y += 70f
        c.drawText(Words.longDate(context, capsule.createdAt), W / 2f, y, paint(f.inter, 36f, MUTED))
        y += 60f
        if (photo != null) {
            val h = if (capsule.text.isBlank()) 1240f else 960f
            val w = h * 3f / 4f
            val rect = RectF((W - w) / 2f, y, (W + w) / 2f, y + h)
            drawShot(context, c, rect, photo)
            y += h + 70f
        } else y += 80f
        val quote = capsule.text.trim().ifBlank { capsule.prompt }
        if (quote.isNotBlank()) {
            val tp = paint(f.lora, if (photo != null) 46f else 58f, INK, Paint.Align.LEFT)
            val maxLines = ((H - 260f - y) / (tp.textSize * 1.45f)).toInt().coerceAtLeast(2)
            val layout = StaticLayout.Builder.obtain("“$quote”", 0, quote.length + 2, tp, W - 200)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.3f)
                .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build()
            c.save()
            c.translate(100f, y)
            layout.draw(c)
            c.restore()
        }
        footer(c, f, context)
        return b
    }

    /** The old shot over the new one, with both dates. */
    fun thenNow(context: Context, capsule: Capsule, then: Photo, now: Photo): Bitmap {
        val f = Fonts(context)
        val (b, c) = base()
        c.drawText(context.getString(R.string.share_then_now), W / 2f, 150f, paint(f.hand, 92f, BLUE))
        val h = 780f
        val w = h * 3f / 4f
        val left = (W - w) / 2f
        drawShot(context, c, RectF(left, 220f, left + w, 220f + h), then)
        drawShot(context, c, RectF(left, 220f + h + 60f, left + w, 220f + 2 * h + 60f), now)
        val label = paint(f.inter, 34f, Color.WHITE, Paint.Align.LEFT)
        val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
        listOf(220f to Words.mediumDate(context, then.takenAt), 220f + h + 60f to Words.mediumDate(context, now.takenAt)).forEach { (top, text) ->
            val tw = label.measureText(text)
            c.drawRoundRect(RectF(left + w - tw - 64f, top + h - 84f, left + w - 20f, top + h - 24f), 30f, 30f, pill)
            c.drawText(text, left + w - tw - 42f, top + h - 42f, label)
        }
        footer(c, f, context)
        return b
    }

    private fun drawShot(context: Context, c: Canvas, rect: RectF, photo: Photo) {
        val radius = 40f
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        c.drawColor(0xFFE6DCCB.toInt())
        val main = photo.back ?: photo.front
        main?.let { drawCover(context, c, rect, it) }
        c.restore()
        val small = if (photo.back != null) photo.front else null
        if (small != null) {
            val sw = rect.width() * 0.3f
            val sr = RectF(rect.left + 28f, rect.top + 28f, rect.left + 28f + sw, rect.top + 28f + sw * 4f / 3f)
            val sp = Path().apply { addRoundRect(sr, 26f, 26f, Path.Direction.CW) }
            c.save()
            c.clipPath(sp)
            drawCover(context, c, sr, small)
            c.restore()
            c.drawRoundRect(sr, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.BLACK })
        }
    }

    /** Draws a stored photo scaled to fill [rect], cropping the overflow. */
    private fun drawCover(context: Context, c: Canvas, rect: RectF, name: String) {
        val file = Media.file(context, name)
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val k = max(rect.width() / bmp.width, rect.height() / bmp.height)
        val w = bmp.width * k
        val h = bmp.height * k
        val dst = RectF(rect.centerX() - w / 2, rect.centerY() - h / 2, rect.centerX() + w / 2, rect.centerY() + h / 2)
        c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun drawEnvelope(c: Canvas, r: RectF) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = r.width()
        val h = r.height()
        p.color = ENVELOPE_SHADE
        c.drawRoundRect(r, w * 0.035f, w * 0.035f, p)
        val pocket = Path().apply {
            moveTo(r.left, r.top + h * 0.18f); lineTo(r.left + w / 2, r.top + h * 0.64f); lineTo(r.right, r.top + h * 0.18f)
            lineTo(r.right, r.bottom); lineTo(r.left, r.bottom); close()
        }
        p.color = ENVELOPE
        c.drawPath(pocket, p)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = ENVELOPE_SHADE; strokeJoin = Paint.Join.ROUND }
        c.drawPath(Path().apply { moveTo(r.left, r.bottom); lineTo(r.left + w / 2, r.top + h * 0.52f); lineTo(r.right, r.bottom) }, stroke)
        val apex = r.top + h * 0.56f
        val flap = Path().apply { moveTo(r.left, r.top); lineTo(r.right, r.top); lineTo(r.left + w / 2, apex); close() }
        p.shader = LinearGradient(0f, r.top, 0f, apex, ENVELOPE, ENVELOPE_SHADE, Shader.TileMode.CLAMP)
        c.drawPath(flap, p)
        p.shader = null
        c.drawPath(flap, stroke)
        // Wax seal with a heart.
        val cx = r.left + w / 2
        val rad = h * 0.17f
        p.color = WAX
        for (i in 0 until 7) {
            val a = Math.toRadians(i * 360.0 / 7 + 10)
            c.drawCircle(cx + (rad * 0.68f * Math.cos(a)).toFloat(), apex + (rad * 0.68f * Math.sin(a)).toFloat(), rad * 0.42f, p)
        }
        c.drawCircle(cx, apex, rad * 0.95f, p)
        c.drawCircle(cx, apex, rad * 0.72f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = rad * 0.09f; color = WAX_DARK; alpha = 140 })
        val s = rad * 0.36f
        val heart = Path().apply {
            moveTo(cx, apex + s * 0.95f)
            cubicTo(cx - s * 1.45f, apex + s * 0.05f, cx - s * 0.85f, apex - s * 1.1f, cx, apex - s * 0.35f)
            cubicTo(cx + s * 0.85f, apex - s * 1.1f, cx + s * 1.45f, apex + s * 0.05f, cx, apex + s * 0.95f)
            close()
        }
        p.color = WAX_DARK
        c.drawPath(heart, p)
    }

    /** Saves the picture to the share cache and opens the system share sheet. */
    fun share(context: Context, bitmap: Bitmap, name: String) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
