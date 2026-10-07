package com.sami.livetv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SamiLightColorScheme = lightColorScheme(
    primary = SamiLightPrimary,
    onPrimary = SamiLightOnPrimary,
    background = SamiLightBackground,
    onBackground = SamiLightOnBackground,
    surface = SamiLightSurface,
    onSurface = SamiLightOnSurface,
    surfaceVariant = SamiLightSurfaceVariant,
)

private val SamiDarkColorScheme = darkColorScheme(
    primary = SamiDarkPrimary,
    onPrimary = SamiDarkOnPrimary,
    background = SamiDarkBackground,
    onBackground = SamiDarkOnBackground,
    surface = SamiDarkSurface,
    onSurface = SamiDarkOnSurface,
    surfaceVariant = SamiDarkSurfaceVariant,
)

@Composable
fun SamiTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SamiDarkColorScheme else SamiLightColorScheme,
        content = content,
    )
}
