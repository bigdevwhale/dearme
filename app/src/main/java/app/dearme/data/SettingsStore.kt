package app.dearme.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboarded: Boolean = false,
    /** "Dear {name}," on letters; empty is fine. */
    val name: String = "",
    val haptics: Boolean = true,
    /** Last clock reading that was trusted: wall time, time since boot, and boot number. */
    val guardWall: Long = 0,
    val guardElapsed: Long = 0,
    val guardBoot: Int = -1,
    /** The clock looked changed by hand; capsules stay shut until it looks right again. */
    val clockSuspect: Boolean = false,
    /** When the last backup was exported; 0 = never. */
    val lastBackup: Long = 0,
    /** When the "back up your capsules" nudge was last shown. */
    val backupNudge: Long = 0,
)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        theme = this[THEME]?.let { t -> runCatching { ThemeMode.valueOf(t) }.getOrNull() } ?: ThemeMode.SYSTEM,
        onboarded = this[ONBOARDED] ?: false,
        name = this[NAME] ?: "",
        haptics = this[HAPTICS] ?: true,
        guardWall = this[GUARD_WALL] ?: 0,
        guardElapsed = this[GUARD_ELAPSED] ?: 0,
        guardBoot = this[GUARD_BOOT] ?: -1,
        clockSuspect = this[CLOCK_SUSPECT] ?: false,
        lastBackup = this[LAST_BACKUP] ?: 0,
        backupNudge = this[BACKUP_NUDGE] ?: 0,
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[THEME] = s.theme.name
            it[ONBOARDED] = s.onboarded
            it[NAME] = s.name
            it[HAPTICS] = s.haptics
            it[GUARD_WALL] = s.guardWall
            it[GUARD_ELAPSED] = s.guardElapsed
            it[GUARD_BOOT] = s.guardBoot
            it[CLOCK_SUSPECT] = s.clockSuspect
            it[LAST_BACKUP] = s.lastBackup
            it[BACKUP_NUDGE] = s.backupNudge
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NAME = stringPreferencesKey("name")
        val HAPTICS = booleanPreferencesKey("haptics")
        val GUARD_WALL = longPreferencesKey("guard_wall")
        val GUARD_ELAPSED = longPreferencesKey("guard_elapsed")
        val GUARD_BOOT = intPreferencesKey("guard_boot")
        val CLOCK_SUSPECT = booleanPreferencesKey("clock_suspect")
        val LAST_BACKUP = longPreferencesKey("last_backup")
        val BACKUP_NUDGE = longPreferencesKey("backup_nudge")
    }
}
