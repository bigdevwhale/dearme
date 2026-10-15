package app.dearme.ui.rituals

import android.content.Context
import app.dearme.R
import app.dearme.data.PhotoMode
import app.dearme.data.Repeat
import app.dearme.data.Ritual
import app.dearme.data.TextMode
import app.dearme.data.Timing
import java.time.LocalDate

/** Ready-made rituals to start from. */
object Templates {
    const val RANDOM = "random"
    const val SUNDAY = "sunday"
    const val MONTHLY = "monthly"
    const val BIRTHDAY = "birthday"
    const val CUSTOM = "custom"

    class T(val key: String)

    val all = listOf(T(RANDOM), T(SUNDAY), T(MONTHLY), T(BIRTHDAY), T(CUSTOM))

    fun name(context: Context, key: String): String = context.getString(
        when (key) {
            RANDOM -> R.string.tpl_random
            SUNDAY -> R.string.tpl_sunday
            MONTHLY -> R.string.tpl_monthly
            BIRTHDAY -> R.string.tpl_birthday
            else -> R.string.tpl_custom
        }
    )

    fun description(context: Context, key: String): String = context.getString(
        when (key) {
            RANDOM -> R.string.tpl_random_desc
            SUNDAY -> R.string.tpl_sunday_desc
            MONTHLY -> R.string.tpl_monthly_desc
            BIRTHDAY -> R.string.tpl_birthday_desc
            else -> R.string.tpl_custom_desc
        }
    )

    fun make(context: Context, key: String): Ritual {
        val name = name(context, key)
        return when (key) {
            RANDOM -> Ritual(
                name = name, timing = Timing.RANDOM, windowStart = 10 * 60, windowEnd = 22 * 60, repeat = Repeat.WEEKLY, days = Ritual.ALL_DAYS,
                photo = PhotoMode.DUAL, textMode = TextMode.LINE, timer = true, lockDays = Ritual.LADDER,
            )
            SUNDAY -> Ritual(
                name = name, timing = Timing.FIXED, at = 21 * 60, repeat = Repeat.WEEKLY, days = 1 shl 6,
                photo = PhotoMode.FRONT, textMode = TextMode.LETTER, usePrompt = true, timer = false, lockDays = 365,
            )
            MONTHLY -> Ritual(
                name = name, timing = Timing.FIXED, at = 9 * 60, repeat = Repeat.MONTHLY, monthDay = 1,
                photo = PhotoMode.ANY, textMode = TextMode.LETTER, usePrompt = true, prompt = context.getString(R.string.tpl_monthly_prompt),
                timer = false, lockDays = 30,
            )
            BIRTHDAY -> {
                val today = LocalDate.now()
                Ritual(
                    name = name, timing = Timing.FIXED, at = 10 * 60, repeat = Repeat.YEARLY, month = today.monthValue, monthDay = today.dayOfMonth,
                    photo = PhotoMode.FRONT, textMode = TextMode.LETTER, voice = true, timer = false, lockDays = 10 * 365,
                )
            }
            else -> Ritual(name = name, timing = Timing.RANDOM, photo = PhotoMode.ANY, textMode = TextMode.LINE, timer = false, lockDays = 365)
        }
    }
}
