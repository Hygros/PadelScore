package ch.hygro.padelscore.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun PadelScoreTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme.copy(
        primary = ColorTeamUsText,
        primaryDim = ColorTeamUsText,
        primaryContainer = ColorPrimary,
        onPrimary = ColorPrimary,
        onPrimaryContainer = Color.White,

        secondary = ColorTeamUsText,
        secondaryDim = ColorTeamUsText,
        secondaryContainer = ColorSecondary,
        onSecondary = ColorSecondary,
        onSecondaryContainer = Color.White,

        tertiary = ColorTeamUsText,
        tertiaryDim = ColorTeamUsText,
        tertiaryContainer = ColorSecondary,
        onTertiary = ColorSecondary,
        onTertiaryContainer = Color.White,

        surfaceContainerLow = Color(0xFF1B1B1B),
        surfaceContainer = ColorSecondary,
        surfaceContainerHigh = Color(0xFF4B4B4B),
        onSurface = Color.White,
        onSurfaceVariant = Color.LightGray,

        error = ColorDestructive,
        onError = Color.White,
        errorContainer = ColorDestructive,
        onErrorContainer = Color.White,

        outline = Color.LightGray,
        outlineVariant = Color.DarkGray
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}