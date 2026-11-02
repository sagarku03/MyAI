package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class JarvisThemeMode(val label: String, val subtitle: String) {
    ARC_CYAN("Nebula Cyan & Violet", "Midnight glassmorphism with neon cyan & electric purple aura"),
    DEEP_COBALT("Electric Cobalt Glass", "Deep sapphire glass panels with royal blue & violet accents"),
    STEALTH_DARK("Obsidian Emerald", "Ultra-dark AMOLED glass with neon green & teal highlights"),
    HIGH_CONTRAST("Hyper-Luminous Pro", "High-contrast neon cyan & lime glassmorphic precision")
}

private val ArcCyanColorScheme = darkColorScheme(
    primary = JarvisNeonCyan,
    onPrimary = Color(0xFF04131A),
    primaryContainer = Color(0xFF152238),
    onPrimaryContainer = JarvisTextPrimary,
    secondary = JarvisViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E1B3A),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = JarvisElectricBlue,
    onTertiary = Color.White,
    background = JarvisMidnightBg,
    onBackground = JarvisTextPrimary,
    surface = JarvisGlassSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisElevatedSurface,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisGlassBorder,
    error = AlertCrimson,
    onError = Color.White
)

private val DeepCobaltColorScheme = darkColorScheme(
    primary = JarvisElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF132347),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = JarvisVioletBright,
    onSecondary = Color(0xFF140B2E),
    secondaryContainer = Color(0xFF1F1942),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = JarvisNeonCyan,
    background = CobaltBackground,
    onBackground = JarvisTextPrimary,
    surface = CobaltSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = Color(0xFF17223B),
    onSurfaceVariant = JarvisTextSecondary,
    outline = Color(0xFF28375C),
    error = AlertCrimson
)

private val StealthDarkColorScheme = darkColorScheme(
    primary = StealthPrimary,
    onPrimary = Color(0xFF031C18),
    primaryContainer = Color(0xFF0E2925),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = JarvisNeonGreen,
    onSecondary = Color(0xFF111C02),
    secondaryContainer = Color(0xFF1A2908),
    onSecondaryContainer = Color(0xFFECFCCB),
    tertiary = JarvisElectricBlue,
    background = StealthBackground,
    onBackground = JarvisTextPrimary,
    surface = StealthSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = Color(0xFF131926),
    onSurfaceVariant = JarvisTextSecondary,
    outline = Color(0xFF222D40),
    error = AlertCrimson
)

private val HighContrastColorScheme = darkColorScheme(
    primary = HighContrastPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0B2F3B),
    onPrimaryContainer = Color.White,
    secondary = HighContrastSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF233805),
    onSecondaryContainer = Color.White,
    tertiary = JarvisVioletBright,
    background = HighContrastBackground,
    onBackground = Color.White,
    surface = HighContrastSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF172136),
    onSurfaceVariant = Color(0xFFD8E6F8),
    outline = Color(0xFF38BDF8),
    error = Color(0xFFFF5252)
)

@Composable
fun JarvisHudTheme(
    themeMode: JarvisThemeMode = JarvisThemeMode.ARC_CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        JarvisThemeMode.ARC_CYAN -> ArcCyanColorScheme
        JarvisThemeMode.DEEP_COBALT -> DeepCobaltColorScheme
        JarvisThemeMode.STEALTH_DARK -> StealthDarkColorScheme
        JarvisThemeMode.HIGH_CONTRAST -> HighContrastColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    JarvisHudTheme(themeMode = JarvisThemeMode.ARC_CYAN, content = content)
}
