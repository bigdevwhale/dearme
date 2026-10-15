package app.dearme.ui.rituals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dearme.R
import app.dearme.container
import app.dearme.data.Call
import app.dearme.data.PhotoMode
import app.dearme.data.Repeat
import app.dearme.data.Ritual
import app.dearme.data.TextMode
import app.dearme.data.Timing
import app.dearme.domain.Schedule
import app.dearme.notify.Alarms
import app.dearme.ui.DearData
import app.dearme.ui.Go
import app.dearme.ui.Words
import app.dearme.ui.components.Chip
import app.dearme.ui.components.ConfirmDialog
import app.dearme.ui.components.InfoRow
import app.dearme.ui.components.PrimaryButton
import app.dearme.ui.components.SectionTitle
import app.dearme.ui.components.Segmented
import app.dearme.ui.components.SoftButton
import app.dearme.ui.components.TimeDialog
import app.dearme.ui.components.TopBar
import app.dearme.ui.components.UCard
import app.dearme.ui.components.UTextField
import app.dearme.ui.ctx
import app.dearme.ui.locale
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private enum class TimeField { FROM, TO, AT }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RitualScreen(id: Long, template: String, data: DearData, go: Go) {
    val context = ctx()
    val container = context.container
    val loc = locale()
    var r by remember { mutableStateOf<Ritual?>(null) }
    LaunchedEffect(id) {
        if (r == null) r = if (id > 0) data.ritualsById[id] ?: container.db.dao().ritual(id) else Templates.make(context, template.ifBlank { Templates.CUSTOM })
    }
    var timeField by remember { mutableStateOf<TimeField?>(null) }
    var pickDay by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val cur = r
    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(stringResource(if (id > 0) R.string.edit_ritual else R.string.new_ritual), onBack = { go.back() }) {
            if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = U.c.faint) }
        }
        if (cur == null) return@Column
        fun set(change: (Ritual) -> Ritual) { r = change(cur) }

        val noContent = cur.photo == PhotoMode.NONE && cur.textMode == TextMode.NONE && !cur.voice
        val noDays = cur.repeat == Repeat.WEEKLY && (cur.days and Ritual.ALL_DAYS) == 0
        val valid = cur.name.isNotBlank() && !noContent && !noDays

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            UTextField(cur.name, { v -> set { it.copy(name = v.take(40)) } }, placeholder = stringResource(R.string.ritual_name))

            SectionTitle(stringResource(R.string.when_title))
            Segmented(listOf(Timing.RANDOM, Timing.FIXED), cur.timing, { stringResource(if (it == Timing.RANDOM) R.string.timing_random else R.string.timing_fixed) }, { t -> set { it.copy(timing = t) } })
            Spacer(Modifier.height(8.dp))
            UCard {
                if (cur.timing == Timing.RANDOM) {
                    val (ws, we) = Schedule.window(cur)
                    InfoRow(Icons.Rounded.Schedule, stringResource(R.string.window_from), Words.clockOf(context, ws), onClick = { timeField = TimeField.FROM })
                    InfoRow(Icons.Rounded.Schedule, stringResource(R.string.window_to), Words.clockOf(context, we), onClick = { timeField = TimeField.TO })
                    Text(stringResource(R.string.random_hint), style = UType.Muted)
                } else {
                    InfoRow(Icons.Rounded.Schedule, stringResource(R.string.time), Words.clockOf(context, cur.at), onClick = { timeField = TimeField.AT })
                }
            }

            SectionTitle(stringResource(R.string.repeat_title))
            Segmented(
                listOf(Repeat.WEEKLY, Repeat.MONTHLY, Repeat.YEARLY), cur.repeat,
                { stringResource(when (it) { Repeat.WEEKLY -> R.string.repeat_days; Repeat.MONTHLY -> R.string.repeat_monthly; Repeat.YEARLY -> R.string.repeat_yearly }) },
                { v -> set { it.copy(repeat = v) } },
            )
            Spacer(Modifier.height(10.dp))
            when (cur.repeat) {
                Repeat.WEEKLY -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val bit = 1 shl (d.value - 1)
                        val on = (cur.days and bit) != 0
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(U.RSm)
                                .background(if (on) U.c.accent else U.c.surface)
                                .border(1.dp, if (on) U.c.accent else U.c.line, U.RSm)
                                .clickable { set { it.copy(days = it.days xor bit) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(d.getDisplayName(TextStyle.NARROW_STANDALONE, loc), style = UType.Button.copy(color = if (on) U.c.onAccent else U.c.muted))
                        }
                    }
                }
                Repeat.MONTHLY -> UCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.day_of_month), style = UType.Body, modifier = Modifier.weight(1f))
                        Stepper(cur.monthDay, 1..31) { v -> set { it.copy(monthDay = v) } }
                    }
                    if (cur.monthDay > 28) Text(stringResource(R.string.short_months_hint), style = UType.Muted)
                }
                Repeat.YEARLY -> UCard {
                    InfoRow(
                        Icons.Rounded.Schedule, stringResource(R.string.every_year_on),
                        DateTimeFormatter.ofPattern("d MMMM", loc).format(LocalDate.of(2024, cur.month, cur.monthDay.coerceIn(1, java.time.YearMonth.of(2024, cur.month).lengthOfMonth()))),
                        onClick = { pickDay = true },
                    )
                }
            }
            if (noDays) Text(stringResource(R.string.pick_a_day), style = UType.Muted.copy(color = U.c.danger), modifier = Modifier.padding(top = 6.dp))

            SectionTitle(stringResource(R.string.what_goes_in))
            Text(stringResource(R.string.photo), style = UType.Muted)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PhotoMode.NONE, PhotoMode.BACK, PhotoMode.FRONT, PhotoMode.DUAL, PhotoMode.ANY).forEach { m ->
                    Chip(Words.photoMode(context, m) ?: stringResource(R.string.none), cur.photo == m, { set { it.copy(photo = m) } })
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.text), style = UType.Muted)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TextMode.NONE, TextMode.LINE, TextMode.LETTER).forEach { m ->
                    Chip(
                        stringResource(when (m) { TextMode.NONE -> R.string.none; TextMode.LINE -> R.string.text_line; TextMode.LETTER -> R.string.text_letter }),
                        cur.textMode == m, { set { it.copy(textMode = m) } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            UCard {
                SwitchRow(stringResource(R.string.voice_note), stringResource(R.string.voice_note_desc), cur.voice) { v -> set { it.copy(voice = v) } }
                SwitchRow(stringResource(R.string.question), stringResource(R.string.question_desc), cur.usePrompt) { v -> set { it.copy(usePrompt = v) } }
                if (cur.usePrompt) {
                    UTextField(cur.prompt, { v -> set { it.copy(prompt = v.take(160)) } }, placeholder = stringResource(R.string.question_placeholder), minLines = 2)
                    Spacer(Modifier.height(6.dp))
                }
                SwitchRow(stringResource(R.string.two_minutes), stringResource(R.string.two_minutes_desc), cur.timer) { v -> set { it.copy(timer = v) } }
            }
            if (noContent) Text(stringResource(R.string.needs_content), style = UType.Muted.copy(color = U.c.danger), modifier = Modifier.padding(top = 6.dp))

            SectionTitle(stringResource(R.string.opens_after_title))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Words.ritualLocks.forEach { d -> Chip(Words.lock(context, d), cur.lockDays == d, { set { it.copy(lockDays = d) } }) }
            }
            if (cur.lockDays == Ritual.LADDER) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ladder_explain), style = UType.Muted)
            }
            Spacer(Modifier.height(22.dp))
            SoftButton(
                stringResource(R.string.try_now),
                {
                    if (!valid) return@SoftButton
                    container.scope.launch {
                        val dao = container.db.dao()
                        val saved = if (cur.id == 0L) cur.copy(id = dao.insertRitual(cur)) else cur.also { dao.updateRitual(it.copy(nextAt = 0)) }
                        val now = System.currentTimeMillis()
                        val call = dao.insertCall(Call(ritualId = saved.id, firedAt = now, deadline = if (saved.timer) now + Alarms.WINDOW_MS else now))
                        container.changed()
                        withContext(Dispatchers.Main) {
                            go.back()
                            go.call(call)
                        }
                    }
                },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow,
            )
            Text(stringResource(R.string.try_now_desc), style = UType.Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(
                stringResource(R.string.save),
                {
                    val toSave = cur.copy(name = cur.name.trim(), nextAt = 0)
                    container.write { if (toSave.id == 0L) dao().insertRitual(toSave) else dao().updateRitual(toSave) }
                    go.back()
                },
                Modifier.fillMaxWidth(), enabled = valid,
            )
        }

        timeField?.let { f ->
            val initial = when (f) { TimeField.FROM -> cur.windowStart; TimeField.TO -> cur.windowEnd; TimeField.AT -> cur.at }
            TimeDialog(initial % (24 * 60), onDismiss = { timeField = null }) { m ->
                set {
                    when (f) {
                        TimeField.FROM -> it.copy(windowStart = m, windowEnd = maxOf(it.windowEnd, m + Schedule.MIN_WINDOW).coerceAtMost(24 * 60))
                        TimeField.TO -> it.copy(windowEnd = if (m == 0) 24 * 60 else m, windowStart = minOf(it.windowStart, (if (m == 0) 24 * 60 else m) - Schedule.MIN_WINDOW).coerceAtLeast(0))
                        TimeField.AT -> it.copy(at = m)
                    }
                }
            }
        }
        if (pickDay) {
            val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().year.let { y ->
                val ym = java.time.YearMonth.of(y, cur.month)
                ym.atDay(cur.monthDay.coerceIn(1, ym.lengthOfMonth())).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            })
            DatePickerDialog(
                onDismissRequest = { pickDay = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val d = java.time.Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            set { it.copy(month = d.monthValue, monthDay = d.dayOfMonth) }
                        }
                        pickDay = false
                    }) { Text(stringResource(R.string.ok), color = U.c.accent) }
                },
                dismissButton = { TextButton(onClick = { pickDay = false }) { Text(stringResource(R.string.cancel), color = U.c.muted) } },
                colors = DatePickerDefaults.colors(containerColor = U.c.bg),
            ) { DatePicker(state, showModeToggle = false) }
        }
        if (confirmDelete) ConfirmDialog(
            title = stringResource(R.string.delete_ritual_title),
            text = stringResource(R.string.delete_ritual_text),
            confirm = stringResource(R.string.delete),
            danger = true,
            onConfirm = {
                Alarms.cancelRitual(context, cur.id)
                container.write { dao().deleteRitual(cur) }
                go.back()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Strong)
            Text(desc, style = UType.Muted)
        }
        Spacer(Modifier.width(10.dp))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = U.c.accent, uncheckedBorderColor = U.c.line, uncheckedTrackColor = U.c.surface2))
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Rounded.Remove, value > range.first) { onChange(value - 1) }
        Text(value.toString(), style = UType.Number, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        StepButton(Icons.Rounded.Add, value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (enabled) U.c.accentSoft else U.c.surface2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) U.c.accent else U.c.faint) }
}
