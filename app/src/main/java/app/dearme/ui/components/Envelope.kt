package app.dearme.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dearme.ui.theme.Inter
import app.dearme.ui.theme.U
import kotlin.math.max

/**
 * The app's one illustration: an envelope closed with a wax seal.
 * [open] lifts the flap (0 closed, 1 open), [crack] splits the seal, [glow] marks a letter that may be opened.
 */
@Composable
fun Envelope(
    modifier: Modifier = Modifier,
    open: Float = 0f,
    crack: Float = 0f,
    glow: Boolean = false,
    paper: Color = U.c.envelope,
    shade: Color = U.c.envelopeShade,
    wax: Color = U.c.wax,
    waxDark: Color = U.c.waxDark,
    letter: Color = U.c.paper,
    halo: Color = U.c.gold,
) {
    val pulse = if (glow) {
        rememberInfiniteTransition(label = "glow").animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse").value
    } else 0f
    Canvas(modifier.aspectRatio(1.5f)) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.035f)
        val apexClosed = h * 0.56f
        val apex = apexClosed + (-h * 0.42f - apexClosed) * open
        val flapInFront = open < 0.5f

        // Back of the envelope, and the open flap behind the letter.
        drawRoundRect(shade, size = size, cornerRadius = r)
        if (!flapInFront) flap(w, apex, paper, shade)
        // The letter rises as the flap opens.
        if (open > 0f) {
            val lift = h * 0.32f * open
            drawRoundRect(letter, Offset(w * 0.08f, h * 0.10f - lift), Size(w * 0.84f, h * 0.8f), CornerRadius(w * 0.02f))
            for (i in 1..4) {
                val y = h * 0.10f - lift + h * 0.12f * i
                drawLine(shade.copy(alpha = 0.6f), Offset(w * 0.16f, y), Offset(w * (if (i == 4) 0.6f else 0.84f), y), strokeWidth = max(1f, h * 0.012f))
            }
        }
        // Front pocket: the two side flaps and the bottom one.
        val pocket = Path().apply {
            moveTo(0f, h * 0.18f); lineTo(w * 0.5f, h * 0.64f); lineTo(w, h * 0.18f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(pocket, paper)
        val seams = Path().apply { moveTo(0f, h); lineTo(w * 0.5f, h * 0.52f); lineTo(w, h) }
        drawPath(seams, shade, style = Stroke(max(1f, h * 0.012f), join = StrokeJoin.Round))
        if (flapInFront) flap(w, apex, paper, shade)

        // The seal sits on the flap's tip and fades as the flap lifts.
        val sealAlpha = (1f - open * 2.2f).coerceIn(0f, 1f)
        if (sealAlpha > 0f) {
            val c = Offset(w * 0.5f, apex)
            val rad = h * 0.17f
            if (glow) drawCircle(halo.copy(alpha = 0.18f + 0.22f * pulse), rad * (1.55f + 0.25f * pulse), c)
            val apart = rad * 0.55f * crack
            // Whole until it cracks: drawing two clipped halves would leave a hairline seam.
            if (crack == 0f) seal(c, rad, wax, waxDark, sealAlpha)
            else for (side in listOf(-1, 1)) {
                translate(left = side * apart, top = apart * 0.3f) {
                    rotate(side * 14f * crack, c) {
                        clipHalf(side, c, rad) { seal(c, rad, wax, waxDark, sealAlpha * (1f - crack * 0.6f)) }
                    }
                }
            }
        }
    }
}

private fun DrawScope.flap(w: Float, apex: Float, paper: Color, shade: Color) {
    val p = Path().apply { moveTo(0f, 0f); lineTo(w, 0f); lineTo(w * 0.5f, apex); close() }
    drawPath(p, Brush.verticalGradient(listOf(paper, lerp(paper, shade, 0.35f)), startY = minOf(0f, apex), endY = maxOf(0f, apex) + 1f))
    drawPath(p, shade, style = Stroke(max(1f, size.height * 0.012f), join = StrokeJoin.Round))
}

/** Draws [block] clipped to the left (side -1) or right (side 1) half of the seal, with a jagged crack line. */
private fun DrawScope.clipHalf(side: Int, c: Offset, rad: Float, block: DrawScope.() -> Unit) {
    val r = rad * 1.6f
    val zig = Path().apply {
        moveTo(c.x, c.y - r)
        lineTo(c.x + rad * 0.12f, c.y - rad * 0.45f)
        lineTo(c.x - rad * 0.1f, c.y - rad * 0.05f)
        lineTo(c.x + rad * 0.14f, c.y + rad * 0.35f)
        lineTo(c.x, c.y + r)
        lineTo(c.x + side * r, c.y + r)
        lineTo(c.x + side * r, c.y - r)
        close()
    }
    drawContext.canvas.save()
    drawContext.canvas.clipPath(zig)
    block()
    drawContext.canvas.restore()
}

private fun DrawScope.seal(c: Offset, rad: Float, wax: Color, dark: Color, alpha: Float) {
    // A blob of wax: a circle with a few soft bulges, a pressed rim and a heart in the middle.
    for (i in 0 until 7) {
        val a = Math.toRadians(i * 360.0 / 7 + 10)
        drawCircle(wax.copy(alpha = alpha), rad * 0.42f, Offset(c.x + (rad * 0.68f * Math.cos(a)).toFloat(), c.y + (rad * 0.68f * Math.sin(a)).toFloat()))
    }
    drawCircle(wax.copy(alpha = alpha), rad * 0.95f, c)
    drawCircle(dark.copy(alpha = alpha * 0.55f), rad * 0.72f, c, style = Stroke(rad * 0.09f))
    drawCircle(Color.White.copy(alpha = alpha * 0.16f), rad * 0.3f, Offset(c.x - rad * 0.38f, c.y - rad * 0.42f))
    val s = rad * 0.36f
    val heart = Path().apply {
        moveTo(c.x, c.y + s * 0.95f)
        cubicTo(c.x - s * 1.45f, c.y + s * 0.05f, c.x - s * 0.85f, c.y - s * 1.1f, c.x, c.y - s * 0.35f)
        cubicTo(c.x + s * 0.85f, c.y - s * 1.1f, c.x + s * 1.45f, c.y + s * 0.05f, c.x, c.y + s * 0.95f)
        close()
    }
    drawPath(heart, dark.copy(alpha = alpha * 0.8f))
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
)

/** A rubber stamp: "LATE · 12 MIN". */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, color: Color = U.c.danger) {
    Text(
        text.uppercase(),
        modifier = modifier
            .rotate(-8f)
            .border(2.dp, color.copy(alpha = 0.8f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color.copy(alpha = 0.85f),
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 1.5.sp,
    )
}
