package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// CURATED THEME PALETTE CONSTANTS (from @uiuxaadi reference specs)
// ============================================================================

// --- 1. Ocean Blue Palette ---
// Ref card: Primary #0B1F3B, Accent #2563EB, Secondary #60A5FA, Surface #DBEAFE, Background #F0F7FF
val OceanBluePrimaryDark = Color(0xFF60A5FA)
val OceanBlueAccentDark = Color(0xFF2563EB)
val OceanBlueSecondaryDark = Color(0xFF93C5FD)
val OceanBlueSurfaceDark = Color(0xFF0E1B33)
val OceanBlueSurfaceElevatedDark = Color(0xFF152747)
val OceanBlueBackgroundDark = Color(0xFF070F1E)
val OceanBlueBorderDark = Color(0xFF1E3A5F)

val OceanBluePrimaryLight = Color(0xFF0B1F3B)
val OceanBlueAccentLight = Color(0xFF2563EB)
val OceanBlueSecondaryLight = Color(0xFF60A5FA)
val OceanBlueSurfaceLight = Color(0xFFDBEAFE)
val OceanBlueSurfaceElevatedLight = Color(0xFFBFDBFE)
val OceanBlueBackgroundLight = Color(0xFFF0F7FF)
val OceanBlueBorderLight = Color(0xFF93C5FD)

// --- 2. Royal Purple Palette ---
// Ref card: Primary #5B21B6, Accent #8B5CF6, Secondary #C7B3FF, Surface #EDE9FE, Background #FAF9FF
val RoyalPurplePrimaryDark = Color(0xFFA855F7)
val RoyalPurpleAccentDark = Color(0xFF8B5CF6)
val RoyalPurpleSecondaryDark = Color(0xFFC084FC)
val RoyalPurpleSurfaceDark = Color(0xFF141829)
val RoyalPurpleSurfaceElevatedDark = Color(0xFF1D2238)
val RoyalPurpleBackgroundDark = Color(0xFF08090F)
val RoyalPurpleBorderDark = Color(0xFF2E3654)

val RoyalPurplePrimaryLight = Color(0xFF5B21B6)
val RoyalPurpleAccentLight = Color(0xFF8B5CF6)
val RoyalPurpleSecondaryLight = Color(0xFFC7B3FF)
val RoyalPurpleSurfaceLight = Color(0xFFEDE9FE)
val RoyalPurpleSurfaceElevatedLight = Color(0xFFDDD6FE)
val RoyalPurpleBackgroundLight = Color(0xFFFAF9FF)
val RoyalPurpleBorderLight = Color(0xFFC4B5FD)

// --- 3. Aqua Mint Palette ---
// Ref card: Primary #042F2E, Accent #0D9488, Secondary #5EEAD4, Surface #D6FFFF, Background #ECFEFE
val AquaMintPrimaryDark = Color(0xFF5EEAD4)
val AquaMintAccentDark = Color(0xFF0D9488)
val AquaMintSecondaryDark = Color(0xFF99F6E4)
val AquaMintSurfaceDark = Color(0xFF0B2524)
val AquaMintSurfaceElevatedDark = Color(0xFF123836)
val AquaMintBackgroundDark = Color(0xFF041514)
val AquaMintBorderDark = Color(0xFF1A4744)

val AquaMintPrimaryLight = Color(0xFF042F2E)
val AquaMintAccentLight = Color(0xFF0D9488)
val AquaMintSecondaryLight = Color(0xFF5EEAD4)
val AquaMintSurfaceLight = Color(0xFFD6FFFF)
val AquaMintSurfaceElevatedLight = Color(0xFFCCFBF1)
val AquaMintBackgroundLight = Color(0xFFECFEFE)
val AquaMintBorderLight = Color(0xFF99F6E4)

// ============================================================================
// BASE TOKENS & BACKWARD COMPATIBILITY
// ============================================================================
val PurpleVoidBg = RoyalPurpleBackgroundDark
val PurpleCosmosBg = Color(0xFF0E111D)

val LiquidGlassSurface = RoyalPurpleSurfaceDark
val LiquidGlassElevated = RoyalPurpleSurfaceElevatedDark
val LiquidGlassPill = Color(0xFF252B45)

val GlassRimLightTop = Color(0x59FFFFFF)
val GlassRimLightMid = Color(0x668B5CF6)
val GlassRimLightBottom = Color(0x4038BDF8)
val GlassInnerSheen = Color(0x18FFFFFF)

val RoyalPurplePrimary = Color(0xFF8B5CF6)
val ElectricOrchid = Color(0xFFA855F7)
val SoftLavenderGlow = Color(0xFFC084FC)
val DeepAmethystPressed = Color(0xFF6D28D9)
val RoyalIndigoAccent = Color(0xFF6366F1)
val PaleLavenderIce = Color(0xFFF3E8FF)

val AccentSkyCyan = Color(0xFF38BDF8)
val AccentCyanBright = Color(0xFF22D3EE)
val AccentRosePink = Color(0xFFEC4899)
val VelvetRoseAccent = Color(0xFFF472B6)
val AccentChampagneGold = Color(0xFFF59E0B)
val AccentMintEmerald = Color(0xFF10B981)

val TextPureWhite = Color(0xFFF8FAFC)
val TextLavenderMuted = Color(0xFFCBD5E1)
val TextVioletDim = Color(0xFF94A3B8)

val StatusEmerald = Color(0xFF34D399)
val StatusAmber = Color(0xFFFBBF24)
val StatusRoseRed = Color(0xFFFB7185)

val JarvisMidnightBg = PurpleVoidBg
val JarvisNavyBgSecondary = PurpleCosmosBg
val JarvisGlassSurface = LiquidGlassSurface
val JarvisElevatedSurface = LiquidGlassElevated
val JarvisGlassBorder = Color(0xFF2E3654)
val JarvisGlassHighlight = Color(0xFF3E4870)

val JarvisViolet = RoyalPurplePrimary
val JarvisVioletBright = SoftLavenderGlow
val JarvisElectricBlue = RoyalIndigoAccent
val JarvisNeonCyan = AccentSkyCyan
val JarvisNeonGreen = AccentMintEmerald
val JarvisGreen = StatusEmerald
val JarvisSky = AccentSkyCyan
val JarvisPurpleDark = Color(0xFF191336)

val JarvisTextPrimary = TextPureWhite
val JarvisTextSecondary = TextLavenderMuted
val JarvisTextMuted = TextVioletDim

val HudBackgroundDark = PurpleVoidBg
val HudSurfaceDark = LiquidGlassSurface
val HudSurfaceVariantDark = LiquidGlassElevated
val HudCardBorder = JarvisGlassBorder

val ArcCyan = AccentSkyCyan
val ArcCyanBright = AccentCyanBright
val ArcCyanDim = RoyalIndigoAccent
val ElectricBlue = RoyalPurplePrimary
val IceBlue = PaleLavenderIce
val TelemetryTextPrimary = TextPureWhite
val TelemetryTextSecondary = TextLavenderMuted
val TelemetryTextMuted = TextVioletDim

val StatusIdleCyan = AccentSkyCyan
val StatusListeningEmerald = StatusEmerald
val StatusThinkingAmber = StatusAmber
val StatusSpeakingAzure = ElectricOrchid
val AlertCrimson = StatusRoseRed

val CobaltPrimary = OceanBlueAccentDark
val CobaltSecondary = OceanBlueSecondaryDark
val CobaltBackground = OceanBlueBackgroundDark
val CobaltSurface = OceanBlueSurfaceDark

val StealthPrimary = ElectricOrchid
val StealthSecondary = AccentRosePink
val StealthBackground = Color(0xFF05060A)
val StealthSurface = Color(0xFF101320)

val HighContrastPrimary = AquaMintPrimaryDark
val HighContrastSecondary = AquaMintAccentDark
val HighContrastBackground = AquaMintBackgroundDark
val HighContrastSurface = AquaMintSurfaceDark

// ============================================================================
// THEME ENUMS & EXTENDED COLOR STRUCTURE
// ============================================================================

enum class ThemePalette(val id: String, val displayName: String, val previewColor: Color, val accentHex: String) {
    OCEAN_BLUE("ocean_blue", "Ocean Blue", Color(0xFF2563EB), "#2563EB"),
    ROYAL_PURPLE("royal_purple", "Royal Purple", Color(0xFF8B5CF6), "#8B5CF6"),
    AQUA_MINT("aqua_mint", "Aqua Mint", Color(0xFF0D9488), "#0D9488");

    companion object {
        fun fromId(id: String?): ThemePalette {
            return entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: ROYAL_PURPLE
        }
    }
}

enum class ThemeDisplayMode(val id: String, val displayName: String) {
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    SYSTEM("system", "System Default");

    companion object {
        fun fromId(id: String?): ThemeDisplayMode {
            return entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: DARK
        }
    }
}

enum class JarvisThemeMode(
    val displayName: String,
    val subtitle: String = "",
    val palette: ThemePalette = ThemePalette.ROYAL_PURPLE,
    val displayMode: ThemeDisplayMode = ThemeDisplayMode.DARK
) {
    // 3 Curated Reference Presets
    OCEAN_BLUE("Ocean Blue", "Clean deep ocean blue with electric azure accents", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.DARK),
    ROYAL_PURPLE("Royal Purple", "Futuristic velvet purple with lavender accents", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.DARK),
    AQUA_MINT("Aqua Mint", "Cyberpunk mint emerald with aqua cyan accents", ThemePalette.AQUA_MINT, ThemeDisplayMode.DARK),

    OCEAN_BLUE_LIGHT("Ocean Blue Light", "Bright daylight ocean blue", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.LIGHT),
    OCEAN_BLUE_DARK("Ocean Blue Dark", "Deep midnight oceanic contrast", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.DARK),
    ROYAL_PURPLE_LIGHT("Royal Purple Light", "Bright daylight lilac royal purple", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.LIGHT),
    ROYAL_PURPLE_DARK("Royal Purple Dark", "Deep obsidian royal purple", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.DARK),
    AQUA_MINT_LIGHT("Aqua Mint Light", "Bright fresh daylight mint", ThemePalette.AQUA_MINT, ThemeDisplayMode.LIGHT),
    AQUA_MINT_DARK("Aqua Mint Dark", "Deep obsidian cyberpunk aqua", ThemePalette.AQUA_MINT, ThemeDisplayMode.DARK),
    SYSTEM_DEFAULT("System Default", "Matches Android device day/night setting", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.SYSTEM),

    // Backward-Compatible Presets
    ARC_CYAN("Royal Purple & Cyan", "Velvet royal purple with electric cyan accents", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.DARK),
    DEEP_COBALT("Deep Ocean Cobalt", "Deep ocean sapphire and cobalt", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.DARK),
    STEALTH_DARK("Stealth Obsidian", "Ultra-dark OLED obsidian with orchid accents", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.DARK),
    HIGH_CONTRAST("High Contrast Aqua", "Maximum contrast high visibility mint & cyan", ThemePalette.AQUA_MINT, ThemeDisplayMode.DARK);

    val label: String
        get() = displayName
}

data class JarvisThemeColors(
    val isDark: Boolean,
    val palette: ThemePalette,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfacePill: Color,
    val primary: Color,
    val accent: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderHighlight: Color,
    val glassRimTop: Color,
    val glassRimBottom: Color,
    val statusEmerald: Color = Color(0xFF10B981),
    val statusAmber: Color = Color(0xFFF59E0B),
    val statusRoseRed: Color = Color(0xFFEF4444)
)

val LocalJarvisColors = compositionLocalOf {
    getJarvisThemeColors(ThemePalette.ROYAL_PURPLE, isDark = true)
}

fun getJarvisThemeColors(palette: ThemePalette, isDark: Boolean): JarvisThemeColors {
    return if (isDark) {
        when (palette) {
            ThemePalette.OCEAN_BLUE -> JarvisThemeColors(
                isDark = true,
                palette = palette,
                background = OceanBlueBackgroundDark,
                surface = OceanBlueSurfaceDark,
                surfaceElevated = OceanBlueSurfaceElevatedDark,
                surfacePill = Color(0xFF1C3259),
                primary = OceanBluePrimaryDark,
                accent = OceanBlueAccentDark,
                secondary = OceanBlueSecondaryDark,
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFFCBD5E1),
                textMuted = Color(0xFF94A3B8),
                border = OceanBlueBorderDark,
                borderHighlight = Color(0xFF2563EB).copy(alpha = 0.5f),
                glassRimTop = Color(0x40FFFFFF),
                glassRimBottom = OceanBlueAccentDark.copy(alpha = 0.35f)
            )
            ThemePalette.ROYAL_PURPLE -> JarvisThemeColors(
                isDark = true,
                palette = palette,
                background = RoyalPurpleBackgroundDark,
                surface = RoyalPurpleSurfaceDark,
                surfaceElevated = RoyalPurpleSurfaceElevatedDark,
                surfacePill = LiquidGlassPill,
                primary = RoyalPurplePrimaryDark,
                accent = RoyalPurpleAccentDark,
                secondary = RoyalPurpleSecondaryDark,
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFFCBD5E1),
                textMuted = Color(0xFF94A3B8),
                border = RoyalPurpleBorderDark,
                borderHighlight = RoyalPurpleAccentDark.copy(alpha = 0.5f),
                glassRimTop = Color(0x50FFFFFF),
                glassRimBottom = RoyalPurpleAccentDark.copy(alpha = 0.35f)
            )
            ThemePalette.AQUA_MINT -> JarvisThemeColors(
                isDark = true,
                palette = palette,
                background = AquaMintBackgroundDark,
                surface = AquaMintSurfaceDark,
                surfaceElevated = AquaMintSurfaceElevatedDark,
                surfacePill = Color(0xFF174240),
                primary = AquaMintPrimaryDark,
                accent = AquaMintAccentDark,
                secondary = AquaMintSecondaryDark,
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFFCBD5E1),
                textMuted = Color(0xFF94A3B8),
                border = AquaMintBorderDark,
                borderHighlight = AquaMintAccentDark.copy(alpha = 0.5f),
                glassRimTop = Color(0x40FFFFFF),
                glassRimBottom = AquaMintAccentDark.copy(alpha = 0.35f)
            )
        }
    } else {
        when (palette) {
            ThemePalette.OCEAN_BLUE -> JarvisThemeColors(
                isDark = false,
                palette = palette,
                background = OceanBlueBackgroundLight,
                surface = OceanBlueSurfaceLight,
                surfaceElevated = OceanBlueSurfaceElevatedLight,
                surfacePill = Color(0xFFDBEAFE),
                primary = OceanBluePrimaryLight,
                accent = OceanBlueAccentLight,
                secondary = OceanBlueSecondaryLight,
                textPrimary = OceanBluePrimaryLight,
                textSecondary = Color(0xFF334155),
                textMuted = Color(0xFF64748B),
                border = OceanBlueBorderLight,
                borderHighlight = OceanBlueAccentLight.copy(alpha = 0.5f),
                glassRimTop = Color(0x60FFFFFF),
                glassRimBottom = OceanBlueAccentLight.copy(alpha = 0.25f)
            )
            ThemePalette.ROYAL_PURPLE -> JarvisThemeColors(
                isDark = false,
                palette = palette,
                background = RoyalPurpleBackgroundLight,
                surface = RoyalPurpleSurfaceLight,
                surfaceElevated = RoyalPurpleSurfaceElevatedLight,
                surfacePill = Color(0xFFEDE9FE),
                primary = RoyalPurplePrimaryLight,
                accent = RoyalPurpleAccentLight,
                secondary = RoyalPurpleSecondaryLight,
                textPrimary = RoyalPurplePrimaryLight,
                textSecondary = Color(0xFF334155),
                textMuted = Color(0xFF64748B),
                border = RoyalPurpleBorderLight,
                borderHighlight = RoyalPurpleAccentLight.copy(alpha = 0.5f),
                glassRimTop = Color(0x60FFFFFF),
                glassRimBottom = RoyalPurpleAccentLight.copy(alpha = 0.25f)
            )
            ThemePalette.AQUA_MINT -> JarvisThemeColors(
                isDark = false,
                palette = palette,
                background = AquaMintBackgroundLight,
                surface = AquaMintSurfaceLight,
                surfaceElevated = AquaMintSurfaceElevatedLight,
                surfacePill = Color(0xFFD6FFFF),
                primary = AquaMintPrimaryLight,
                accent = AquaMintAccentLight,
                secondary = AquaMintSecondaryLight,
                textPrimary = AquaMintPrimaryLight,
                textSecondary = Color(0xFF334155),
                textMuted = Color(0xFF64748B),
                border = AquaMintBorderLight,
                borderHighlight = AquaMintAccentLight.copy(alpha = 0.5f),
                glassRimTop = Color(0x60FFFFFF),
                glassRimBottom = AquaMintAccentLight.copy(alpha = 0.25f)
            )
        }
    }
}
