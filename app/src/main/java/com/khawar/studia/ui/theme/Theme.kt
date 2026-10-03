package com.khawar.studia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.khawar.studia.R
import com.khawar.studia.data.ThemeMode

/** The palette from the prototype: warm greys, one orange accent, a dark "locked" palette. */
@Immutable
data class StudiaColors(
    val bg: Color, val card: Color, val tile: Color, val tile2: Color,
    val ink: Color, val ink2: Color, val ink3: Color,
    val sel: Color, val onSel: Color,
    val accent: Color, val onAccent: Color,
    val line: Color, val drumEdge: Color, val drumMid: Color,
    val shadow: Color,
    /** Slide-to-start track: only a shade off the background, so it reads flat. */
    val slider: Color,
    val dark: Boolean,
) {
    val lockBg = Color(0xFF0E0D0C)
    val lockTile = Color(0xFF221F1D)
    val lockInk = Color(0xFFF3F0EC)
    val lockInk2 = Color(0xFF8A847F)
    val lockDim = Color(0xFF3A3532)
}

val LightColors = StudiaColors(
    bg = Color(0xFFE4E2DF), card = Color(0xFFF9F8F6), tile = Color(0xFFD9D7D3), tile2 = Color(0xFFCBC8C3),
    ink = Color(0xFF36322F), ink2 = Color(0xFF615B56), ink3 = Color(0xFFAAA49E),
    sel = Color(0xFF46413D), onSel = Color(0xFFF6F4F1),
    accent = Color(0xFFE4532A), onAccent = Color(0xFFFFFFFF),
    line = Color(0xFFC8C5C0), drumEdge = Color(0xFFC9C6C1), drumMid = Color(0xFFFBFAF8),
    shadow = Color(0x47281E14), slider = Color(0xFFDEDCD8), dark = false,
)

val DarkColors = StudiaColors(
    bg = Color(0xFF151312), card = Color(0xFF25221F), tile = Color(0xFF1D1B19), tile2 = Color(0xFF302C29),
    ink = Color(0xFFF0ECE7), ink2 = Color(0xFF968F89), ink3 = Color(0xFF5D5752),
    sel = Color(0xFF47423D), onSel = Color(0xFFF6F2ED),
    accent = Color(0xFFF06A3E), onAccent = Color(0xFF1A1210),
    line = Color(0xFF36312E), drumEdge = Color(0xFF0F0E0D), drumMid = Color(0xFF2E2A27),
    shadow = Color(0x99000000), slider = Color(0xFF1C1A18), dark = true,
)

val LocalColors = staticCompositionLocalOf { LightColors }

/** Short accessor: `S.c.accent`. */
object S {
    val c: StudiaColors @Composable get() = LocalColors.current
}

private fun manrope(w: Int) = Font(
    R.font.manrope,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

private val typography = Typography().withFont(Manrope)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun StudiaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    // Material components (date picker, bottom sheet) pick these up.
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.ink,
        surface = c.bg, onSurface = c.ink, surfaceVariant = c.tile, onSurfaceVariant = c.ink2,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.card, outline = c.line,
        secondaryContainer = c.sel, onSecondaryContainer = c.onSel,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.ink,
        surface = c.bg, onSurface = c.ink, surfaceVariant = c.tile, onSurfaceVariant = c.ink2,
        surfaceContainerHigh = c.card, surfaceContainerHighest = c.card, outline = c.line,
        secondaryContainer = c.sel, onSecondaryContainer = c.onSel,
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalColors provides c, LocalContentColor provides c.ink, content = content)
    }
}
