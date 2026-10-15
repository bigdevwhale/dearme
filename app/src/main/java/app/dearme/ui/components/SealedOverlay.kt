package app.dearme.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dearme.R
import app.dearme.data.Capsule
import app.dearme.ui.Words
import app.dearme.ui.ctx
import app.dearme.ui.share.ShareCards
import app.dearme.ui.theme.U
import app.dearme.ui.theme.UType
import kotlinx.coroutines.delay

/** The letter slides in, the flap closes, the wax drops. Then: "See you on …". */
@Composable
fun SealedOverlay(capsule: Capsule, haptics: Boolean, onDone: () -> Unit) {
    val context = ctx()
    val haptic = LocalHapticFeedback.current
    val flap = remember { Animatable(1f) }
    val seal = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(250)
        flap.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        seal.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        words.animateTo(1f, tween(500))
    }
    BackHandler(onBack = onDone)
    Column(
        Modifier.fillMaxSize().background(U.c.bg).systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Envelope(
            Modifier.fillMaxWidth(0.8f).scale(0.96f + 0.04f * seal.value),
            open = flap.value,
            wax = U.c.wax.copy(alpha = seal.value.coerceIn(0f, 1f)),
            waxDark = U.c.waxDark.copy(alpha = seal.value.coerceIn(0f, 1f)),
        )
        Spacer(Modifier.height(36.dp))
        Column(Modifier.alpha(words.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.sealed_title), style = UType.Display)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.see_you_on, Words.longDate(context, capsule.openAt)), style = UType.Hand, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(Words.inSpan(context, capsule.createdAt, capsule.openAt), style = UType.Sub)
            Spacer(Modifier.height(36.dp))
            SoftButton(
                stringResource(R.string.share_postcard),
                { ShareCards.share(context, ShareCards.sealed(context, capsule), "dearme-sealed") },
                Modifier.fillMaxWidth(), icon = Icons.Rounded.IosShare,
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(stringResource(R.string.done), onDone, Modifier.fillMaxWidth())
        }
    }
}
