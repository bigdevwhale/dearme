package app.dearme

import app.dearme.data.Repeat
import app.dearme.data.Ritual
import app.dearme.data.Timing
import app.dearme.domain.Lock
import app.dearme.domain.Schedule
import app.dearme.domain.TimeGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

class ScheduleTest {
    private val base = Ritual(name = "r")

    @Test
    fun fixedTimeLaterTodayIsToday() {
        val r = base.copy(timing = Timing.FIXED, at = 21 * 60)
        assertEquals(LocalDateTime.of(2026, 10, 15, 21, 0), Schedule.next(r, LocalDateTime.of(2026, 10, 15, 9, 0)))
    }

    @Test
    fun fixedTimeAlreadyPassedIsTomorrow() {
        val r = base.copy(timing = Timing.FIXED, at = 8 * 60)
        assertEquals(LocalDateTime.of(2026, 10, 16, 8, 0), Schedule.next(r, LocalDateTime.of(2026, 10, 15, 9, 0)))
    }

    @Test
    fun randomStaysInsideWindowAndAfterNow() {
        val r = base.copy(timing = Timing.RANDOM, windowStart = 10 * 60, windowEnd = 22 * 60)
        val rnd = Random(1)
        repeat(500) {
            val after = LocalDateTime.of(2026, 10, 15, 0, 0).plusMinutes(rnd.nextLong(24 * 60))
            val t = Schedule.next(r, after, rnd)!!
            assertTrue(t.isAfter(after))
            val m = t.hour * 60 + t.minute
            assertTrue("$t", m in 10 * 60 until 22 * 60)
        }
    }

    @Test
    fun randomLateInWindowStaysToday() {
        val r = base.copy(timing = Timing.RANDOM, windowStart = 10 * 60, windowEnd = 22 * 60)
        val after = LocalDateTime.of(2026, 10, 15, 21, 30)
        val t = Schedule.next(r, after, Random(3))!!
        assertEquals(after.toLocalDate(), t.toLocalDate())
    }

    @Test
    fun randomCallsOnceADay() {
        val r = base.copy(timing = Timing.RANDOM, windowStart = 10 * 60, windowEnd = 22 * 60)
        val firedAt = LocalDateTime.of(2026, 10, 15, 11, 0)
        repeat(50) { seed ->
            val t = Schedule.next(r, firedAt, Random(seed), skip = firedAt.toLocalDate())!!
            assertEquals(LocalDate.of(2026, 10, 16), t.toLocalDate())
        }
    }

    @Test
    fun weeklyOnlyOnPickedDays() {
        val sunday = base.copy(timing = Timing.FIXED, at = 21 * 60, days = 1 shl 6)
        val t = Schedule.next(sunday, LocalDateTime.of(2026, 10, 15, 12, 0))!!
        assertEquals(DayOfWeek.SUNDAY, t.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 18), t.toLocalDate())
    }

    @Test
    fun noDaysNeverCalls() {
        assertNull(Schedule.next(base.copy(days = 0), LocalDateTime.of(2026, 10, 15, 12, 0)))
    }

    @Test
    fun monthlyClampsToShortMonths() {
        val r = base.copy(timing = Timing.FIXED, at = 9 * 60, repeat = Repeat.MONTHLY, monthDay = 31)
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), Schedule.next(r, LocalDateTime.of(2026, 11, 1, 0, 0)))
    }

    @Test
    fun yearlyFindsNextYear() {
        val r = base.copy(timing = Timing.FIXED, at = 10 * 60, repeat = Repeat.YEARLY, month = 3, monthDay = 14)
        assertEquals(LocalDateTime.of(2027, 3, 14, 10, 0), Schedule.next(r, LocalDateTime.of(2026, 10, 15, 0, 0)))
    }

    @Test
    fun leapDayStillFound() {
        val r = base.copy(timing = Timing.FIXED, at = 10 * 60, repeat = Repeat.YEARLY, month = 2, monthDay = 29)
        assertNotNull(Schedule.next(r, LocalDateTime.of(2026, 10, 15, 0, 0)))
    }
}

class LockTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDateTime.of(2026, 1, 31, 12, 0).toInstant(zone).toEpochMilli()

    @Test
    fun yearsFollowTheCalendar() {
        assertEquals(LocalDateTime.of(2056, 1, 31, 12, 0).toInstant(zone).toEpochMilli(), Lock.openAt(start, 30 * 365, zone))
    }

    @Test
    fun aMonthIsACalendarMonth() {
        assertEquals(LocalDateTime.of(2026, 2, 28, 12, 0).toInstant(zone).toEpochMilli(), Lock.openAt(start, 30, zone))
    }

    @Test
    fun customMonthsFollowTheCalendar() {
        assertEquals(LocalDateTime.of(2026, 4, 30, 12, 0).toInstant(zone).toEpochMilli(), Lock.openAt(start, 90, zone))
        assertEquals(LocalDateTime.of(2026, 2, 10, 12, 0).toInstant(zone).toEpochMilli(), Lock.openAt(start, 10, zone))
    }

    @Test
    fun ladderGrows() {
        val day = 86_400_000L
        assertEquals(7, Lock.ladderDays(null, start))
        assertEquals(7, Lock.ladderDays(start, start + 10 * day))
        assertEquals(30, Lock.ladderDays(start, start + 40 * day))
        assertEquals(365, Lock.ladderDays(start, start + 400 * day))
    }
}

class TimeGuardTest {
    private val min = 60_000L
    private val last = TimeGuard.Trusted(wall = 1_000_000_000L, elapsed = 50 * min, boot = 7)

    @Test
    fun steadyClockIsTrusted() {
        assertTrue(TimeGuard.consistent(last, TimeGuard.Reading(last.wall + 120 * min, last.elapsed + 120 * min, 7)))
    }

    @Test
    fun jumpForwardInSameBootIsCaught() {
        assertFalse(TimeGuard.consistent(last, TimeGuard.Reading(last.wall + 365L * 24 * 60 * min, last.elapsed + 5 * min, 7)))
    }

    @Test
    fun clockBackAfterRebootIsCaught() {
        assertFalse(TimeGuard.consistent(last, TimeGuard.Reading(last.wall - 600 * min, 2 * min, 8)))
    }

    @Test
    fun rebootWithSaneClockIsTrusted() {
        assertTrue(TimeGuard.consistent(last, TimeGuard.Reading(last.wall + 300 * min, 2 * min, 8)))
    }

    @Test
    fun firstRunIsTrusted() {
        assertTrue(TimeGuard.consistent(TimeGuard.Trusted(0, 0, -1), TimeGuard.Reading(5, 5, 1)))
    }
}
