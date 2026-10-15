package app.dearme.domain

import app.dearme.data.Repeat
import app.dearme.data.Ritual
import app.dearme.data.Timing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** When a ritual calls next. Pure, so it can be tested without a phone. */
object Schedule {
    /** Shortest random window the editor allows, minutes. */
    const val MIN_WINDOW = 30

    fun matches(r: Ritual, date: LocalDate): Boolean = when (r.repeat) {
        Repeat.WEEKLY -> (r.days and (1 shl (date.dayOfWeek.value - 1))) != 0
        Repeat.MONTHLY -> date.dayOfMonth == r.monthDay.coerceIn(1, date.lengthOfMonth())
        Repeat.YEARLY -> date.monthValue == r.month && date.dayOfMonth == r.monthDay.coerceIn(1, date.lengthOfMonth())
    }

    /** The first call strictly after [after], or null when the ritual can never call (no days picked). */
    fun next(r: Ritual, after: LocalDateTime, random: Random = Random.Default): LocalDateTime? {
        if (r.repeat == Repeat.WEEKLY && (r.days and Ritual.ALL_DAYS) == 0) return null
        val start = after.toLocalDate()
        // A yearly ritual on 29 February still lands within five years; nothing else needs that long.
        for (d in 0L..(366L * 5)) {
            val date = start.plusDays(d)
            if (!matches(r, date)) continue
            when (r.timing) {
                Timing.FIXED -> {
                    val t = date.atTime(r.at / 60, r.at % 60)
                    if (t.isAfter(after)) return t
                }
                Timing.RANDOM -> {
                    val (ws, we) = window(r)
                    val lo = date.atStartOfDay().plusMinutes(ws.toLong())
                    val hi = date.atStartOfDay().plusMinutes(we.toLong())
                    if (!hi.isAfter(after)) continue
                    val from = if (lo.isAfter(after)) lo else after.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)
                    if (!hi.isAfter(from)) continue
                    val span = ChronoUnit.SECONDS.between(from, hi)
                    return from.plusSeconds(random.nextLong(span))
                }
            }
        }
        return null
    }

    /** Window clamped to one day and at least [MIN_WINDOW] long. */
    fun window(r: Ritual): Pair<Int, Int> {
        val s = r.windowStart.coerceIn(0, 24 * 60 - MIN_WINDOW)
        val e = r.windowEnd.coerceIn(s + MIN_WINDOW, 24 * 60)
        return s to e
    }
}
