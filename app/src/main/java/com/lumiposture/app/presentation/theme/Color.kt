package com.lumiposture.app.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Mode Palette (Cool Alabaster Canvas + Clinical Blue)
val LightCanvas = Color(0xFFF4F5F7)
val LightCardSurface = Color(0xFFFFFFFF)
val LightGlassTint = Color(0xB8FFFFFF) // 72% white frosted surface
val LightGlassBorder = Color(0xA6FFFFFF) // 65% white hairline
val LightOutlineSubtle = Color(0x0D000000) // 5% black outer hairline
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextTertiary = Color(0xFF64748B)
val LightSurfaceVariant = Color(0xFFE8ECF2)

val LightPrimary = Color(0xFF1D63ED)
val LightOptimal = Color(0xFF16A34A)
val LightWarning = Color(0xFFD97706)
val LightCritical = Color(0xFFDC2626)

// Dark Mode Palette (Obsidian Slate Canvas + Luminous Clinical Blue)
val DarkCanvas = Color(0xFF0B0D11)
val DarkCardSurface = Color(0xFF14171F)
val DarkGlassTint = Color(0xAD181C26) // 68% translucent slate glass
val DarkGlassBorder = Color(0x1FFFFFFF) // 12% white hairline
val DarkOutlineSubtle = Color(0x14FFFFFF)
val DarkTextPrimary = Color(0xFFF1F5F9)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextTertiary = Color(0xFF64748B)
val DarkSurfaceVariant = Color(0xFF1E2330)

val DarkPrimary = Color(0xFF4B8BFF)
val DarkOptimal = Color(0xFF34D399)
val DarkWarning = Color(0xFFFBBF24)
val DarkCritical = Color(0xFFF87171)

// Camera HUD overlay constants (over dark live camera feed)
val HudGlassTint = Color(0xA612151C)
val HudGlassBorder = Color(0x2EFFFFFF)
val HudTextPrimary = Color(0xFFF8FAFC)
val HudTextSecondary = Color(0xFFCBD5E1)

@Immutable
data class LumiSemanticColors(
    val canvas: Color,
    val cardSurface: Color,
    val glassTint: Color,
    val glassBorder: Color,
    val subtleOutline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val optimal: Color,
    val warning: Color,
    val critical: Color,
    val isDark: Boolean,
)

val LightLumiColors = LumiSemanticColors(
    canvas = LightCanvas,
    cardSurface = LightCardSurface,
    glassTint = LightGlassTint,
    glassBorder = LightGlassBorder,
    subtleOutline = LightOutlineSubtle,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    primary = LightPrimary,
    optimal = LightOptimal,
    warning = LightWarning,
    critical = LightCritical,
    isDark = false,
)

val DarkLumiColors = LumiSemanticColors(
    canvas = DarkCanvas,
    cardSurface = DarkCardSurface,
    glassTint = DarkGlassTint,
    glassBorder = DarkGlassBorder,
    subtleOutline = DarkOutlineSubtle,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    primary = DarkPrimary,
    optimal = DarkOptimal,
    warning = DarkWarning,
    critical = DarkCritical,
    isDark = true,
)

val LocalLumiColors = staticCompositionLocalOf { LightLumiColors }
