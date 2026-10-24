package gb.frame

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Color identity of an app. Every screen of the kit reads its colors from here, so each emulator only picks a palette
 * (or derives one from its accent color with [EmuPalette.fromAccent]).
 */
@Immutable
data class EmuPalette(
    /** Main accent: selection, primary buttons, menu icons tint. */
    val accent: Color,
    /** Text/content drawn on [accent]. */
    val onAccent: Color,
    /** Secondary highlight: section titles, values, selected choices. */
    val highlight: Color,
    /** Screen background gradient, top to bottom. */
    val background: List<Color>,
    /** Floating panels (top bar, menus, dialogs). */
    val panel: Color,
    /** Settings cards. */
    val card: Color,
    /** Hairline borders of panels and cards. */
    val border: Color,
    /** Secondary text. */
    val dim: Color,
    /** Warnings (missing BIOS, errors). */
    val warn: Color,
    /** Base used for the Material color scheme (surfaces, containers). */
    val surface: Color,
) {
    val backgroundBrush get() = Brush.verticalGradient(background)
    val panelBorder get() = BorderStroke(1.dp, border)

    companion object {
        /**
         * Derives a full palette from one accent color (e.g. Color(0xFF6FD3FF) for a blue app): backgrounds and panels
         * are dark tones of the accent's hue, the highlight stays gold unless given.
         */
        fun fromAccent(accent: Color, highlight: Color = Color(0xFFE8C26A)): EmuPalette {
            val base = lerp(accent, Color.Black, 0.86f)
            return EmuPalette(
                accent = accent,
                onAccent = lerp(accent, Color.Black, 0.85f),
                highlight = highlight,
                background = listOf(lerp(accent, Color.Black, 0.86f), lerp(accent, Color.Black, 0.78f), lerp(accent, Color.Black, 0.90f)),
                panel = lerp(accent, Color.Black, 0.88f).copy(alpha = 0.9f),
                card = lerp(accent, Color.Black, 0.88f).copy(alpha = 0.9f),
                border = Color.White.copy(alpha = 0.13f),
                dim = Color.White.copy(alpha = 0.65f),
                warn = Color(0xFFFF8A65),
                surface = base,
            )
        }
    }
}

object EmuPalettes {
    /** Legacy violet palette retained for apps that explicitly choose it. */
    val Violet = EmuPalette(
        accent = Color(0xFFB9A6FF),
        onAccent = Color(0xFF1A1433),
        highlight = Color(0xFFE8C26A),
        background = listOf(Color(0xFF1D1636), Color(0xFF2B2152), Color(0xFF161126)),
        panel = Color(0xFF1B1830).copy(alpha = 0.88f),
        card = Color(0xFF1B1830).copy(alpha = 0.9f),
        border = Color.White.copy(alpha = 0.13f),
        dim = Color.White.copy(alpha = 0.65f),
        warn = Color(0xFFFF8A65),
        surface = Color(0xFF221B3F),
    )

    /** Classic Console: charcoal hardware surfaces, Saturn indigo controls, and restrained warm highlights. */
    val ClassicConsole = EmuPalette(
        accent = Color(0xFF8798D0),
        onAccent = Color(0xFF10131B),
        highlight = Color(0xFFE5C56A),
        background = listOf(Color(0xFF11131A), Color(0xFF202431), Color(0xFF0B0D12)),
        panel = Color(0xFF1A1D27).copy(alpha = 0.96f),
        card = Color(0xFF1A1D27).copy(alpha = 0.96f),
        border = Color.White.copy(alpha = 0.17f),
        dim = Color(0xFFB9BECA),
        warn = Color(0xFFFF8A65),
        surface = Color(0xFF181B24),
    )
}

val LocalEmuPalette = staticCompositionLocalOf { EmuPalettes.Violet }

/** Current palette inside [EmuTheme]. */
val palette: EmuPalette @Composable get() = LocalEmuPalette.current

private fun schemeFor(p: EmuPalette) = darkColorScheme(
    primary = p.accent, onPrimary = p.onAccent,
    primaryContainer = lerp(p.accent, p.surface, 0.7f), onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = p.highlight, onSecondary = lerp(p.highlight, Color.Black, 0.85f),
    secondaryContainer = lerp(p.highlight, Color.Black, 0.7f), onSecondaryContainer = lerp(p.highlight, Color.White, 0.6f),
    tertiary = Color(0xFF7FD7C4),
    background = p.background.first(), onBackground = Color(0xFFEDE9FF),
    surface = p.surface, onSurface = Color(0xFFEDE9FF),
    surfaceVariant = lerp(p.surface, p.accent, 0.12f), onSurfaceVariant = Color(0xFFCBC3EA),
    surfaceContainer = lerp(p.surface, p.accent, 0.06f), surfaceContainerHigh = lerp(p.surface, p.accent, 0.12f),
    surfaceContainerHighest = lerp(p.surface, p.accent, 0.18f),
    surfaceContainerLow = lerp(p.surface, Color.Black, 0.05f), surfaceContainerLowest = lerp(p.surface, Color.Black, 0.2f),
    outline = lerp(p.accent, p.surface, 0.55f), outlineVariant = lerp(p.accent, p.surface, 0.75f),
)

/**
 * Theme of every app using the kit: Material colors from [palette], and on small screens (short side under 480dp,
 * e.g. 720x720 handhelds) the whole interface is scaled down so layouts made for 480dp+ fit.
 */
@Composable
fun EmuTheme(palette: EmuPalette = EmuPalettes.Violet, content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val d = LocalDensity.current
    val shortSide = minOf(cfg.screenWidthDp, cfg.screenHeightDp).coerceAtLeast(1)
    val f = (shortSide / 480f).coerceIn(0.72f, 1f)
    CompositionLocalProvider(LocalDensity provides Density(d.density * f, d.fontScale), LocalEmuPalette provides palette) {
        MaterialTheme(colorScheme = schemeFor(palette), content = content)
    }
}
