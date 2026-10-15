package app.dearme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dearme.data.Media
import app.dearme.ui.theme.U
import coil3.compose.AsyncImage

/** One stored photo by file name. */
@Composable
fun MediaImage(name: String, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    AsyncImage(model = Media.file(context, name), contentDescription = null, modifier = modifier, contentScale = scale)
}

/**
 * A shot as BeReal shows it: the main picture with the other one in a small corner frame.
 * Tapping the small one swaps them. Either file may be missing.
 */
@Composable
fun DualPhoto(back: String?, front: String?, modifier: Modifier = Modifier, ratio: Float = 3f / 4f, corner: Dp = 18.dp, inset: Boolean = true) {
    var swapped by remember(back, front) { mutableStateOf(false) }
    val main = if (swapped) front ?: back else back ?: front
    val small = if (back != null && front != null) (if (swapped) back else front) else null
    val shape = RoundedCornerShape(corner)
    Box(modifier.fillMaxWidth().aspectRatio(ratio).clip(shape).background(U.c.surface2)) {
        if (main != null) MediaImage(main, Modifier.fillMaxSize())
        if (small != null && inset) {
            Box(
                Modifier
                    .padding(12.dp)
                    .fillMaxWidth(0.3f)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(corner * 0.65f))
                    .border(2.dp, Color.Black.copy(alpha = 0.85f), RoundedCornerShape(corner * 0.65f))
                    .clickable(remember { MutableInteractionSource() }, null) { swapped = !swapped }
                    .align(Alignment.TopStart),
            ) { MediaImage(small, Modifier.fillMaxSize()) }
        }
    }
}
