package io.github.aristidisg.ensemble.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.aristidisg.ensemble.R

/*
 * An achromatic interface that never tints how your clothes look: near-neutral greys, one ink
 * color for primary actions, green / red / blue only on the swipe stamps, and one bold "spark"
 * accent (vermilion) for headlines and the Like button: never behind a piece of clothing.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E2), onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF3A3A3A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E2), onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF3A3A3A), onTertiary = Color.White,
    background = Color(0xFFF5F5F4), onBackground = Color(0xFF111111),
    surface = Color(0xFFF5F5F4), onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE9E9E7), onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF0F0EE),
    surfaceContainer = Color(0xFFEBEBE9), surfaceContainerHigh = Color(0xFFE6E6E4), surfaceContainerHighest = Color(0xFFE0E0DE),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFFCFCFCD),
    error = Color(0xFFB3261E), onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color.White, onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF2E2E2E), onPrimaryContainer = Color.White,
    secondary = Color(0xFFCFCFCF), onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF2E2E2E), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFCFCFCF), onTertiary = Color(0xFF111111),
    background = Color(0xFF121212), onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF121212), onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color(0xFF0C0C0C), surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF242424), surfaceContainerHighest = Color(0xFF2C2C2C),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
)

@Immutable
data class EnsembleColors(
    val backdrop: Color, val like: Color, val nope: Color, val wear: Color,
    val accent: Color = Color(0xFFE4472B), val onAccent: Color = Color.White,
)

val LocalEnsembleColors = staticCompositionLocalOf { EnsembleColors(Color(0xFFE9E9E7), Color(0xFF1B8A4B), Color(0xFFC62828), Color(0xFF1E5BD8)) }

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(ExperimentalTextApi::class)
private fun fraunces(w: Int) = Font(R.font.fraunces, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))
val Fraunces = FontFamily(fraunces(400), fraunces(500), fraunces(600))

private val base = Typography()

val EnsembleTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Fraunces),
    displayMedium = base.displayMedium.copy(fontFamily = Fraunces),
    displaySmall = base.displaySmall.copy(fontFamily = Fraunces),
    headlineLarge = base.headlineLarge.copy(fontFamily = Fraunces),
    headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces),
    headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter),
    bodySmall = base.bodySmall.copy(fontFamily = Inter),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
)

/** Big editorial headline for the outfit card: the technique name. */
val HeroStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp)

val StampStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, letterSpacing = 3.sp)

@Composable
fun EnsembleTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    val extra = if (dark) EnsembleColors(Color(0xFF2A2A2A), Color(0xFF4CC38A), Color(0xFFFF6B6B), Color(0xFF6EA0FF), Color(0xFFFF6A4D), Color(0xFF1A0A06))
    else EnsembleColors(Color(0xFFE9E9E7), Color(0xFF1B8A4B), Color(0xFFC62828), Color(0xFF1E5BD8))
    androidx.compose.runtime.CompositionLocalProvider(LocalEnsembleColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = EnsembleTypography, content = content)
    }
}
