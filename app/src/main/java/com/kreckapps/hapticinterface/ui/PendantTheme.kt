
package com.kreckapps.hapticinterface.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PendantColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,

    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF001D36),

    secondary = Color(0xFF386A9B),
    onSecondary = Color.White,

    secondaryContainer = Color(0xFFD4E4F7),
    onSecondaryContainer = Color(0xFF001D36)
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
