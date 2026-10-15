package app.dearme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.dearme.R
import app.dearme.data.ThemeMode

/** Letter paper, fountain-pen ink and red sealing wax; at night, a desk under a lamp. */
@Immutable
data class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val paper: Color,
    val paperLine: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    /** Sealing wax. */
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val wax: Color,
    val waxDark: Color,
    val envelope: Color,
    val envelopeShade: Color,
    val gold: Color,
    val goldSoft: Color,
    val sage: Color,
    val sageSoft: Color,
    /** Ink blue. */
    val sky: Color,
    val skySoft: Color,
    val danger: Color,
    val dark: Boolean,
)

val LightPalette = Palette(
    bg = Color(0xFFF5EFE4),
    surface = Color(0xFFFFFCF6),
    surface2 = Color(0xFFFAF4E9),
    paper = Color(0xFFFFFBF2),
    paperLine = Color(0xFFE9DFCB),
    line = Color(0xFFE6DCCB),
    ink = Color(0xFF1E2433),
    muted = Color(0xFF6A6455),
    faint = Color(0xFFA69D8B),
    accent = Color(0xFFB3342F),
    accentSoft = Color(0xFFF5DFD9),
    onAccent = Color(0xFFFFFFFF),
    wax = Color(0xFFB3342F),
    waxDark = Color(0xFF7E1F1C),
    envelope = Color(0xFFF1E4CC),
    envelopeShade = Color(0xFFE2D0AE),
    gold = Color(0xFFB0812A),
    goldSoft = Color(0xFFF4E8CF),
    sage = Color(0xFF4C8466),
    sageSoft = Color(0xFFDFEDE3),
    sky = Color(0xFF2F5C93),
    skySoft = Color(0xFFDDE7F3),
    danger = Color(0xFFC6382F),
    dark = false,
)

val DarkPalette = Palette(
    bg = Color(0xFF15171D),
    surface = Color(0xFF1D2028),
    surface2 = Color(0xFF22262F),
    paper = Color(0xFF23262E),
    paperLine = Color(0xFF30343E),
    line = Color(0xFF2F333D),
    ink = Color(0xFFEFE8DA),
    muted = Color(0xFFABA392),
    faint = Color(0xFF6F6A5E),
    accent = Color(0xFFE0574F),
    accentSoft = Color(0xFF3B2121),
    onAccent = Color(0xFF1E0F0E),
    wax = Color(0xFFC63E37),
    waxDark = Color(0xFF7E1F1C),
    envelope = Color(0xFFD9CBB0),
    envelopeShade = Color(0xFFBCAB8C),
    gold = Color(0xFFE3B65C),
    goldSoft = Color(0xFF3A3020),
    sage = Color(0xFF7CC39C),
    sageSoft = Color(0xFF1F3329),
    sky = Color(0xFF8AB4EA),
    skySoft = Color(0xFF1D2A3D),
    danger = Color(0xFFFF6B63),
    dark = true,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val Inter = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** The letter itself. */
val Lora = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_medium, FontWeight.Medium),
    Font(R.font.lora_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Handwriting: "Dear me," and signatures. */
val Hand = FontFamily(Font(R.font.caveat, FontWeight.Normal))

object U {
    val c: Palette @Composable get() = LocalPalette.current

    val RXl = RoundedCornerShape(28.dp)
    val RLg = RoundedCornerShape(24.dp)
    val RMd = RoundedCornerShape(18.dp)
    val RSm = RoundedCornerShape(12.dp)
}

object UType {
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Medium, fontSize = 32.sp, letterSpacing = (-0.01).em, lineHeight = 38.sp, color = U.c.ink)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Medium, fontSize = 26.sp, letterSpacing = (-0.01).em, lineHeight = 32.sp, color = U.c.ink)
    val Sub: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontSize = 14.5.sp, lineHeight = 21.sp, color = U.c.muted)
    val Eyebrow: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.12.em, color = U.c.faint)
    val Strong: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.01).em, color = U.c.ink)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, color = U.c.ink)
    val Muted: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, color = U.c.muted)
    val Small: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = U.c.faint)
    val Number: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.02).em, color = U.c.ink, fontFeatureSettings = "tnum")
    val Timer: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, letterSpacing = (-0.03).em, color = U.c.ink, fontFeatureSettings = "tnum")
    val Button: TextStyle @Composable get() = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Letter: TextStyle @Composable get() = TextStyle(fontFamily = Lora, fontSize = 18.sp, lineHeight = 28.sp, color = U.c.ink)
    val Hand: TextStyle @Composable get() = TextStyle(fontFamily = app.dearme.ui.theme.Hand, fontSize = 30.sp, lineHeight = 34.sp, color = U.c.sky)
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun DearTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme.copy(
                primary = p.accent,
                onPrimary = p.onAccent,
                primaryContainer = p.accentSoft,
                onPrimaryContainer = p.ink,
                secondaryContainer = p.accentSoft,
                onSecondaryContainer = p.ink,
                background = p.bg,
                surface = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface,
                surfaceContainerHighest = p.surface2,
                surfaceContainerLow = p.surface2,
                onBackground = p.ink,
                onSurface = p.ink,
                onSurfaceVariant = p.muted,
                outline = p.line,
                outlineVariant = p.line,
                error = p.danger,
            ),
            typography = MaterialTheme.typography.copy(
                bodyLarge = UType.Body,
                bodyMedium = UType.Body.copy(fontSize = 14.sp),
                labelLarge = UType.Button,
            ),
            content = content,
        )
    }
}
