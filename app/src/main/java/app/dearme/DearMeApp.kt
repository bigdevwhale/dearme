package app.dearme

import android.app.Application
import android.content.Context
import app.dearme.data.Clock
import app.dearme.data.DearDatabase
import app.dearme.data.DemoSeed
import app.dearme.data.Media
import app.dearme.data.SettingsStore
import app.dearme.notify.Alarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {
    val db = DearDatabase.create(context)
    val settings = SettingsStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fire-and-forget so a write survives the screen being closed right after tapping save. */
    fun write(block: suspend DearDatabase.() -> Unit) {
        scope.launch {
            db.block()
            changed()
        }
    }

    /** Re-plan rituals and openings after any change. */
    suspend fun changed() {
        Alarms.reschedule(context)
    }

    /** On start, boot and clock changes: note the clock, then re-arm alarms. */
    suspend fun clockAndAlarms() {
        Clock.check(context, settings)
        changed()
    }
}

class DearMeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Alarms.ensureChannels(this)
        // Re-arm on every start: OEM task killers (HyperOS & co.) silently drop alarms.
        container.scope.launch {
            if (BuildConfig.DEMO) DemoSeed.seedIfEmpty(this@DearMeApp, container.db, container.settings)
            container.clockAndAlarms()
            sweepOrphans()
        }
    }

    /** Media left behind by drafts that were never sealed. */
    private suspend fun sweepOrphans() {
        val s = container.db.dao().snapshot()
        val keep = (s.photos.flatMap { it.files } + s.capsules.mapNotNull { it.voice }).toSet()
        Media.sweep(this, keep, olderThan = System.currentTimeMillis() - 24 * 3_600_000L)
    }
}

val Context.container: AppContainer get() = (applicationContext as DearMeApp).container
