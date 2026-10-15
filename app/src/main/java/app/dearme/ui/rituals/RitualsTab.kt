package app.dearme.ui.rituals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dearme.R
import app.dearme.container
import app.dearme.data.Ritual
import app.dearme.data.Timing
import app.dearme.ui.DearData
import app.dearme.ui.Go
import app.dearme.ui.Words
import app.dearme.ui.components.IconDisc
import app.dearme.ui.components.SectionTitle
import app.dearme.ui.components.UCard
import app.dearme.ui.ctx
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType

@Composable
fun RitualsTab(data: DearData, go: Go) {
    val context = ctx()
    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        item {
            Text(stringResource(R.string.tab_rituals), style = UType.Display, modifier = Modifier.padding(top = 18.dp))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.rituals_sub), style = UType.Sub)
            Spacer(Modifier.height(16.dp))
        }
        items(data.rituals, key = { it.id }) { r ->
            RitualCard(r, data) { go.ritual(r.id) }
            Spacer(Modifier.height(10.dp))
        }
        item {
            SectionTitle(stringResource(R.string.add_ritual))
            Templates.all.forEach { t ->
                UCard(onClick = { go.ritual(0, t.key) }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconDisc(templateIcon(t.key), 40.dp, U.c.sky, U.c.skySoft)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Templates.name(context, t.key), style = UType.Strong)
                            Text(Templates.description(context, t.key), style = UType.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun templateIcon(key: String): ImageVector = when (key) {
    Templates.RANDOM -> Icons.Rounded.Casino
    Templates.SUNDAY -> Icons.Rounded.EditNote
    Templates.MONTHLY -> Icons.Rounded.CalendarMonth
    Templates.BIRTHDAY -> Icons.Rounded.Cake
    Templates.CUSTOM -> Icons.Rounded.Tune
    else -> Icons.Rounded.Add
}

@Composable
private fun RitualCard(r: Ritual, data: DearData, onClick: () -> Unit) {
    val context = ctx()
    val container = context.container
    UCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = UType.Strong)
                Spacer(Modifier.height(2.dp))
                Text(Words.ritualWhen(context, r), style = UType.Muted)
                Text(Words.ritualWhat(context, r), style = UType.Muted)
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = r.enabled,
                onCheckedChange = { on -> container.write { dao().updateRitual(r.copy(enabled = on, nextAt = 0)) } },
                colors = SwitchDefaults.colors(checkedTrackColor = U.c.accent, uncheckedBorderColor = U.c.line, uncheckedTrackColor = U.c.surface2),
            )
        }
        if (r.enabled && r.nextAt > 0) {
            Spacer(Modifier.height(8.dp))
            // A random call stays a surprise: only the day is shown.
            val text = if (r.timing == Timing.RANDOM) stringResource(R.string.next_call_day, day(r.nextAt, data.now))
            else stringResource(R.string.next_call_at, day(r.nextAt, data.now), Words.clock(context, r.nextAt))
            Text(text, style = UType.Small.copy(color = U.c.accent))
        }
    }
}

@Composable
private fun day(at: Long, now: Long): String {
    val context = ctx()
    val zone = java.time.ZoneId.systemDefault()
    val d = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (d) {
        today -> stringResource(R.string.today_lower)
        today.plusDays(1) -> stringResource(R.string.tomorrow_lower)
        else -> Words.mediumDate(context, at)
    }
}
