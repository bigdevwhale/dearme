package app.dearme.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Locale
import kotlin.random.Random

/** Made-up letters and moments for README screenshots; only takes effect with `-Pdemo` (app.dearme.demo). */
object DemoSeed {
    private const val DAY = 86_400_000L
    private const val HOUR = 3_600_000L

    suspend fun seedIfEmpty(context: Context, db: DearDatabase, settings: SettingsStore) {
        val dao = db.dao()
        if (dao.capsules().isNotEmpty()) return
        val ru = Locale.getDefault().language == "ru"
        val now = System.currentTimeMillis()
        settings.update { it.copy(onboarded = true, name = if (ru) "Аня" else "Anna", lastBackup = now - 3 * DAY) }

        val randomId = dao.insertRitual(
            Ritual(
                name = if (ru) "Случайный момент" else "Random moment", timing = Timing.RANDOM, photo = PhotoMode.DUAL,
                textMode = TextMode.LINE, timer = true, lockDays = Ritual.LADDER, createdAt = now - 400 * DAY,
            )
        )
        val sundayId = dao.insertRitual(
            Ritual(
                name = if (ru) "Воскресное письмо" else "Sunday letter", timing = Timing.FIXED, at = 21 * 60, days = 1 shl 6,
                photo = PhotoMode.FRONT, textMode = TextMode.LETTER, usePrompt = true, timer = false, lockDays = 365, createdAt = now - 399 * DAY,
            )
        )
        dao.insertRitual(
            Ritual(
                name = if (ru) "Письмо на день рождения" else "Birthday letter", timing = Timing.FIXED, at = 10 * 60, repeat = Repeat.YEARLY,
                month = 3, monthDay = 14, photo = PhotoMode.FRONT, textMode = TextMode.LETTER, voice = true, timer = false,
                lockDays = 3650, enabled = true, createdAt = now - 398 * DAY,
            )
        )

        fun shot(scene: Int, seed: Int, selfie: Boolean = true): Pair<String, String?> {
            val back = Media.saveBitmap(context, paint(scene, seed, 900, 1200))
            val front = if (selfie) Media.saveBitmap(context, selfie(seed, 600, 800)) else null
            return back to front
        }

        fun asset(path: String): String? = runCatching {
            context.assets.open(path).use { Media.saveBitmap(context, android.graphics.BitmapFactory.decodeStream(it)) }
        }.getOrNull()

        suspend fun add(c: Capsule, scene: Int? = null, selfie: Boolean = true, now2: Int? = null, photo: Pair<String, String>? = null) {
            val id = dao.insertCapsule(c)
            val real = photo?.let { (b, f) -> asset(b)?.let { it to asset(f) } }
            if (real != null) {
                dao.insertPhoto(Photo(capsuleId = id, back = real.first, front = real.second, takenAt = c.createdAt))
            } else if (scene != null) {
                val (b, f) = shot(scene, c.createdAt.toInt(), selfie)
                dao.insertPhoto(Photo(capsuleId = id, back = b, front = f, takenAt = c.createdAt))
            }
            if (now2 != null) {
                val (b, f) = shot(now2, c.createdAt.toInt() + 7, selfie)
                dao.insertPhoto(Photo(capsuleId = id, back = b, front = f, takenAt = c.openedAt ?: now, role = Photo.ROLE_NOW))
            }
            if (c.ritualId != null) dao.insertCall(Call(ritualId = c.ritualId, firedAt = c.createdAt - 40_000, deadline = c.createdAt + 80_000, capsuleId = id))
        }

        // The letter in every screenshot: written a year ago, opened yesterday, with a "then & now".
        add(
            Capsule(
                kind = Kind.LETTER, createdAt = now - 366 * DAY - 5 * HOUR, openAt = now - DAY - 5 * HOUR, openedAt = now - DAY, notified = true,
                prompt = if (ru) "Чего ты сейчас боишься больше всего?" else "What are you most afraid of right now?",
                text = if (ru) "Что не справлюсь с новой работой. Завтра первый день, я третий раз перекладываю одежду на стуле.\n\nЕсли ты читаешь это и всё ещё там — скажи себе спасибо за этот страх. Если нет — тоже ничего. Главное, не забывай ходить к морю по вечерам."
                else "That I won't be good enough at the new job. Tomorrow is day one and I've rearranged my clothes on the chair three times.\n\nIf you're reading this and you're still there, thank the scared version of you. If not, that's fine too. Just keep walking to the sea in the evenings.",
            ),
            scene = 0, now2 = 0,
        )
        // Ready to open right now.
        add(
            Capsule(
                kind = Kind.LETTER, createdAt = now - 365 * DAY + 2 * HOUR, openAt = now - 2 * HOUR, notified = true,
                text = if (ru) "Сегодня выпал первый снег, и я наконец купила тёплые ботинки. Интересно, ты всё ещё живёшь в этой квартире с жёлтыми окнами напротив?"
                else "The first snow fell today and I finally bought warm boots. I wonder if you still live in this flat with the yellow windows across the street?",
            ),
            scene = 2, photo = "demo/snow.jpg" to "demo/selfie.jpg",
        )
        // Opened moments from the ladder.
        val lines = if (ru) listOf("Опять дождь, опять кофе.", "Первый снег! Мокрые ботинки.", "Ждём поезд, все уставшие и счастливые.", "Дедлайн в 18:00, сейчас 17:43.", "Просто сижу на балконе.")
        else listOf("Rain again, coffee again.", "First snow! Wet boots.", "Waiting for the train, tired and happy.", "Deadline at 6, it's 5:43.", "Just sitting on the balcony.")
        lines.forEachIndexed { i, line ->
            val created = now - (60 + i * 23) * DAY - (i * 3 + 2) * HOUR
            add(
                Capsule(
                    kind = Kind.MOMENT, createdAt = created, openAt = created + 30 * DAY, openedAt = created + 30 * DAY + HOUR, notified = true,
                    text = line, ritualId = randomId, lateMs = if (i == 3) 12 * 60_000L else 0,
                ),
                scene = (i + 1) % 5,
            )
        }
        // On their way.
        add(
            Capsule(
                kind = Kind.LETTER, createdAt = now - 20 * DAY, openAt = now - 20 * DAY + 30L * 365 * DAY + 7 * DAY,
                text = (if (ru) "письмо себе через тридцать лет " else "a letter for thirty years from now ").repeat(40).trim(),
                voice = null, prompt = "",
            ),
            scene = 4,
        )
        add(Capsule(kind = Kind.LETTER, createdAt = now - 214 * DAY, openAt = now - 214 * DAY + 3650 * DAY, text = "…".repeat(200), voice = null, voiceMs = 0))
        for (i in 0 until 4) {
            val created = now - (i * 7 + 2) * DAY - 3 * HOUR
            add(Capsule(kind = Kind.LETTER, createdAt = created, openAt = created + 365 * DAY, text = "word ".repeat(120 + i * 40), ritualId = sundayId), scene = 3, selfie = true)
        }
        val recent = if (ru) listOf("Обед на бегу", "Кот спит на клавиатуре", "Закат с балкона", "Очередь в банке", "Читаю в парке")
        else listOf("Lunch on the run", "The cat is asleep on my keyboard", "Sunset from the balcony", "Queue at the bank", "Reading in the park")
        recent.forEachIndexed { i, line ->
            val created = now - (i + 1) * DAY - (i * 2 + 1) * HOUR
            add(Capsule(kind = Kind.MOMENT, createdAt = created, openAt = created + 7 * DAY + 3 * HOUR, text = line, ritualId = randomId), scene = i % 5)
        }
        // Calls from the last days; one was missed.
        dao.insertCall(Call(ritualId = randomId, firedAt = now - 9 * DAY, deadline = now - 9 * DAY + 120_000, dismissed = true))
    }

    /** A painted stand-in for a photo: 0 sea at sunset, 1 rainy window, 2 snowy street, 3 desk with tea, 4 mountains. */
    private fun paint(scene: Int, seed: Int, w: Int, h: Int): Bitmap {
        val rnd = Random(seed)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun sky(top: Int, bottom: Int, until: Float = h.toFloat()) {
            p.shader = LinearGradient(0f, 0f, 0f, until, top, bottom, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), until, p)
            p.shader = null
        }
        when (scene) {
            0 -> {
                sky(0xFF2B2D5C.toInt(), 0xFFF2A36B.toInt(), h * 0.62f)
                p.shader = RadialGradient(w * 0.5f, h * 0.58f, w * 0.5f, 0xFFFFE2A8.toInt(), 0x00FFE2A8, Shader.TileMode.CLAMP)
                c.drawCircle(w * 0.5f, h * 0.58f, w * 0.5f, p); p.shader = null
                p.color = 0xFFFFD58A.toInt(); c.drawCircle(w * 0.5f, h * 0.6f, w * 0.09f, p)
                p.shader = LinearGradient(0f, h * 0.62f, 0f, h.toFloat(), 0xFF4A4E7A.toInt(), 0xFF1D1F3B.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(0f, h * 0.62f, w.toFloat(), h.toFloat(), p); p.shader = null
                p.color = 0x66FFD58A
                for (i in 0 until 14) {
                    val y = h * 0.64f + i * h * 0.025f
                    val half = w * (0.18f - i * 0.009f) * (0.8f + rnd.nextFloat() * 0.4f)
                    c.drawRect(w * 0.5f - half, y, w * 0.5f + half, y + h * 0.006f, p)
                }
            }
            1 -> {
                sky(0xFF6F7D8C.toInt(), 0xFF39434F.toInt())
                p.color = 0xFF2A323B.toInt()
                for (i in 0 until 6) c.drawRect(i * w / 6f + 10, h * (0.45f + rnd.nextFloat() * 0.2f), (i + 1) * w / 6f - 10, h.toFloat(), p)
                p.color = 0xFFF2C46B.toInt()
                for (i in 0 until 40) {
                    val x = rnd.nextInt(w).toFloat(); val y = h * (0.55f + rnd.nextFloat() * 0.4f)
                    c.drawRect(x, y, x + 14, y + 18, p)
                }
                p.color = 0x55FFFFFF
                for (i in 0 until 120) {
                    val x = rnd.nextInt(w).toFloat(); val y = rnd.nextInt(h).toFloat(); val r = 3f + rnd.nextFloat() * 7
                    c.drawOval(x, y, x + r, y + r * 1.6f, p)
                }
                p.color = 0xFF1A1E24.toInt(); p.strokeWidth = 18f
                c.drawLine(w / 2f, 0f, w / 2f, h.toFloat(), p); c.drawLine(0f, h * 0.4f, w.toFloat(), h * 0.4f, p)
            }
            2 -> {
                sky(0xFFB9C6D6.toInt(), 0xFFE9EEF3.toInt())
                p.color = 0xFF8C95A3.toInt()
                c.drawRect(0f, h * 0.3f, w * 0.3f, h * 0.8f, p); c.drawRect(w * 0.7f, h * 0.25f, w.toFloat(), h * 0.8f, p)
                p.color = 0xFFFFE7A6.toInt()
                for (i in 0 until 10) c.drawRect(w * 0.05f + (i % 2) * w * 0.12f, h * 0.35f + (i / 2) * h * 0.08f, w * 0.12f + (i % 2) * w * 0.12f, h * 0.39f + (i / 2) * h * 0.08f, p)
                p.color = 0xFFF7F9FB.toInt(); c.drawRect(0f, h * 0.8f, w.toFloat(), h.toFloat(), p)
                p.color = Color.WHITE
                for (i in 0 until 160) c.drawCircle(rnd.nextInt(w).toFloat(), rnd.nextInt(h).toFloat(), 2f + rnd.nextFloat() * 6, p)
            }
            3 -> {
                sky(0xFF8A6A50.toInt(), 0xFF5B4434.toInt())
                p.color = 0xFFF4EFE6.toInt(); c.save(); c.rotate(-8f, w / 2f, h / 2f)
                c.drawRect(w * 0.12f, h * 0.18f, w * 0.72f, h * 0.7f, p)
                p.color = 0xFFB9B0A2.toInt()
                for (i in 0 until 12) c.drawRect(w * 0.18f, h * 0.24f + i * h * 0.035f, w * (0.5f + rnd.nextFloat() * 0.18f), h * 0.247f + i * h * 0.035f, p)
                c.restore()
                p.color = 0xFFEFE9E0.toInt(); c.drawCircle(w * 0.74f, h * 0.76f, w * 0.17f, p)
                p.color = 0xFF9A5A2C.toInt(); c.drawCircle(w * 0.74f, h * 0.76f, w * 0.13f, p)
                p.color = 0x33FFFFFF; c.drawCircle(w * 0.7f, h * 0.73f, w * 0.04f, p)
            }
            else -> {
                sky(0xFF7FB3E0.toInt(), 0xFFDCEBF5.toInt())
                fun ridge(color: Int, base: Float, amp: Float) {
                    val path = Path().apply {
                        moveTo(0f, h.toFloat()); lineTo(0f, base)
                        var x = 0f
                        while (x <= w) { lineTo(x, base - rnd.nextFloat() * amp); x += w / 7f }
                        lineTo(w.toFloat(), h.toFloat()); close()
                    }
                    p.color = color; c.drawPath(path, p)
                }
                ridge(0xFF9DB0C8.toInt(), h * 0.55f, h * 0.2f)
                ridge(0xFF5F7F73.toInt(), h * 0.7f, h * 0.15f)
                ridge(0xFF2F4D41.toInt(), h * 0.85f, h * 0.1f)
            }
        }
        return b
    }

    /** A soft head-and-shoulders silhouette against warm light, as a stand-in selfie. */
    private fun selfie(seed: Int, w: Int, h: Int): Bitmap {
        val rnd = Random(seed)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val warm = listOf(0xFFE9B78F.toInt(), 0xFFB9C9E8.toInt(), 0xFFD9C3E6.toInt(), 0xFFC9E0C4.toInt())[rnd.nextInt(4)]
        p.shader = RadialGradient(w * 0.5f, h * 0.35f, h * 0.8f, warm, 0xFF3A3340.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p); p.shader = null
        p.color = 0xFF2C2630.toInt()
        c.drawOval(w * 0.3f, h * 0.22f, w * 0.7f, h * 0.62f, p)
        c.drawRoundRect(w * 0.08f, h * 0.66f, w * 0.92f, h * 1.2f, w * 0.3f, w * 0.3f, p)
        c.drawRect(w * 0.42f, h * 0.55f, w * 0.58f, h * 0.7f, p)
        return b
    }

    private object Color {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
