package il.co.tradesmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import il.co.tradesmanager.data.repository.SettingsRepository.ThemeMode

// High-visibility amber on slate: legible in direct sun, and distinct enough
// from the safety-signal reds and greens used for stock and checklist states.
private val Amber = Color(0xFFF2B705)
private val AmberDark = Color(0xFF8A6800)
private val Slate = Color(0xFF1F2933)
private val SlateLight = Color(0xFF3E4C59)
private val Sand = Color(0xFFFAFAF7)
private val Ink = Color(0xFF12181F)

private val LightColors = lightColorScheme(
    primary = Slate,
    onPrimary = Color.White,
    primaryContainer = Amber,
    onPrimaryContainer = Slate,
    secondary = SlateLight,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Slate,
    surface = Color.White,
    onSurface = Slate,
    surfaceVariant = Color(0xFFE7E9EC),
    onSurfaceVariant = SlateLight,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    primaryContainer = AmberDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9AA5B1),
    onSecondary = Ink,
    background = Ink,
    onBackground = Color(0xFFE4E7EB),
    surface = Color(0xFF1B222B),
    onSurface = Color(0xFFE4E7EB),
    surfaceVariant = Color(0xFF2A333D),
    onSurfaceVariant = Color(0xFFB6BFC9),
    error = Color(0xFFF2B8B5),
)

/**
 * Larger text is a first-class setting, not an accessibility afterthought:
 * this app is read at arm's length, in gloves, on a scaffold. It scales the
 * whole type ramp rather than a chosen few styles, so nothing collides.
 */
private const val LARGE_TEXT_SCALE = 1.18f

@Composable
fun TradesManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = if (largeText) scaled(Typography(), LARGE_TEXT_SCALE) else Typography(),
        content = content,
    )
}

private fun scaled(base: Typography, factor: Float): Typography = Typography(
    displayLarge = base.displayLarge.scale(factor),
    displayMedium = base.displayMedium.scale(factor),
    displaySmall = base.displaySmall.scale(factor),
    headlineLarge = base.headlineLarge.scale(factor),
    headlineMedium = base.headlineMedium.scale(factor),
    headlineSmall = base.headlineSmall.scale(factor),
    titleLarge = base.titleLarge.scale(factor),
    titleMedium = base.titleMedium.scale(factor),
    titleSmall = base.titleSmall.scale(factor),
    bodyLarge = base.bodyLarge.scale(factor),
    bodyMedium = base.bodyMedium.scale(factor),
    bodySmall = base.bodySmall.scale(factor),
    labelLarge = base.labelLarge.scale(factor),
    labelMedium = base.labelMedium.scale(factor),
    labelSmall = base.labelSmall.scale(factor),
)

private fun TextStyle.scale(factor: Float): TextStyle =
    copy(fontSize = fontSize.scale(factor), lineHeight = lineHeight.scale(factor))

private fun TextUnit.scale(factor: Float): TextUnit =
    if (isUnspecified) this else (value * factor).sp
