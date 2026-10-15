package app.dearme.ui.capsule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dearme.R
import app.dearme.container
import app.dearme.data.Capsule
import app.dearme.data.Clock
import app.dearme.data.Kind
import app.dearme.data.Media
import app.dearme.data.Photo
import app.dearme.data.PhotoMode
import app.dearme.ui.DearData
import app.dearme.ui.Go
import app.dearme.ui.Words
import app.dearme.ui.camera.CameraOverlay
import app.dearme.ui.components.ConfirmDialog
import app.dearme.ui.components.DualPhoto
import app.dearme.ui.components.Envelope
import app.dearme.ui.components.PrimaryButton
import app.dearme.ui.components.SectionTitle
import app.dearme.ui.components.SoftButton
import app.dearme.ui.components.Stamp
import app.dearme.ui.components.TopBar
import app.dearme.ui.components.UCard
import app.dearme.ui.components.VoicePlayer
import app.dearme.ui.ctx
import app.dearme.ui.share.ShareCards
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import app.dearme.ui.vault.Hints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CapsuleScreen(id: Long, data: DearData, go: Go) {
    val context = ctx()
    val container = context.container
    val c = data.capsules.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    var nowCamera by remember { mutableStateOf(false) }
    // Set while the seal is being broken, so the letter appears under the animation rather than all at once.
    var ceremony by remember { mutableStateOf(false) }

    if (c == null) {
        Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) { TopBar("", onBack = { go.back() }) }
        return
    }
    val photos = data.photosBy[c.id].orEmpty()
    val then = photos.filter { it.role == Photo.ROLE_THEN }
    val nowShot = photos.lastOrNull { it.role == Photo.ROLE_NOW }

    if (nowCamera) {
        val mode = then.firstOrNull()?.let { if (it.back != null && it.front != null) PhotoMode.DUAL else if (it.front != null) PhotoMode.FRONT else PhotoMode.BACK } ?: PhotoMode.ANY
        CameraOverlay(mode, null, onShot = { s ->
            nowCamera = false
            container.write {
                nowShot?.let { old -> dao().deletePhoto(old); Media.delete(context, old.files) }
                dao().insertPhoto(Photo(capsuleId = c.id, back = s.back, front = s.front, takenAt = s.takenAt, role = Photo.ROLE_NOW))
            }
        }, onClose = { nowCamera = false })
        return
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
        TopBar(Words.capsuleTitle(context, c, data.ritualsById[c.ritualId ?: -1]), onBack = { go.back() }) {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = U.c.faint) }
        }
        when {
            c.isOpen() && !ceremony -> Opened(c, then, nowShot, data, go, onNowPhoto = { nowCamera = true })
            c.isDue(data.now) || ceremony -> Ready(c, data, onOpening = { ceremony = true }, onOpened = { ceremony = false })
            else -> Sealed(c, data, then.size)
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = stringResource(R.string.delete_title),
        text = stringResource(if (c.isOpen()) R.string.delete_opened_text else R.string.delete_sealed_text),
        confirm = stringResource(R.string.delete),
        danger = true,
        onConfirm = {
            container.write { Media.delete(context, dao().deleteCapsule(c)) }
            go.back()
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun Sealed(c: Capsule, data: DearData, shots: Int) {
    val context = ctx()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Envelope(Modifier.fillMaxWidth(0.82f))
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, Modifier.size(15.dp), tint = U.c.faint)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sealed_until).uppercase(), style = UType.Eyebrow)
        }
        Spacer(Modifier.height(6.dp))
        Text(Words.longDate(context, c.openAt), style = UType.Title, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(Words.countdown(context, c.openAt - data.now), style = UType.Timer)
        Spacer(Modifier.height(6.dp))
        Text(Words.inSpan(context, data.now, c.openAt), style = UType.Sub)
        Spacer(Modifier.height(26.dp))
        UCard {
            Text(stringResource(R.string.sealed_on, Words.dateTime(context, c.createdAt)), style = UType.Body)
            Spacer(Modifier.height(6.dp))
            Hints(c, shots)
            if (c.prompt.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.answers_question), style = UType.Muted)
                Text(c.prompt, style = UType.Letter.copy(fontSize = 15.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic, color = U.c.sky))
            }
        }
        Spacer(Modifier.height(16.dp))
        SoftButton(
            stringResource(R.string.share_postcard),
            { ShareCards.share(context, ShareCards.sealed(context, c), "dearme-sealed") },
            Modifier.fillMaxWidth(), icon = Icons.Rounded.IosShare,
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** The seal can be broken: check the clock, crack the wax, lift the flap. */
@Composable
private fun Ready(c: Capsule, data: DearData, onOpening: () -> Unit, onOpened: () -> Unit) {
    val context = ctx()
    val container = context.container
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val crack = remember { Animatable(0f) }
    val flap = remember { Animatable(0f) }
    var checking by remember { mutableStateOf(false) }
    var clockWrong by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }

    fun breakSeal() {
        if (checking || opening) return
        checking = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Clock.verifyForOpening(context, container.settings) }
            if (!ok || System.currentTimeMillis() < c.openAt) {
                checking = false
                clockWrong = true
                return@launch
            }
            checking = false
            opening = true
            onOpening()
            if (data.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            crack.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            flap.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
            delay(250)
            withContext(Dispatchers.IO) {
                container.db.dao().updateCapsule(c.copy(openedAt = System.currentTimeMillis(), notified = true))
                container.changed()
            }
            onOpened()
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Envelope(Modifier.fillMaxWidth(0.86f).clickable(enabled = !checking && !opening) { breakSeal() }, open = flap.value, crack = crack.value, glow = crack.value == 0f)
        Spacer(Modifier.height(34.dp))
        Text(stringResource(R.string.ready_title).uppercase(), style = UType.Eyebrow.copy(color = U.c.gold))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.from_you, Words.ago(context, c.createdAt, data.now)), style = UType.Title, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.written_on, Words.longDate(context, c.createdAt)), style = UType.Sub)
        Spacer(Modifier.height(32.dp))
        if (checking) CircularProgressIndicator(color = U.c.accent)
        else if (!opening) PrimaryButton(stringResource(R.string.break_seal), { breakSeal() }, Modifier.fillMaxWidth())
    }

    if (clockWrong) ConfirmDialog(
        title = stringResource(R.string.clock_wrong_title),
        text = stringResource(R.string.clock_wrong_text),
        confirm = stringResource(R.string.ok),
        onConfirm = {},
        onDismiss = { clockWrong = false },
    )
}

@Composable
private fun Opened(c: Capsule, then: List<Photo>, nowShot: Photo?, data: DearData, go: Go, onNowPhoto: () -> Unit) {
    val context = ctx()
    val original = c.replyTo?.let { r -> data.capsules.firstOrNull { it.id == r } }
    val replies = data.capsules.filter { it.replyTo == c.id }
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 12 }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.from_you_on, Words.longDate(context, c.createdAt)).uppercase(), style = UType.Eyebrow)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.travelled, Words.span(context, c.createdAt, c.openedAt ?: c.openAt)),
                style = UType.Muted,
            )
            Spacer(Modifier.height(14.dp))
            then.forEach { p ->
                Box {
                    DualPhoto(p.back, p.front, Modifier.fillMaxWidth())
                    if (c.lateMs > 0) Stamp(
                        stringResource(R.string.late_stamp, Words.span(context, 0, c.lateMs)),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp), color = U.c.onAccent,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (then.isEmpty() && c.lateMs > 0) {
                Stamp(stringResource(R.string.late_stamp, Words.span(context, 0, c.lateMs)))
                Spacer(Modifier.height(12.dp))
            }
            if (c.text.isNotBlank() || c.prompt.isNotBlank()) {
                Column(Modifier.fillMaxWidth().background(U.c.paper, U.RMd).border(1.dp, U.c.paperLine, U.RMd).padding(20.dp)) {
                    if (c.kind == Kind.LETTER) {
                        val name = data.settings.name.trim()
                        Text(if (name.isEmpty()) stringResource(R.string.dear_me) else stringResource(R.string.dear_name, name), style = UType.Hand)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (c.prompt.isNotBlank()) {
                        Text(c.prompt, style = UType.Letter.copy(fontSize = 15.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic, color = U.c.sky))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (c.text.isNotBlank()) Text(c.text, style = UType.Letter)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.signature, Words.mediumDate(context, c.createdAt)), style = UType.Hand.copy(fontSize = 24.sp), modifier = Modifier.align(Alignment.End))
                }
                Spacer(Modifier.height(12.dp))
            }
            c.voice?.let {
                VoicePlayer(it, c.voiceMs, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            val firstThen = then.firstOrNull()
            if (firstThen != null) {
                SectionTitle(stringResource(R.string.then_and_now))
                if (nowShot != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            DualPhoto(firstThen.back, firstThen.front, Modifier.fillMaxWidth(), corner = 14.dp)
                            Text(Words.mediumDate(context, firstThen.takenAt), style = UType.Small, modifier = Modifier.padding(top = 4.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            DualPhoto(nowShot.back, nowShot.front, Modifier.fillMaxWidth(), corner = 14.dp)
                            Text(Words.mediumDate(context, nowShot.takenAt), style = UType.Small, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SoftButton(stringResource(R.string.retake), onNowPhoto, Modifier.weight(1f), icon = Icons.Rounded.CameraAlt)
                        SoftButton(
                            stringResource(R.string.share), { ShareCards.share(context, ShareCards.thenNow(context, c, firstThen, nowShot), "dearme-then-now") },
                            Modifier.weight(1f), icon = Icons.Rounded.IosShare,
                        )
                    }
                } else {
                    Text(stringResource(R.string.then_and_now_text), style = UType.Muted)
                    Spacer(Modifier.height(10.dp))
                    SoftButton(stringResource(R.string.take_now_photo), onNowPhoto, Modifier.fillMaxWidth(), icon = Icons.Rounded.CameraAlt)
                }
            }

            if (original != null) {
                SectionTitle(stringResource(R.string.this_answers))
                UCard(onClick = { go.capsule(original.id) }) {
                    Text(stringResource(R.string.letter_from, Words.mediumDate(context, original.createdAt)), style = UType.Strong)
                    if (original.text.isNotBlank()) Text(original.text, style = UType.Muted, maxLines = 2)
                }
            }
            if (replies.isNotEmpty()) {
                SectionTitle(stringResource(R.string.your_replies))
                replies.forEach { r ->
                    UCard(onClick = { go.capsule(r.id) }) {
                        Text(
                            if (r.isOpen()) stringResource(R.string.letter_from, Words.mediumDate(context, r.createdAt))
                            else stringResource(R.string.reply_sealed_until, Words.mediumDate(context, r.openAt)),
                            style = UType.Strong,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(Modifier.navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(stringResource(R.string.reply), { go.write(c.id) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Rounded.Reply)
                SoftButton(
                    stringResource(R.string.share),
                    { ShareCards.share(context, ShareCards.opened(context, c, firstThen), "dearme-letter") },
                    Modifier.weight(1f), icon = Icons.Rounded.IosShare,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
