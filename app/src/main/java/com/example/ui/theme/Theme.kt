package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

object JarvisTheme {
    val colors: JarvisThemeColors
        @Composable
        get() = LocalJarvisColors.current
}

// Apple iOS 26 Continuous Squircle Curvature System
val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun JarvisHudTheme(
    themeMode: JarvisThemeMode = JarvisThemeMode.ROYAL_PURPLE,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode.displayMode) {
        ThemeDisplayMode.LIGHT -> false
        ThemeDisplayMode.DARK -> true
        ThemeDisplayMode.SYSTEM -> systemInDark
    }

    val palette = themeMode.palette
    val themeColors = getJarvisThemeColors(palette, isDark)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = themeColors.primary,
            onPrimary = themeColors.onPrimary,
            primaryContainer = themeColors.surfaceElevated,
            onPrimaryContainer = themeColors.textPrimary,
            secondary = themeColors.accent,
            onSecondary = themeColors.onPrimary,
            secondaryContainer = themeColors.surfacePill,
            onSecondaryContainer = themeColors.textPrimary,
            tertiary = themeColors.secondary,
            onTertiary = themeColors.onPrimary,
            background = themeColors.background,
            onBackground = themeColors.textPrimary,
            surface = themeColors.surface,
            onSurface = themeColors.textPrimary,
            surfaceVariant = themeColors.surfaceElevated,
            onSurfaceVariant = themeColors.textSecondary,
            outline = themeColors.border,
            error = themeColors.statusRoseRed,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = themeColors.primary,
            onPrimary = themeColors.onPrimary,
            primaryContainer = themeColors.surfaceElevated,
            onPrimaryContainer = themeColors.textPrimary,
            secondary = themeColors.accent,
            onSecondary = themeColors.onPrimary,
            secondaryContainer = themeColors.surfacePill,
            onSecondaryContainer = themeColors.textPrimary,
            tertiary = themeColors.secondary,
            onTertiary = themeColors.onPrimary,
            background = themeColors.background,
            onBackground = themeColors.textPrimary,
            surface = themeColors.surface,
            onSurface = themeColors.textPrimary,
            surfaceVariant = themeColors.surfaceElevated,
            onSurfaceVariant = themeColors.textSecondary,
            outline = themeColors.border,
            error = themeColors.statusRoseRed,
            onError = Color.White
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalJarvisColors provides themeColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = JarvisShapes,
            content = content
        )
    }
}

@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    JarvisHudTheme(content = content)
}
