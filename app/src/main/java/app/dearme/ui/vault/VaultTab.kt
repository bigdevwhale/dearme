package app.dearme.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dearme.R
import app.dearme.container
import app.dearme.data.Call
import app.dearme.data.Capsule
import app.dearme.data.Kind
import app.dearme.ui.DearData
import app.dearme.ui.Go
import app.dearme.ui.Words
import app.dearme.ui.components.Chip
import app.dearme.ui.components.Envelope
import app.dearme.ui.components.IconDisc
import app.dearme.ui.components.MediaImage
import app.dearme.ui.components.PrimaryButton
import app.dearme.ui.components.SectionTitle
import app.dearme.ui.components.SoftButton
import app.dearme.ui.components.UCard
import app.dearme.ui.ctx
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import kotlinx.coroutines.launch

private enum class Filter { ALL, LETTERS, MOMENTS }

@Composable
fun VaultTab(data: DearData, go: Go) {
    val context = ctx()
    val container = context.container
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val s = data.settings
    val sealed = data.sealed.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.LETTERS -> it.kind == Kind.LETTER
            Filter.MOMENTS -> it.kind == Kind.MOMENT
        }
    }
    val showBackupNudge = data.sealed.size >= 3 && data.now - s.lastBackup > 30L * 86_400_000 && data.now - s.backupNudge > 7L * 86_400_000

    LazyColumn(Modifier.statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val name = s.name.trim()
                    Text(if (name.isEmpty()) stringResource(R.string.dear_me) else stringResource(R.string.dear_name, name), style = UType.Hand.copy(fontSize = 38.sp))
                    Text(summary(data), style = UType.Sub)
                }
                IconButton(onClick = { go.settings() }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings), tint = U.c.muted) }
            }
        }

        if (s.clockSuspect) item {
            Spacer(Modifier.height(16.dp))
            Notice(Icons.Rounded.Warning, stringResource(R.string.clock_suspect_title), stringResource(R.string.clock_suspect_text))
        }

        if (data.waiting.isNotEmpty()) item { Spacer(Modifier.height(16.dp)) }
        items(data.waiting, key = { "call" + it.id }) { call ->
            WaitingCall(call, data, onOpen = { go.call(call.id) }, onDismiss = {
                container.write { dao().updateCall(call.copy(dismissed = true)) }
            })
            Spacer(Modifier.height(10.dp))
        }

        if (data.ready.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.ready_to_open)) }
            items(data.ready, key = { "ready" + it.id }) { c ->
                ReadyCard(c, data) { go.capsule(c.id) }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (data.capsules.isEmpty()) item { Empty(go) }
        else data.sealed.firstOrNull()?.let { next -> item { NextHero(next, data) { go.capsule(next.id) } } }

        if (showBackupNudge) item {
            Spacer(Modifier.height(14.dp))
            UCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconDisc(Icons.Rounded.CloudUpload, 40.dp, U.c.sky, U.c.skySoft)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.backup_nudge_title), style = UType.Strong)
                        Text(stringResource(R.string.backup_nudge_text, data.sealed.size), style = UType.Muted)
                    }
                    IconButton(onClick = { container.scope.launch { container.settings.update { it.copy(backupNudge = System.currentTimeMillis()) } } }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = U.c.faint)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SoftButton(stringResource(R.string.backup_now), { go.settings() }, Modifier.fillMaxWidth(), color = U.c.sky, soft = U.c.skySoft)
            }
        }

        if (data.sealed.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.on_the_way, data.sealed.size))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Filter.entries) { f ->
                        Chip(
                            when (f) {
                                Filter.ALL -> stringResource(R.string.filter_all)
                                Filter.LETTERS -> stringResource(R.string.filter_letters)
                                Filter.MOMENTS -> stringResource(R.string.filter_moments)
                            },
                            filter == f, { filter = f },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            items(sealed, key = { "sealed" + it.id }) { c ->
                SealedRow(c, data) { go.capsule(c.id) }
                Spacer(Modifier.height(10.dp))
            }
        }

        if (data.opened.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.opened_letters)) }
            items(data.opened, key = { "opened" + it.id }) { c ->
                OpenedRow(c, data) { go.capsule(c.id) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun summary(data: DearData): String {
    val context = ctx()
    val next = data.sealed.firstOrNull()
    return when {
        data.capsules.isEmpty() -> stringResource(R.string.vault_empty_sub)
        next != null -> context.resources.getQuantityString(R.plurals.letters_on_the_way, data.sealed.size, data.sealed.size)
        else -> stringResource(R.string.vault_all_opened)
    }
}

@Composable
private fun Notice(icon: ImageVector, title: String, text: String) {
    Row(
        Modifier.fillMaxWidth().clip(U.RMd).background(U.c.goldSoft).padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = U.c.gold)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = UType.Strong)
            Spacer(Modifier.height(2.dp))
            Text(text, style = UType.Muted)
        }
    }
}

@Composable
private fun WaitingCall(call: Call, data: DearData, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val r = data.ritualsById[call.ritualId] ?: return
    val left = call.deadline - data.now
    val timed = call.deadline > call.firedAt
    Row(
        Modifier.fillMaxWidth().clip(U.RMd).background(if (timed && left > 0) U.c.accent else U.c.surface)
            .then(if (timed && left > 0) Modifier else Modifier.border(1.dp, U.c.line, U.RMd))
            .clickable(onClick = onOpen).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val onColor = if (timed && left > 0) U.c.onAccent else U.c.ink
        Column(Modifier.weight(1f)) {
            Text(r.name, style = UType.Strong.copy(color = onColor))
            Text(
                when {
                    timed && left > 0 -> stringResource(R.string.call_left, Words.minSec(left))
                    timed -> stringResource(R.string.call_late, Words.span(ctx(), call.deadline, data.now))
                    else -> stringResource(R.string.call_waiting, Words.clock(ctx(), call.firedAt))
                },
                style = UType.Muted.copy(color = if (timed && left > 0) U.c.onAccent.copy(alpha = 0.85f) else U.c.muted),
            )
        }
        Text(stringResource(R.string.answer), style = UType.Button.copy(color = onColor), modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.skip), tint = onColor.copy(alpha = 0.6f)) }
    }
}

@Composable
private fun ReadyCard(c: Capsule, data: DearData, onClick: () -> Unit) {
    val context = ctx()
    UCard(onClick = onClick, padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Envelope(Modifier.width(96.dp), glow = true)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Words.capsuleTitle(context, c, data.ritualsById[c.ritualId ?: -1]), style = UType.Strong)
                Text(stringResource(R.string.written_ago, Words.ago(context, c.createdAt, data.now)), style = UType.Muted)
            }
        }
    }
}

@Composable
private fun NextHero(c: Capsule, data: DearData, onClick: () -> Unit) {
    val context = ctx()
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp).clip(U.RLg).background(U.c.surface).border(1.dp, U.c.line, U.RLg).clickable(onClick = onClick).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Envelope(Modifier.fillMaxWidth(0.62f))
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.next_opens_in).uppercase(), style = UType.Eyebrow)
        Spacer(Modifier.height(6.dp))
        Text(Words.countdown(context, c.openAt - data.now), style = UType.Timer.copy(fontSize = 34.sp))
        Spacer(Modifier.height(4.dp))
        Text(Words.longDate(context, c.openAt) + " · " + Words.capsuleTitle(context, c, data.ritualsById[c.ritualId ?: -1]), style = UType.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SealedRow(c: Capsule, data: DearData, onClick: () -> Unit) {
    val context = ctx()
    val photos = data.thenPhotos(c.id)
    UCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Envelope(Modifier.width(58.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Words.capsuleTitle(context, c, data.ritualsById[c.ritualId ?: -1]), style = UType.Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.sealed_opens, Words.mediumDate(context, c.openAt)), style = UType.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Hints(c, photos.size)
            }
            Spacer(Modifier.width(8.dp))
            Text(Words.span(context, data.now, c.openAt), style = UType.Small.copy(color = U.c.accent), textAlign = TextAlign.End, modifier = Modifier.width(78.dp))
        }
    }
}

/** What's inside without showing it: "📷 2 · 🎙 0:42 · ✉ 340". */
@Composable
fun Hints(c: Capsule, shots: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (shots > 0) Hint(Icons.Rounded.CameraAlt, shots.toString())
        if (c.voice != null) Hint(Icons.Rounded.Mic, Words.minSec(c.voiceMs))
        val w = Words.words(c.text)
        if (w > 0) Hint(Icons.AutoMirrored.Rounded.Notes, ctx().resources.getQuantityString(R.plurals.words, w, w))
    }
}

@Composable
private fun Hint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = U.c.faint)
        Spacer(Modifier.width(3.dp))
        Text(text, style = UType.Small)
    }
}

@Composable
private fun OpenedRow(c: Capsule, data: DearData, onClick: () -> Unit) {
    val context = ctx()
    val photo = data.thenPhotos(c.id).firstOrNull()
    UCard(onClick = onClick, padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val file = photo?.back ?: photo?.front
            if (file != null) MediaImage(file, Modifier.size(58.dp).clip(U.RSm))
            else Box(Modifier.size(58.dp).clip(U.RSm).background(U.c.paper).border(1.dp, U.c.paperLine, U.RSm), contentAlignment = Alignment.Center) {
                Text("✉", fontSize = 22.sp, color = U.c.faint)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.text.trim().lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: Words.capsuleTitle(context, c, data.ritualsById[c.ritualId ?: -1]),
                    style = UType.Letter.copy(fontSize = 16.sp, lineHeight = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.written_on, Words.mediumDate(context, c.createdAt)), style = UType.Muted)
            }
        }
    }
}

@Composable
private fun Empty(go: Go) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Envelope(Modifier.fillMaxWidth(0.66f))
        Spacer(Modifier.height(26.dp))
        Text(stringResource(R.string.empty_title), style = UType.Title, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_text), style = UType.Sub, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.write_first), { go.write() }, Modifier.fillMaxWidth())
    }
}
