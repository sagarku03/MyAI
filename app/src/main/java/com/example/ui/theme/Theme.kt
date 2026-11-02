package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class JarvisThemeMode(val label: String, val subtitle: String) {
    ARC_CYAN("Arc Reactor Cyan", "Classic glowing cyan & deep space obsidian HUD"),
    DEEP_COBALT("Deep Tactical Cobalt", "Royal electric blue telemetry with midnight navy core"),
    STEALTH_DARK("Stealth Obsidian", "Ultra-dark AMOLED surface with muted teal accents"),
    HIGH_CONTRAST("High-Contrast Spec", "Maximum legibility neon cyan & yellow tactical overlay")
}

private val ArcCyanColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = Color(0xFF003844),
    onPrimaryContainer = IceBlue,
    secondary = StatusSpeakingAzure,
    onSecondary = Color(0xFF001E2F),
    secondaryContainer = Color(0xFF092A44),
    onSecondaryContainer = Color(0xFFC7E7FF),
    tertiary = StatusThinkingAmber,
    onTertiary = Color(0xFF261900),
    background = HudBackgroundDark,
    onBackground = TelemetryTextPrimary,
    surface = HudSurfaceDark,
    onSurface = TelemetryTextPrimary,
    surfaceVariant = HudSurfaceVariantDark,
    onSurfaceVariant = TelemetryTextSecondary,
    outline = HudCardBorder,
    error = AlertCrimson,
    onError = Color.White
)

private val DeepCobaltColorScheme = darkColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F2952),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = CobaltSecondary,
    onSecondary = Color(0xFF001A40),
    secondaryContainer = Color(0xFF13274F),
    onSecondaryContainer = Color(0xFFDCE7FF),
    tertiary = ArcCyan,
    background = CobaltBackground,
    onBackground = TelemetryTextPrimary,
    surface = CobaltSurface,
    onSurface = TelemetryTextPrimary,
    surfaceVariant = Color(0xFF14244C),
    onSurfaceVariant = Color(0xFF9EB8E6),
    outline = Color(0xFF244380),
    error = AlertCrimson
)

private val StealthDarkColorScheme = darkColorScheme(
    primary = StealthPrimary,
    onPrimary = Color(0xFF001F24),
    primaryContainer = Color(0xFF002F38),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = StealthSecondary,
    onSecondary = Color(0xFF00201A),
    secondaryContainer = Color(0xFF072924),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = StatusThinkingAmber,
    background = StealthBackground,
    onBackground = Color(0xFFD8ECF2),
    surface = StealthSurface,
    onSurface = Color(0xFFD8ECF2),
    surfaceVariant = Color(0xFF0D1824),
    onSurfaceVariant = Color(0xFF7898AD),
    outline = Color(0xFF142C3D),
    error = AlertCrimson
)

private val HighContrastColorScheme = darkColorScheme(
    primary = HighContrastPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004D4D),
    onPrimaryContainer = Color.White,
    secondary = HighContrastSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF333300),
    onSecondaryContainer = Color.White,
    tertiary = StatusListeningEmerald,
    background = HighContrastBackground,
    onBackground = Color.White,
    surface = HighContrastSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF102236),
    onSurfaceVariant = Color(0xFFCCEEFF),
    outline = HighContrastPrimary,
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
