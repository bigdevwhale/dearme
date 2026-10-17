package app.dearme.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.dearme.R
import app.dearme.data.Capsule
import app.dearme.data.Kind
import app.dearme.data.PhotoMode
import app.dearme.data.Repeat
import app.dearme.data.Ritual
import app.dearme.data.TextMode
import app.dearme.data.Timing
import app.dearme.domain.Lock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Dates, spans and labels in the current language; usable from notifications and from Compose. */
object Words {
    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun locale(context: Context): Locale = context.resources.configuration.locales[0]
    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

    fun year(ms: Long): String = date(ms).year.toString()

    /** "14 October 2026". */
    fun longDate(context: Context, ms: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale(context)).format(date(ms))

    /** "14 Oct 2026". */
    fun mediumDate(context: Context, ms: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale(context)).format(date(ms))

    fun clock(context: Context, ms: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(context)).format(Instant.ofEpochMilli(ms).atZone(zone()))

    fun clockOf(context: Context, minutes: Int): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(context)).format(LocalTime.of(minutes / 60 % 24, minutes % 60))

    /** "14 Oct 2026, 17:43". */
    fun dateTime(context: Context, ms: Long): String = mediumDate(context, ms) + ", " + clock(context, ms)

    /** The largest one or two units between two moments: "5 years", "1 year 2 months", "3 days", "4 hours". */
    fun span(context: Context, from: Long, to: Long): String {
        val r = context.resources
        val p = Period.between(date(minOf(from, to)), date(maxOf(from, to)))
        val parts = mutableListOf<String>()
        if (p.years > 0) parts += r.getQuantityString(R.plurals.years, p.years, p.years)
        if (p.months > 0) parts += r.getQuantityString(R.plurals.months, p.months, p.months)
        if (parts.isEmpty()) {
            val days = p.days
            if (days >= 7 && days % 7 == 0) parts += r.getQuantityString(R.plurals.weeks, days / 7, days / 7)
            else if (days > 0) parts += r.getQuantityString(R.plurals.days, days, days)
        }
        if (parts.isEmpty()) {
            val minutes = ((maxOf(from, to) - minOf(from, to)) / 60_000).toInt()
            parts += if (minutes >= 60) r.getQuantityString(R.plurals.hours, minutes / 60, minutes / 60)
            else r.getQuantityString(R.plurals.minutes, minutes.coerceAtLeast(1), minutes.coerceAtLeast(1))
        }
        return parts.take(2).joinToString(" ")
    }

    /** "3 years ago", "today". */
    fun ago(context: Context, from: Long, now: Long): String =
        if (now - from < 12 * 3_600_000L && date(from) == date(now)) context.getString(R.string.today_lower)
        else context.getString(R.string.ago, span(context, from, now))

    /** "in 5 years". */
    fun inSpan(context: Context, now: Long, to: Long): String = context.getString(R.string.in_span, span(context, now, to))

    /** Countdown to the second: "123 d 04:05:06" or "04:05:06". */
    fun countdown(context: Context, ms: Long): String {
        val s = (ms.coerceAtLeast(0) / 1000)
        val d = s / 86_400
        val hms = "%02d:%02d:%02d".format(Locale.ROOT, s % 86_400 / 3600, s % 3600 / 60, s % 60)
        return if (d > 0) context.getString(R.string.countdown_days, d.toString(), hms) else hms
    }

    /** "1:42". */
    fun minSec(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d".format(Locale.ROOT, s / 60, s % 60)
    }

    /** A lock preset: "1 week", "1 month", "5 years". */
    fun lock(context: Context, days: Int): String {
        val r = context.resources
        return when {
            days == Ritual.LADDER -> context.getString(R.string.lock_ladder)
            days >= 365 && days % 365 == 0 -> r.getQuantityString(R.plurals.years, days / 365, days / 365)
            days % 30 == 0 -> r.getQuantityString(R.plurals.months, days / 30, days / 30)
            days % 7 == 0 -> r.getQuantityString(R.plurals.weeks, days / 7, days / 7)
            else -> r.getQuantityString(R.plurals.days, days, days)
        }
    }

    fun words(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    /** "Mon, Wed, Fri", "Every day", "Weekdays". */
    fun days(context: Context, mask: Int): String {
        val all = Ritual.ALL_DAYS
        return when (mask and all) {
            all -> context.getString(R.string.every_day)
            0b0011111 -> context.getString(R.string.weekdays)
            0b1100000 -> context.getString(R.string.weekends)
            else -> DayOfWeek.entries.filter { (mask and (1 shl (it.value - 1))) != 0 }
                .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale(context)) }
        }
    }

    /** One line about when a ritual calls: "Every day · random between 10:00 and 22:00". */
    fun ritualWhen(context: Context, r: Ritual): String {
        val l = locale(context)
        val repeat = when (r.repeat) {
            Repeat.WEEKLY -> days(context, r.days)
            Repeat.MONTHLY -> context.getString(R.string.monthly_on, r.monthDay)
            Repeat.YEARLY -> context.getString(
                R.string.yearly_on,
                DateTimeFormatter.ofPattern("d MMMM", l).format(LocalDate.of(2024, r.month, r.monthDay.coerceIn(1, java.time.YearMonth.of(2024, r.month).lengthOfMonth()))),
            )
        }
        val time = when (r.timing) {
            Timing.RANDOM -> context.getString(R.string.random_between, clockOf(context, r.windowStart), clockOf(context, r.windowEnd))
            Timing.FIXED -> context.getString(R.string.at_time, clockOf(context, r.at))
        }
        return "$repeat · $time"
    }

    /** One line about what goes in: "Both cameras + a line · opens in 1 year". */
    fun ritualWhat(context: Context, r: Ritual): String {
        val parts = mutableListOf<String>()
        photoMode(context, r.photo)?.let { parts += it }
        when (r.textMode) {
            TextMode.LINE -> parts += context.getString(R.string.what_line)
            TextMode.LETTER -> parts += context.getString(R.string.what_letter)
            TextMode.NONE -> {}
        }
        if (r.voice) parts += context.getString(R.string.what_voice)
        val what = parts.joinToString(" + ").replaceFirstChar { it.titlecase(locale(context)) }
        val opens = if (r.lockDays == Ritual.LADDER) context.getString(R.string.lock_ladder_short) else context.getString(R.string.opens_after, lock(context, r.lockDays))
        return listOf(what, opens).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun photoMode(context: Context, m: PhotoMode): String? = when (m) {
        PhotoMode.NONE -> null
        PhotoMode.BACK -> context.getString(R.string.mode_back)
        PhotoMode.FRONT -> context.getString(R.string.mode_front)
        PhotoMode.DUAL -> context.getString(R.string.mode_dual)
        PhotoMode.ANY -> context.getString(R.string.mode_any)
    }

    /** What a sealed capsule is called in lists: "To me, in 5 years" or the ritual's name. */
    fun capsuleTitle(context: Context, c: Capsule, ritual: Ritual?): String = when {
        c.kind == Kind.MOMENT && ritual != null -> ritual.name
        c.kind == Kind.MOMENT -> context.getString(R.string.moment)
        c.replyTo != null -> context.getString(R.string.reply_title)
        else -> context.getString(R.string.letter_to_me, span(context, c.createdAt, c.openAt))
    }

    /** "A week for now; from 12 November, a month." Null when the ladder is at its last step. */
    fun ladderNote(context: Context, firstCapsule: Long?, now: Long): String? {
        val next = Lock.ladderNextStep(firstCapsule, now) ?: return null
        val nextDays = if (Lock.ladderDays(firstCapsule, now) == 7) 30 else 365
        return context.getString(R.string.ladder_note, lock(context, Lock.ladderDays(firstCapsule, now)), mediumDate(context, next), lock(context, nextDays))
    }

    /** The default lock presets plus the ladder, in the order shown. */
    val ritualLocks: List<Int> = listOf(Ritual.LADDER) + Lock.presets
}

@Composable
fun locale(): Locale = LocalConfiguration.current.locales[0]

/** Shorthand for [Words] calls inside composables. */
@Composable
fun ctx(): Context {
    LocalConfiguration.current
    return LocalContext.current
}
