package app.dearme.domain

import kotlin.math.abs

/**
 * Notices when the phone's clock is moved by hand, so a capsule can't be opened early by changing the date.
 * Without a server this is a speed bump, not a vault: it compares wall time with time since boot.
 */
object TimeGuard {
    /** Drift tolerated between wall time and time since boot (network time corrections, sleep). */
    const val TOLERANCE = 15 * 60_000L

    /** The network clock may differ from the phone's by this much and still count as right. */
    const val NETWORK_TOLERANCE = 60 * 60_000L

    data class Reading(val wall: Long, val elapsed: Long, val boot: Int)

    data class Trusted(val wall: Long, val elapsed: Long, val boot: Int)

    /** True when [now] looks like an honest continuation of [last]. */
    fun consistent(last: Trusted?, now: Reading): Boolean {
        if (last == null || last.boot == -1 || last.wall == 0L) return true
        return if (now.boot != -1 && now.boot == last.boot) {
            abs((now.wall - last.wall) - (now.elapsed - last.elapsed)) <= TOLERANCE
        } else {
            // After a reboot only a clock that went backwards can be told apart.
            now.wall >= last.wall - TOLERANCE
        }
    }

    fun networkAgrees(network: Long, wall: Long): Boolean = abs(network - wall) <= NETWORK_TOLERANCE
}
