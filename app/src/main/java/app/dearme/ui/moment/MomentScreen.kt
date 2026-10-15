package app.dearme.ui.moment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dearme.R
import app.dearme.container
import app.dearme.content.Prompts
import app.dearme.data.Capsule
import app.dearme.data.Kind
import app.dearme.data.Media
import app.dearme.data.Photo
import app.dearme.data.PhotoMode
import app.dearme.data.TextMode
import app.dearme.domain.Lock
import app.dearme.notify.Alarms
import app.dearme.ui.DearData
import app.dearme.ui.Go
import app.dearme.ui.Words
import app.dearme.ui.camera.CameraOverlay
import app.dearme.ui.camera.Shot
import app.dearme.ui.components.DualPhoto
import app.dearme.ui.components.Envelope
import app.dearme.ui.components.PrimaryButton
import app.dearme.ui.components.SealedOverlay
import app.dearme.ui.components.SoftButton
import app.dearme.ui.components.Stamp
import app.dearme.ui.components.TopBar
import app.dearme.ui.components.VoiceRecorder
import app.dearme.ui.ctx
import app.dearme.ui.locale
import app.dearme.ui.theme.Inter
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Answering a ritual's call: the shot first (the clock is ticking), then a line or a letter, then the seal. */
@Composable
fun MomentScreen(callId: Long, data: DearData, go: Go) {
    val context = ctx()
    val container = context.container
    val loc = locale()
    val call = data.calls.firstOrNull { it.id == callId }
    val ritual = call?.let { data.ritualsById[it.ritualId] }

    var shot by remember { mutableStateOf<Shot?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var voice by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceMs by rememberSaveable { mutableLongStateOf(0L) }
    var camera by remember { mutableStateOf(false) }
    var sealed by remember { mutableStateOf<Capsule?>(null) }
    val prompt = rememberSaveable(ritual?.id) {
        when {
            ritual == null || !ritual.usePrompt -> ""
            ritual.prompt.isNotBlank() -> ritual.prompt
            else -> Prompts.random(loc)
        }
    }

    LaunchedEffect(ritual?.id) { ritual?.let { Alarms.clearCallNotification(context, it.id) } }
    // The camera opens straight away: the moment is now.
    var autoCamera by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(ritual?.id) {
        if (autoCamera && ritual != null && call.capsuleId == null && ritual.photo != PhotoMode.NONE) camera = true
        autoCamera = false
    }

    fun leave() {
        Media.delete(context, shot?.files.orEmpty() + listOfNotNull(voice))
        go.back()
    }

    sealed?.let {
        SealedOverlay(it, data.settings.haptics) { go.home() }
        return
    }
    if (call == null || ritual == null || call.capsuleId != null) {
        Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding()) {
            TopBar("", onBack = { go.back() })
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Envelope(Modifier.fillMaxWidth(0.5f))
                Spacer(Modifier.height(20.dp))
                Text(stringResource(if (call?.capsuleId != null) R.string.call_answered else R.string.call_gone), style = UType.Sub, textAlign = TextAlign.Center)
            }
        }
        return
    }
    val timed = call.deadline > call.firedAt
    if (camera) {
        CameraOverlay(
            ritual.photo, if (timed) call.deadline else null,
            onShot = { shot = it; camera = false },
            onClose = { camera = false },
            retakes = 1,
        )
        return
    }

    BackHandler { leave() }
    val left = call.deadline - data.now
    val needsShot = ritual.photo != PhotoMode.NONE
    val canSeal = shot != null || text.isNotBlank() || voice != null

    fun seal() {
        val now = System.currentTimeMillis()
        val s = shot
        val answeredAt = s?.takenAt ?: now
        container.scope.launch {
            val dao = container.db.dao()
            val days = Lock.daysFor(ritual, dao.firstCreated(), now)
            val capsule = Capsule(
                kind = Kind.MOMENT, createdAt = answeredAt,
                openAt = Lock.openAt(answeredAt, days, java.time.ZoneId.systemDefault()),
                text = text.trim(), prompt = prompt, voice = voice, voiceMs = voiceMs, ritualId = ritual.id,
                lateMs = if (timed) (answeredAt - call.deadline).coerceAtLeast(0) else 0,
            )
            val id = dao.insertCapsule(capsule)
            if (s != null) dao.insertPhoto(Photo(capsuleId = id, back = s.back, front = s.front, takenAt = s.takenAt))
            dao.updateCall(call.copy(capsuleId = id))
            container.changed()
            withContext(Dispatchers.Main) { sealed = capsule.copy(id = id) }
        }
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(ritual.name, onBack = { leave() }) {
            if (timed) {
                val late = left <= 0
                Text(
                    if (late) stringResource(R.string.late_by, Words.minSec(-left)) else Words.minSec(left),
                    color = U.c.onAccent, fontFamily = Inter, style = UType.Button,
                    modifier = Modifier.padding(end = 12.dp).clip(CircleShape).background(if (late) U.c.faint else U.c.wax).padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            val s = shot
            if (needsShot) {
                if (s != null) {
                    Box {
                        DualPhoto(s.back, s.front, Modifier.fillMaxWidth())
                        if (timed && s.takenAt > call.deadline) {
                            Stamp(stringResource(R.string.late_stamp, Words.span(context, call.deadline, s.takenAt)), modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp), color = U.c.onAccent)
                        }
                    }
                } else {
                    SoftButton(stringResource(R.string.take_photo), { camera = true }, Modifier.fillMaxWidth().height(64.dp), icon = Icons.Rounded.CameraAlt)
                }
                Spacer(Modifier.height(16.dp))
            }
            if (prompt.isNotBlank()) {
                Text(prompt, style = UType.Letter.copy(fontSize = 17.sp, color = U.c.sky, fontStyle = FontStyle.Italic))
                Spacer(Modifier.height(10.dp))
            }
            when (ritual.textMode) {
                TextMode.NONE -> {}
                TextMode.LINE, TextMode.LETTER -> {
                    val line = ritual.textMode == TextMode.LINE
                    Box(
                        Modifier.fillMaxWidth().clip(U.RMd).background(U.c.paper).border(1.dp, U.c.paperLine, U.RMd).padding(16.dp)
                            .heightIn(min = if (line) 0.dp else 180.dp),
                    ) {
                        if (text.isEmpty()) Text(stringResource(if (line) R.string.line_placeholder else R.string.letter_placeholder), style = UType.Letter.copy(color = U.c.faint))
                        BasicTextField(
                            value = text, onValueChange = { text = if (line) it.replace('\n', ' ').take(140) else it },
                            textStyle = UType.Letter, cursorBrush = SolidColor(U.c.accent),
                            singleLine = false, maxLines = if (line) 3 else Int.MAX_VALUE,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (line) Text("${text.length}/140", style = UType.Small, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (ritual.voice) {
                VoiceRecorder(voice, voiceMs) { f, ms -> voice = f; voiceMs = ms }
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.moment_opens, Words.lock(context, Lock.daysFor(ritual, data.capsules.minOfOrNull { it.createdAt }, data.now))), style = UType.Muted)
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(stringResource(R.string.seal), { seal() }, Modifier.fillMaxWidth(), enabled = canSeal)
        }
    }
}
