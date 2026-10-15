package app.dearme.domain

import app.dearme.data.Ritual
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** How long a capsule stays sealed. */
object Lock {
    /** Presets offered everywhere, in days. Multiples of 365 are whole years, multiples of 30 calendar months. */
    val presets = listOf(7, 30, 365, 5 * 365, 10 * 365, 30 * 365)

    /** The moment [days] after [from]: whole months and years follow the calendar, so a year is a year. */
    fun openAt(from: Long, days: Int, zone: ZoneId): Long {
        val z = Instant.ofEpochMilli(from).atZone(zone)
        val t: ZonedDateTime = when {
            days >= 365 && days % 365 == 0 -> z.plusYears((days / 365).toLong())
            days >= 30 && days % 30 == 0 -> z.plusMonths((days / 30).toLong())
            else -> z.plusDays(days.toLong())
        }
        return t.toInstant().toEpochMilli()
    }

    /**
     * The ladder for everyday moments, so the first openings come soon:
     * a week while the habit is new, a month during the first year, a year after that.
     */
    fun ladderDays(firstCapsule: Long?, now: Long): Int {
        val used = if (firstCapsule == null) 0 else ChronoUnit.DAYS.between(Instant.ofEpochMilli(firstCapsule), Instant.ofEpochMilli(now))
        return when {
            used < 28 -> 7
            used < 365 -> 30
            else -> 365
        }
    }

    fun daysFor(r: Ritual?, firstCapsule: Long?, now: Long): Int =
        if (r == null || r.lockDays == Ritual.LADDER) ladderDays(firstCapsule, now) else r.lockDays
}
