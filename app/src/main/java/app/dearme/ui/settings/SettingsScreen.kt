package app.dearme.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.dearme.BuildConfig
import app.dearme.R
import app.dearme.container
import app.dearme.data.Backup
import app.dearme.data.Settings
import app.dearme.data.ThemeMode
import app.dearme.notify.Alarms
import app.dearme.ui.DearData
import app.dearme.ui.Language
import app.dearme.ui.Words
import app.dearme.ui.components.Chip
import app.dearme.ui.components.ConfirmDialog
import app.dearme.ui.components.SectionTitle
import app.dearme.ui.components.TopBar
import app.dearme.ui.components.UCard
import app.dearme.ui.components.UTextField
import app.dearme.ui.ctx
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(data: DearData, onBack: () -> Unit) {
    val context = ctx()
    val container = context.container
    val settings = data.settings
    fun update(change: (Settings) -> Settings) {
        container.scope.launch { container.settings.update(change) }
    }
    var canNotify by remember { mutableStateOf(Alarms.canNotify(context)) }
    var canExact by remember { mutableStateOf(Alarms.canExact(context)) }
    // Re-read on resume so the permission cards disappear after granting in system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canNotify = Alarms.canNotify(context)
        canExact = Alarms.canExact(context)
        container.scope.launch { container.changed() }
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
        container.scope.launch { container.changed() }
    }
    var name by remember { mutableStateOf(settings.name) }
    LaunchedEffect(name) {
        delay(400)
        if (name.trim() != settings.name) container.settings.update { it.copy(name = name.trim()) }
    }

    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val exported = stringResource(R.string.backup_done)
    val failed = stringResource(R.string.backup_failed)
    val restored = stringResource(R.string.backup_restored)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        container.scope.launch {
            val ok = runCatching {
                val snapshot = container.db.dao().snapshot()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { Backup.write(context, snapshot, it) } }
                container.settings.update { it.copy(lastBackup = System.currentTimeMillis()) }
            }.isSuccess
            withContext(Dispatchers.Main) {
                busy = false
                Toast.makeText(context, if (ok) exported else failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(stringResource(R.string.settings), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            if (!canNotify) {
                Spacer(Modifier.height(8.dp))
                UCard(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
                }) {
                    Text(stringResource(R.string.perm_title), style = UType.Strong.copy(color = U.c.accent))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.perm_text), style = UType.Muted)
                }
            }
            if (!canExact && Build.VERSION.SDK_INT >= 31) {
                Spacer(Modifier.height(8.dp))
                UCard(onClick = {
                    runCatching {
                        context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null)))
                    }
                }) {
                    Text(stringResource(R.string.exact_title), style = UType.Strong.copy(color = U.c.accent))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.exact_text), style = UType.Muted)
                }
            }

            SectionTitle(stringResource(R.string.set_name))
            UTextField(name, { name = it.take(30) }, placeholder = stringResource(R.string.onb_name_hint))
            Text(stringResource(R.string.set_name_text), style = UType.Small, modifier = Modifier.padding(top = 6.dp, start = 4.dp))

            SectionTitle(stringResource(R.string.backup))
            UCard {
                Text(stringResource(R.string.backup_text), style = UType.Muted)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (settings.lastBackup > 0) stringResource(R.string.backup_last, Words.dateTime(context, settings.lastBackup))
                    else stringResource(R.string.backup_never),
                    style = UType.Small.copy(color = if (settings.lastBackup > 0) U.c.faint else U.c.accent),
                )
                Spacer(Modifier.height(8.dp))
                if (busy) CircularProgressIndicator(color = U.c.accent, modifier = Modifier.padding(8.dp))
                else Row {
                    TextButton(onClick = { export.launch("DearMe-${LocalDate.now()}.dearme") }) {
                        Text(stringResource(R.string.backup_export), color = U.c.accent, style = UType.Button)
                    }
                    TextButton(onClick = { import.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.backup_import), color = U.c.accent, style = UType.Button)
                    }
                }
            }

            SectionTitle(stringResource(R.string.set_reliability))
            UCard(onClick = {
                context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) {
                Text(stringResource(R.string.autostart_title), style = UType.Strong)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.autostart_text), style = UType.Muted)
            }
            Spacer(Modifier.height(10.dp))
            UCard {
                Text(stringResource(R.string.clock_title), style = UType.Strong)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(if (settings.clockSuspect) R.string.clock_suspect_text else R.string.clock_ok_text), style = UType.Muted)
            }

            SectionTitle(stringResource(R.string.set_feel))
            UCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_haptics), style = UType.Strong)
                        Text(stringResource(R.string.set_haptics_text), style = UType.Muted)
                    }
                    Switch(settings.haptics, { v -> update { it.copy(haptics = v) } }, colors = SwitchDefaults.colors(checkedTrackColor = U.c.accent))
                }
            }

            SectionTitle(stringResource(R.string.set_appearance))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.theme_system), settings.theme == ThemeMode.SYSTEM, { update { it.copy(theme = ThemeMode.SYSTEM) } })
                Chip(stringResource(R.string.theme_light), settings.theme == ThemeMode.LIGHT, { update { it.copy(theme = ThemeMode.LIGHT) } })
                Chip(stringResource(R.string.theme_dark), settings.theme == ThemeMode.DARK, { update { it.copy(theme = ThemeMode.DARK) } })
            }

            SectionTitle(stringResource(R.string.language))
            val current = Language.current()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.lang_system), current == Language.SYSTEM, { Language.set(Language.SYSTEM) })
                Chip("English", current == Language.EN, { Language.set(Language.EN) })
                Chip("Русский", current == Language.RU, { Language.set(Language.RU) })
            }

            SectionTitle(stringResource(R.string.about))
            UCard {
                Text(stringResource(R.string.about_text), style = UType.Sub)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = U.c.line)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = UType.Small)
            }
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.backup_import_title),
            text = stringResource(R.string.backup_import_text),
            confirm = stringResource(R.string.backup_import),
            danger = true,
            onConfirm = {
                busy = true
                container.scope.launch {
                    val ok = runCatching {
                        val (snapshot, staging) = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)!!.use { Backup.read(context, it) }
                        }
                        // Old alarms point at ritual ids that may not exist after the restore.
                        container.db.dao().rituals().forEach { Alarms.cancelRitual(context, it.id) }
                        container.db.dao().replaceAll(snapshot)
                        withContext(Dispatchers.IO) { Backup.commitMedia(context, staging) }
                        container.changed()
                    }.isSuccess
                    withContext(Dispatchers.Main) {
                        busy = false
                        Toast.makeText(context, if (ok) restored else failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}
