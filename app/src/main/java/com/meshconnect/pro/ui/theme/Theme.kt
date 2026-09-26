package com.meshconnect.pro.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DayYellowPrimary,
    onPrimary = DayOnPrimary,
    secondary = DayYellowVariant,
    onSecondary = DayOnPrimary,
    background = DayBackground,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = DayOnSurfaceMuted,
    outline = DayBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = NightBluePrimary,
    onPrimary = NightOnPrimary,
    secondary = NightBlueVariant,
    onSecondary = NightOnPrimary,
    background = NightBackground,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightOnSurfaceMuted,
    outline = NightBorder
)

@Composable
fun MeshConnectProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val animatedPrimary = animateColorAsState(colorScheme.primary, tween(400), label = "primary").value
    val animatedBg = animateColorAsState(colorScheme.background, tween(400), label = "bg").value
    val animatedSurface = animateColorAsState(colorScheme.surface, tween(400), label = "surface").value

    val effectiveColorScheme = colorScheme.copy(
        primary = animatedPrimary,
        background = animatedBg,
        surface = animatedSurface
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = animatedSurface.toArgb()
            window.navigationBarColor = animatedSurface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = effectiveColorScheme,
        content = content
    )
}
