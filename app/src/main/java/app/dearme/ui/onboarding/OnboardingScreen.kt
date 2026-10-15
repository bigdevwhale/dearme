package app.dearme.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dearme.R
import app.dearme.container
import app.dearme.ui.components.Envelope
import app.dearme.ui.components.IconDisc
import app.dearme.ui.components.PrimaryButton
import app.dearme.ui.components.UCard
import app.dearme.ui.components.UTextField
import app.dearme.ui.ctx
import app.dearme.ui.rituals.Templates
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = ctx()
    val container = context.container
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var moments by rememberSaveable { mutableStateOf(true) }

    fun finish() {
        container.scope.launch {
            if (moments && container.db.dao().rituals().isEmpty()) container.db.dao().insertRitual(Templates.make(context, Templates.RANDOM))
            container.settings.update { it.copy(onboarded = true, name = name.trim()) }
            container.changed()
        }
        onDone()
    }

    Column(Modifier.fillMaxSize().background(U.c.bg).statusBarsPadding().imePadding()) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (page) {
                    0 -> {
                        Envelope(Modifier.fillMaxWidth(0.78f), glow = true)
                        Spacer(Modifier.height(34.dp))
                        Text(stringResource(R.string.dear_me), style = UType.Hand.copy(fontSize = 46.sp))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.onb_title), style = UType.Display, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.onb_text), style = UType.Sub, textAlign = TextAlign.Center)
                    }
                    1 -> {
                        Text(stringResource(R.string.onb_how), style = UType.Title, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Step(Icons.Rounded.EditNote, stringResource(R.string.onb_step1_title), stringResource(R.string.onb_step1_text))
                        Step(Icons.Rounded.Casino, stringResource(R.string.onb_step2_title), stringResource(R.string.onb_step2_text))
                        Step(Icons.Rounded.Drafts, stringResource(R.string.onb_step3_title), stringResource(R.string.onb_step3_text))
                    }
                    else -> {
                        Text(stringResource(R.string.onb_name_title), style = UType.Title, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.onb_name_text), style = UType.Sub, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        UTextField(name, { name = it.take(30) }, placeholder = stringResource(R.string.onb_name_hint))
                        Spacer(Modifier.height(24.dp))
                        UCard {
                            Row(Modifier.clickable { moments = !moments }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.tpl_random), style = UType.Strong)
                                    Text(stringResource(R.string.onb_moments_text), style = UType.Muted)
                                }
                                Spacer(Modifier.width(10.dp))
                                Switch(moments, { moments = it }, colors = SwitchDefaults.colors(checkedTrackColor = U.c.accent, uncheckedBorderColor = U.c.line, uncheckedTrackColor = U.c.surface2))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.onb_private), style = UType.Small, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                Box(Modifier.padding(4.dp).size(if (pager.currentPage == i) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (pager.currentPage == i) U.c.accent else U.c.line))
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            val last = pager.currentPage == 2
            PrimaryButton(
                stringResource(if (last) R.string.onb_begin else R.string.next),
                { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        IconDisc(icon, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Strong)
            Spacer(Modifier.height(2.dp))
            Text(text, style = UType.Sub)
        }
    }
}
