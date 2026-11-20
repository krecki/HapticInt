package com.kreckapps.hapticinterface.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Central palette for the pendant UI.
 *
 * Edit these values to adjust the app's appearance. Components should use
 * MaterialTheme.colorScheme rather than defining their own UI colors.
 */
object PendantColors {
    // Main action / selection color.
    val primary = Color(0xFF1976D2)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFD6E8FF)
    val onPrimaryContainer = Color(0xFF102A43)

    // Secondary accents.
    val secondary = Color(0xFF386A9B)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFDCE8F2)
    val onSecondaryContainer = Color(0xFF172B3A)

    // Neutral backgrounds: deliberately cool/neutral grey, with no pink tint.
    val background = Color(0xFFF4F6F8)
    val surface = Color(0xFFFFFFFF)
    val surfaceVariant = Color(0xFFE9EDF1)
    val onBackground = Color(0xFF202830)
    val onSurface = Color(0xFF202830)
    val onSurfaceVariant = Color(0xFF53616D)

    // Borders and supporting states.
    val outline = Color(0xFF87939E)
    val outlineVariant = Color(0xFFD1D8DE)
    val tertiary = Color(0xFF557C69)
    val onTertiary = Color(0xFFFFFFFF)
    val error = Color(0xFFBA1A1A)
    val onError = Color(0xFFFFFFFF)
}

private val PendantColorScheme = lightColorScheme(
    primary = PendantColors.primary,
    onPrimary = PendantColors.onPrimary,
    primaryContainer = PendantColors.primaryContainer,
    onPrimaryContainer = PendantColors.onPrimaryContainer,
    secondary = PendantColors.secondary,
    onSecondary = PendantColors.onSecondary,
    secondaryContainer = PendantColors.secondaryContainer,
    onSecondaryContainer = PendantColors.onSecondaryContainer,
    tertiary = PendantColors.tertiary,
    onTertiary = PendantColors.onTertiary,
    error = PendantColors.error,
    onError = PendantColors.onError,
    background = PendantColors.background,
    onBackground = PendantColors.onBackground,
    surface = PendantColors.surface,
    onSurface = PendantColors.onSurface,
    surfaceVariant = PendantColors.surfaceVariant,
    onSurfaceVariant = PendantColors.onSurfaceVariant,
    outline = PendantColors.outline,
    outlineVariant = PendantColors.outlineVariant
)

@Composable
fun PendantTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PendantColorScheme,
        content = content
    )
}
