package app.dearme.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.dearme.MainActivity
import app.dearme.R
import app.dearme.container
import app.dearme.data.Call
import app.dearme.data.Ritual
import app.dearme.domain.Schedule
import app.dearme.ui.Words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Everything that happens while the app is closed: rituals calling and capsules opening. */
object Alarms {
    private const val CHANNEL_MOMENTS = "moments_v1"
    private const val CHANNEL_LETTERS = "letters_v1"
    const val EXTRA_ROUTE = "route"
    private const val EXTRA_RITUAL = "ritual"

    private const val ACTION_RITUAL = "app.dearme.notify.RITUAL"
    private const val ACTION_OPEN = "app.dearme.notify.OPEN"

    private const val ID_OPENED = 1
    private const val ID_RITUAL_BASE = 1000

    /** The two minutes of a timed ritual. */
    const val WINDOW_MS = 2 * 60_000L

    /** A planned call missed by less than this (the phone was asleep, the app was killed) still rings. */
    private const val GRACE_MS = 10 * 60_000L

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_MOMENTS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_MOMENTS, context.getString(R.string.channel_moments), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_moments_desc)
                }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_LETTERS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LETTERS, context.getString(R.string.channel_letters), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.channel_letters_desc)
                }
            )
        }
    }

    private fun ritualPi(context: Context, id: Long) = PendingIntent.getBroadcast(
        context, (ID_RITUAL_BASE + id).toInt(),
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_RITUAL).putExtra(EXTRA_RITUAL, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openPi(context: Context) = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_OPEN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun cancelRitual(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(ritualPi(context, id))
        clearCallNotification(context, id)
    }

    fun clearCallNotification(context: Context, ritualId: Long) =
        NotificationManagerCompat.from(context).cancel((ID_RITUAL_BASE + ritualId).toInt())

    /** Plans every ritual's next call and the next capsule opening. Safe to call any time. */
    suspend fun reschedule(context: Context) {
        val dao = context.container.db.dao()
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        for (r in dao.rituals()) {
            if (!r.enabled) {
                am.cancel(ritualPi(context, r.id))
                if (r.nextAt != 0L) dao.updateRitual(r.copy(nextAt = 0))
                continue
            }
            var at = r.nextAt
            if (at == 0L || at < now - GRACE_MS) {
                at = nextFor(r, now, zone) ?: 0L
                dao.updateRitual(r.copy(nextAt = at))
            }
            if (at == 0L) {
                am.cancel(ritualPi(context, r.id))
                continue
            }
            // A call missed within the grace period rings right away instead of being skipped.
            val ringAt = maxOf(at, now + 1000)
            val pi = ritualPi(context, r.id)
            // The two minutes are counted from when the call actually rings, so a late alarm costs nothing.
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ringAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ringAt, pi)
        }

        val opening = dao.nextOpening()
        if (opening == null) am.cancel(openPi(context))
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(opening, now + 1000), openPi(context))
    }

    fun nextFor(r: Ritual, after: Long, zone: ZoneId = ZoneId.systemDefault()): Long? =
        Schedule.next(r, LocalDateTime.ofInstant(Instant.ofEpochMilli(after), zone))?.atZone(zone)?.toInstant()?.toEpochMilli()

    private suspend fun fireRitual(context: Context, id: Long) {
        val dao = context.container.db.dao()
        val r = dao.ritual(id) ?: return
        val now = System.currentTimeMillis()
        // Stale or early broadcasts (an alarm left from an edited ritual) are ignored; reschedule fixes the plan.
        if (!r.enabled || r.nextAt == 0L || r.nextAt > now + 60_000) return
        val deadline = if (r.timer) now + WINDOW_MS else now
        val callId = dao.insertCall(Call(ritualId = r.id, firedAt = now, deadline = deadline))
        dao.updateRitual(r.copy(nextAt = nextFor(r, now + 60_000) ?: 0L))

        if (!canNotify(context)) return
        val title = if (r.timer) context.getString(R.string.notif_moment_title) else r.name
        val text = if (r.timer) context.getString(R.string.notif_moment_text, r.name) else context.getString(R.string.notif_ritual_text)
        val b = NotificationCompat.Builder(context, CHANNEL_MOMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open(context, (ID_RITUAL_BASE + r.id).toInt(), "call/$callId"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (r.timer) b.setWhen(deadline).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        try {
            NotificationManagerCompat.from(context).notify((ID_RITUAL_BASE + r.id).toInt(), b.build())
        } catch (_: SecurityException) {
        }
    }

    private suspend fun fireOpen(context: Context) {
        val dao = context.container.db.dao()
        val now = System.currentTimeMillis()
        val due = dao.dueUnnotified(now)
        if (due.isEmpty()) return
        due.forEach { dao.updateCapsule(it.copy(notified = true)) }
        if (!canNotify(context)) return
        val first = due.first()
        val title = if (due.size == 1) context.getString(R.string.notif_opened_title, Words.year(first.createdAt))
        else context.resources.getQuantityString(R.plurals.notif_opened_many, due.size, due.size)
        val text = if (due.size == 1) context.getString(R.string.notif_opened_text, Words.ago(context, first.createdAt, now))
        else context.getString(R.string.notif_opened_many_text)
        val route = if (due.size == 1) "capsule/${first.id}" else "vault"
        val n = NotificationCompat.Builder(context, CHANNEL_LETTERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, 100, route))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_OPENED, n)
        } catch (_: SecurityException) {
        }
    }

    private fun open(context: Context, code: Int, route: String) = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_ROUTE, route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun fire(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RITUAL -> fireRitual(context, intent.getLongExtra(EXTRA_RITUAL, -1))
            ACTION_OPEN -> fireOpen(context)
        }
    }

    internal val actions = setOf(ACTION_RITUAL, ACTION_OPEN)
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in Alarms.actions) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                Alarms.fire(context, intent)
                Alarms.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val pending = goAsync()
        receiverScope.launch {
            try {
                context.container.clockAndAlarms()
            } finally {
                pending.finish()
            }
        }
    }
}
