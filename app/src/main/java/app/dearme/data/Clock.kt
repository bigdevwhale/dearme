package app.dearme.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings as SystemSettings
import app.dearme.domain.TimeGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** The phone's clock, checked against itself (and the network when there is one) before a seal is broken. */
object Clock {
    private fun reading(context: Context) = TimeGuard.Reading(
        wall = System.currentTimeMillis(),
        elapsed = SystemClock.elapsedRealtime(),
        boot = runCatching { SystemSettings.Global.getInt(context.contentResolver, SystemSettings.Global.BOOT_COUNT) }.getOrDefault(-1),
    )

    /** Records the clock when it looks honest, flags it when it doesn't. Returns true when it can be trusted. */
    suspend fun check(context: Context, store: SettingsStore): Boolean {
        val r = reading(context)
        var ok = false
        store.update { s ->
            val last = TimeGuard.Trusted(s.guardWall, s.guardElapsed, s.guardBoot)
            ok = TimeGuard.consistent(last, r)
            if (ok) s.copy(guardWall = r.wall, guardElapsed = r.elapsed, guardBoot = r.boot, clockSuspect = false)
            else s.copy(clockSuspect = true)
        }
        return ok
    }

    /**
     * Before a capsule opens: the local check, then the network clock if it answers within a few seconds.
     * A network time that agrees clears a false alarm; one that disagrees keeps the capsule shut.
     */
    suspend fun verifyForOpening(context: Context, store: SettingsStore): Boolean {
        val local = check(context, store)
        val net = networkTime() ?: return local
        val r = reading(context)
        val agrees = TimeGuard.networkAgrees(net, r.wall)
        store.update {
            if (agrees) it.copy(guardWall = r.wall, guardElapsed = r.elapsed, guardBoot = r.boot, clockSuspect = false)
            else it.copy(clockSuspect = true)
        }
        return agrees
    }

    private suspend fun networkTime(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://www.gstatic.com/generate_204").openConnection() as HttpURLConnection
            c.connectTimeout = 3000
            c.readTimeout = 3000
            c.requestMethod = "HEAD"
            c.useCaches = false
            try {
                c.connect()
                c.date.takeIf { it > 0 }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}
