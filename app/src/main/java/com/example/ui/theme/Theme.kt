package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = TerracottaDark,
    secondary = BurnishedGold,
    onSecondary = DeepEspresso,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = DeepEspresso,
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldSoft,
    onTertiaryContainer = EmeraldSuccess,
    background = AlabasterBg,
    onBackground = DeepEspresso,
    surface = WarmSurface,
    onSurface = DeepEspresso,
    surfaceVariant = SandSurfaceVariant,
    onSurfaceVariant = MutedTaupe,
    outline = HairlineOutline,
    outlineVariant = HairlineOutline,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonSoft,
    onErrorContainer = CrimsonError
)

private val DarkColors = darkColorScheme(
    primary = DarkTerracotta,
    onPrimary = DeepEspresso,
    primaryContainer = Color(0xFF5A2013),
    onPrimaryContainer = TerracottaSoft,
    secondary = DarkGold,
    onSecondary = DeepEspresso,
    secondaryContainer = Color(0xFF4A3512),
    onSecondaryContainer = GoldSoft,
    tertiary = Color(0xFF4ADE80),
    onTertiary = DeepEspresso,
    tertiaryContainer = Color(0xFF134E2A),
    onTertiaryContainer = EmeraldSoft,
    background = DarkBg,
    onBackground = WarmIvoryText,
    surface = DarkSurface,
    onSurface = WarmIvoryText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = WarmMutedText,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFFCA5A5),
    onError = DeepEspresso,
    errorContainer = Color(0xFF5F1414),
    onErrorContainer = CrimsonSoft
)

val BazaarShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BazaarHubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BazaarTypography,
        shapes = BazaarShapes,
        content = content
    )
}
