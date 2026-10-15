package app.dearme.ui.write

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
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
import app.dearme.domain.Lock
import app.dearme.ui.DearData
import app.dearme.ui.Go
import app.dearme.ui.Words
import app.dearme.ui.camera.CameraOverlay
import app.dearme.ui.camera.Shot
import app.dearme.ui.components.Chip
import app.dearme.ui.components.ConfirmDialog
import app.dearme.ui.components.DualPhoto
import app.dearme.ui.components.FutureDateDialog
import app.dearme.ui.components.PrimaryButton
import app.dearme.ui.components.SealedOverlay
import app.dearme.ui.components.SectionTitle
import app.dearme.ui.components.SoftButton
import app.dearme.ui.components.TopBar
import app.dearme.ui.components.VoiceRecorder
import app.dearme.ui.ctx
import app.dearme.ui.locale
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A letter to the future: words, photos, a voice note, and the day it may be opened. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WriteScreen(replyTo: Long?, data: DearData, go: Go) {
    val context = ctx()
    val container = context.container
    val loc = locale()
    val original = replyTo?.let { id -> data.capsules.firstOrNull { it.id == id } }

    var text by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    val shots = remember { mutableStateListOf<Shot>() }
    var voice by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceMs by rememberSaveable { mutableLongStateOf(0L) }
    var lockDays by rememberSaveable { mutableIntStateOf(365) }
    var customAt by rememberSaveable { mutableLongStateOf(0L) }
    var camera by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmSeal by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var sealed by remember { mutableStateOf<Capsule?>(null) }

    val now = data.now
    val openAt = if (customAt > 0) customAt else Lock.openAt(now, lockDays, java.time.ZoneId.systemDefault())
    val empty = text.isBlank() && shots.isEmpty() && voice == null

    fun discard() {
        Media.delete(context, shots.flatMap { it.files } + listOfNotNull(voice))
        go.back()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris: List<Uri> ->
        container.scope.launch {
            val added = withContext(Dispatchers.IO) { uris.mapNotNull { Media.importImage(context, it) } }
            withContext(Dispatchers.Main) { added.forEach { shots += Shot(it, null, System.currentTimeMillis()) } }
        }
    }

    fun seal() {
        val createdAt = System.currentTimeMillis()
        val capsule = Capsule(
            kind = Kind.LETTER, createdAt = createdAt,
            openAt = if (customAt > 0) customAt else Lock.openAt(createdAt, lockDays, java.time.ZoneId.systemDefault()),
            text = text.trim(), prompt = prompt, voice = voice, voiceMs = voiceMs, replyTo = replyTo,
        )
        val list = shots.toList()
        container.scope.launch {
            val dao = container.db.dao()
            val id = dao.insertCapsule(capsule)
            list.forEach { dao.insertPhoto(Photo(capsuleId = id, back = it.back, front = it.front, takenAt = it.takenAt)) }
            container.changed()
            withContext(Dispatchers.Main) { sealed = capsule.copy(id = id) }
        }
    }

    BackHandler(enabled = sealed == null && !camera) { if (empty) discard() else confirmDiscard = true }

    sealed?.let {
        SealedOverlay(it, data.settings.haptics) { go.home() }
        return
    }
    if (camera) {
        CameraOverlay(PhotoMode.ANY, null, onShot = { shots += it; camera = false }, onClose = { camera = false })
        return
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        TopBar(stringResource(if (original != null) R.string.reply_title else R.string.new_letter), onBack = { if (empty) discard() else confirmDiscard = true })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (original != null) {
                Column(Modifier.fillMaxWidth().clip(U.RMd).background(U.c.surface2).padding(14.dp)) {
                    Text(stringResource(R.string.in_reply_to, Words.mediumDate(context, original.createdAt)), style = UType.Eyebrow)
                    if (original.text.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("“" + original.text.trim() + "”", style = UType.Letter.copy(fontSize = 15.sp, lineHeight = 22.sp, color = U.c.muted), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // The paper.
            Column(Modifier.fillMaxWidth().clip(U.RMd).background(U.c.paper).border(1.dp, U.c.paperLine, U.RMd).padding(20.dp)) {
                val name = data.settings.name.trim()
                Text(if (name.isEmpty()) stringResource(R.string.dear_me) else stringResource(R.string.dear_name, name), style = UType.Hand)
                Spacer(Modifier.height(8.dp))
                if (prompt.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(prompt, style = UType.Letter.copy(fontSize = 15.sp, lineHeight = 21.sp, color = U.c.sky, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), modifier = Modifier.weight(1f))
                        IconButton(onClick = { prompt = Prompts.random(loc, prompt) }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.another_question), tint = U.c.faint) }
                        IconButton(onClick = { prompt = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove), tint = U.c.faint) }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
                    if (text.isEmpty()) Text(stringResource(R.string.letter_placeholder), style = UType.Letter.copy(color = U.c.faint))
                    BasicTextField(
                        value = text, onValueChange = { text = it },
                        textStyle = UType.Letter,
                        cursorBrush = SolidColor(U.c.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    )
                }
                val words = Words.words(text)
                if (words > 0) Text(context.resources.getQuantityString(R.plurals.words, words, words), style = UType.Small, modifier = Modifier.align(Alignment.End))
            }
            if (prompt.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(CircleShape).clickable { prompt = Prompts.random(loc) }.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Lightbulb, null, Modifier.size(18.dp), tint = U.c.gold)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.need_idea), style = UType.Button.copy(color = U.c.gold))
                }
            }

            SectionTitle(stringResource(R.string.attach))
            if (shots.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shots) { s ->
                        Box {
                            DualPhoto(s.back, s.front, Modifier.width(132.dp), corner = 14.dp)
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { Media.delete(context, s.files); shots.remove(s) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Close, stringResource(R.string.remove), Modifier.size(16.dp), tint = Color.White) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(stringResource(R.string.camera), { camera = true }, Modifier.weight(1f), icon = Icons.Rounded.CameraAlt)
                SoftButton(
                    stringResource(R.string.gallery),
                    { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    Modifier.weight(1f), icon = Icons.Rounded.AddPhotoAlternate,
                )
            }
            Spacer(Modifier.height(10.dp))
            VoiceRecorder(voice, voiceMs) { f, ms -> voice = f; voiceMs = ms }

            SectionTitle(stringResource(R.string.opens_in))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lock.presets.forEach { d ->
                    Chip(Words.lock(context, d), customAt == 0L && lockDays == d, { lockDays = d; customAt = 0 })
                }
                Chip(
                    if (customAt > 0) Words.mediumDate(context, customAt) else stringResource(R.string.pick_date),
                    customAt > 0, { pickDate = true },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.opens_on_span, Words.longDate(context, openAt), Words.inSpan(context, now, openAt)), style = UType.Muted)
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.background(U.c.bg).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(stringResource(R.string.seal), { confirmSeal = true }, Modifier.fillMaxWidth(), enabled = !empty)
        }
    }

    if (pickDate) FutureDateDialog(if (customAt > 0) customAt else openAt, onDismiss = { pickDate = false }) { customAt = it }
    if (confirmSeal) ConfirmDialog(
        title = stringResource(R.string.seal_confirm_title, Words.longDate(context, openAt)),
        text = stringResource(R.string.seal_confirm_text),
        confirm = stringResource(R.string.seal),
        onConfirm = { seal() },
        onDismiss = { confirmSeal = false },
    )
    if (confirmDiscard) ConfirmDialog(
        title = stringResource(R.string.discard_title),
        text = stringResource(R.string.discard_text),
        confirm = stringResource(R.string.discard),
        onConfirm = { discard() },
        onDismiss = { confirmDiscard = false },
        danger = true,
    )
}
