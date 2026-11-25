package com.lumiposture.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LumiLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightTextSecondary,
    onSecondary = Color.White,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightCardSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    error = LightCritical,
    onError = Color.White,
    outline = LightOutlineSubtle,
)

private val LumiDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF061533),
    secondary = DarkTextSecondary,
    onSecondary = DarkCanvas,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkCardSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    error = DarkCritical,
    onError = Color(0xFF2D0707),
    outline = DarkOutlineSubtle,
)

@Composable
fun LumiPostureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) LumiDarkColorScheme else LumiLightColorScheme
    val semanticColors = if (darkTheme) DarkLumiColors else LightLumiColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalLumiColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LumiTypography,
            shapes = LumiShapes,
            content = content,
        )
    }
}

object LumiTheme {
    val colors: LumiSemanticColors
        @Composable get() = LocalLumiColors.current
}
