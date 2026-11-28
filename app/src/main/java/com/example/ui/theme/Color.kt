package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// AGENTRA PRECISION WORKSTATION PALETTE SYSTEM (Calibrated Matte Surfaces)
// ============================================================================

// --- 0. Graphite Workstation (Default Neutral Matte) ---
val GraphitePrimaryDark = Color(0xFF5B8DEF)
val GraphiteAccentDark = Color(0xFF5B8DEF)
val GraphiteSecondaryDark = Color(0xFF8AB4F8)
val GraphiteSurfaceDark = Color(0xFF15171C)
val GraphiteSurfaceElevatedDark = Color(0xFF1D2027)
val GraphiteBackgroundDark = Color(0xFF0D0E12)
val GraphiteBorderDark = Color(0xFF2A2E38)

val GraphitePrimaryLight = Color(0xFF2563EB)
val GraphiteAccentLight = Color(0xFF2563EB)
val GraphiteSecondaryLight = Color(0xFF3B82F6)
val GraphiteSurfaceLight = Color(0xFFFFFFFF)
val GraphiteSurfaceElevatedLight = Color(0xFFF0F2F5)
val GraphiteBackgroundLight = Color(0xFFF7F8FA)
val GraphiteBorderLight = Color(0xFFDCE0E7)

// --- 1. Ocean Blue Palette ---
val OceanBluePrimaryDark = Color(0xFF60A5FA)
val OceanBlueAccentDark = Color(0xFF3B82F6)
val OceanBlueSecondaryDark = Color(0xFF93C5FD)
val OceanBlueSurfaceDark = Color(0xFF131720)
val OceanBlueSurfaceElevatedDark = Color(0xFF1B202C)
val OceanBlueBackgroundDark = Color(0xFF0C0F14)
val OceanBlueBorderDark = Color(0xFF263042)

val OceanBluePrimaryLight = Color(0xFF1D4ED8)
val OceanBlueAccentLight = Color(0xFF2563EB)
val OceanBlueSecondaryLight = Color(0xFF60A5FA)
val OceanBlueSurfaceLight = Color(0xFFFFFFFF)
val OceanBlueSurfaceElevatedLight = Color(0xFFEFF4FC)
val OceanBlueBackgroundLight = Color(0xFFF6F8FC)
val OceanBlueBorderLight = Color(0xFFD1DDF3)

// --- 2. Royal Purple Palette (Restrained Violet on Neutral Slate) ---
val RoyalPurplePrimaryDark = Color(0xFF9B87F5)
val RoyalPurpleAccentDark = Color(0xFF8B74EB)
val RoyalPurpleSecondaryDark = Color(0xFFB8A9F7)
val RoyalPurpleSurfaceDark = Color(0xFF15141C)
val RoyalPurpleSurfaceElevatedDark = Color(0xFF1D1C26)
val RoyalPurpleBackgroundDark = Color(0xFF0D0C12)
val RoyalPurpleBorderDark = Color(0xFF2C2A3A)

val RoyalPurplePrimaryLight = Color(0xFF6D28D9)
val RoyalPurpleAccentLight = Color(0xFF7C3AED)
val RoyalPurpleSecondaryLight = Color(0xFFA78BFA)
val RoyalPurpleSurfaceLight = Color(0xFFFFFFFF)
val RoyalPurpleSurfaceElevatedLight = Color(0xFFF3F1FC)
val RoyalPurpleBackgroundLight = Color(0xFFF9F8FC)
val RoyalPurpleBorderLight = Color(0xFFDFD9F4)

// --- 3. Aqua Mint Palette (Calm Sage Teal on Neutral Charcoal) ---
val AquaMintPrimaryDark = Color(0xFF2DD4BF)
val AquaMintAccentDark = Color(0xFF14B8A6)
val AquaMintSecondaryDark = Color(0xFF5EEAD4)
val AquaMintSurfaceDark = Color(0xFF131A19)
val AquaMintSurfaceElevatedDark = Color(0xFF1A2322)
val AquaMintBackgroundDark = Color(0xFF0C1110)
val AquaMintBorderDark = Color(0xFF253533)

val AquaMintPrimaryLight = Color(0xFF0F766E)
val AquaMintAccentLight = Color(0xFF0D9488)
val AquaMintSecondaryLight = Color(0xFF2DD4BF)
val AquaMintSurfaceLight = Color(0xFFFFFFFF)
val AquaMintSurfaceElevatedLight = Color(0xFFEEF7F6)
val AquaMintBackgroundLight = Color(0xFFF5FAFA)
val AquaMintBorderLight = Color(0xFFCCE3E0)

// ============================================================================
// BASE TOKENS & BACKWARD COMPATIBILITY
// ============================================================================
val PurpleVoidBg = GraphiteBackgroundDark
val PurpleCosmosBg = GraphiteSurfaceDark

val LiquidGlassSurface = GraphiteSurfaceDark
val LiquidGlassElevated = GraphiteSurfaceElevatedDark
val LiquidGlassPill = Color(0xFF252932)

val GlassRimLightTop = GraphiteBorderDark
val GlassRimLightMid = GraphiteBorderDark
val GlassRimLightBottom = GraphiteBorderDark
val GlassInnerSheen = Color(0x0DFFFFFF)

val RoyalPurplePrimary = Color(0xFF5B8DEF)
val ElectricOrchid = Color(0xFF8B74EB)
val SoftLavenderGlow = Color(0xFF8AB4F8)
val DeepAmethystPressed = Color(0xFF3B6FD8)
val RoyalIndigoAccent = Color(0xFF5B8DEF)
val PaleLavenderIce = Color(0xFFECEEF2)

val AccentSkyCyan = Color(0xFF5B8DEF)
val AccentCyanBright = Color(0xFF38BDF8)
val AccentRosePink = Color(0xFFE5484D)
val VelvetRoseAccent = Color(0xFFEF4444)
val AccentChampagneGold = Color(0xFFD97706)
val AccentMintEmerald = Color(0xFF22C55E)

val TextPureWhite = Color(0xFFECEEF2)
val TextLavenderMuted = Color(0xFF98A0AE)
val TextVioletDim = Color(0xFF6B7280)

val StatusEmerald = Color(0xFF22C55E)
val StatusAmber = Color(0xFFEAB308)
val StatusRoseRed = Color(0xFFEF4444)

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
    GRAPHITE("graphite", "Graphite", Color(0xFF5B8DEF), "#5B8DEF"),
    OCEAN_BLUE("ocean_blue", "Ocean", Color(0xFF3B82F6), "#3B82F6"),
    ROYAL_PURPLE("royal_purple", "Violet", Color(0xFF8B74EB), "#8B74EB"),
    AQUA_MINT("aqua_mint", "Teal", Color(0xFF14B8A6), "#14B8A6");

    companion object {
        fun fromId(id: String?): ThemePalette {
            return entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: GRAPHITE
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
    val palette: ThemePalette = ThemePalette.GRAPHITE,
    val displayMode: ThemeDisplayMode = ThemeDisplayMode.DARK
) {
    // Primary Workstation Presets
    GRAPHITE("Graphite", "Matte charcoal surfaces with calm cobalt focus", ThemePalette.GRAPHITE, ThemeDisplayMode.DARK),
    GRAPHITE_LIGHT("Graphite Light", "Warm paper light canvas with crisp cobalt focus", ThemePalette.GRAPHITE, ThemeDisplayMode.LIGHT),
    GRAPHITE_DARK("Graphite Dark", "Matte charcoal canvas with restrained cobalt focus", ThemePalette.GRAPHITE, ThemeDisplayMode.DARK),

    // Curated Reference Presets
    OCEAN_BLUE("Ocean", "Deep slate blue with restrained azure accents", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.DARK),
    ROYAL_PURPLE("Violet", "Neutral slate with muted violet accents", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.DARK),
    AQUA_MINT("Teal", "Charcoal surfaces with calm teal accents", ThemePalette.AQUA_MINT, ThemeDisplayMode.DARK),

    OCEAN_BLUE_LIGHT("Ocean Light", "Clean daylight blue palette", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.LIGHT),
    OCEAN_BLUE_DARK("Ocean Dark", "Deep slate ocean contrast", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.DARK),
    ROYAL_PURPLE_LIGHT("Violet Light", "Clean daylight violet palette", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.LIGHT),
    ROYAL_PURPLE_DARK("Violet Dark", "Neutral slate with muted violet", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.DARK),
    AQUA_MINT_LIGHT("Teal Light", "Clean daylight teal palette", ThemePalette.AQUA_MINT, ThemeDisplayMode.LIGHT),
    AQUA_MINT_DARK("Teal Dark", "Charcoal surfaces with calm teal", ThemePalette.AQUA_MINT, ThemeDisplayMode.DARK),
    SYSTEM_DEFAULT("System Default", "Matches Android device day/night setting", ThemePalette.GRAPHITE, ThemeDisplayMode.SYSTEM),

    // Backward-Compatible Presets
    ARC_CYAN("Graphite Slate", "Neutral charcoal with slate blue accents", ThemePalette.GRAPHITE, ThemeDisplayMode.DARK),
    DEEP_COBALT("Deep Ocean Cobalt", "Deep ocean sapphire and cobalt", ThemePalette.OCEAN_BLUE, ThemeDisplayMode.DARK),
    STEALTH_DARK("Stealth Charcoal", "Dark matte charcoal with subtle violet accents", ThemePalette.ROYAL_PURPLE, ThemeDisplayMode.DARK),
    HIGH_CONTRAST("High Contrast Teal", "High-legibility teal on matte charcoal", ThemePalette.AQUA_MINT, ThemeDisplayMode.DARK);

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
    val cardSurface: Color = surface,
    val cardBorder: Color = border,
    val onPrimary: Color = Color.White,
    val codeBackground: Color = if (isDark) Color(0xFF101216) else Color(0xFFF3F4F6),
    val codeHeader: Color = if (isDark) Color(0xFF181B22) else Color(0xFFE5E7EB),
    val codeText: Color = if (isDark) Color(0xFFE5E7EB) else Color(0xFF111827),
    val inputBackground: Color = surfaceElevated,
    val chatBubbleUser: Color = primary,
    val chatBubbleAssistant: Color = surfaceElevated,
    val divider: Color = border,
    val statusEmerald: Color = if (isDark) Color(0xFF22C55E) else Color(0xFF16A34A),
    val statusAmber: Color = if (isDark) Color(0xFFEAB308) else Color(0xFFD97706),
    val statusRoseRed: Color = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
)

val LocalJarvisColors = compositionLocalOf {
    getJarvisThemeColors(ThemePalette.GRAPHITE, isDark = true)
}

fun getJarvisThemeColors(palette: ThemePalette, isDark: Boolean): JarvisThemeColors {
    return if (isDark) {
        when (palette) {
            ThemePalette.GRAPHITE -> JarvisThemeColors(
                isDark = true,
                palette = palette,
                background = GraphiteBackgroundDark,
                surface = GraphiteSurfaceDark,
                surfaceElevated = GraphiteSurfaceElevatedDark,
                surfacePill = Color(0xFF252932),
                cardSurface = GraphiteSurfaceDark,
                cardBorder = GraphiteBorderDark,
                primary = GraphitePrimaryDark,
                onPrimary = Color.White,
                accent = GraphiteAccentDark,
                secondary = GraphiteSecondaryDark,
                textPrimary = Color(0xFFECEEF2),
                textSecondary = Color(0xFF98A0AE),
                textMuted = Color(0xFF6B7280),
                border = GraphiteBorderDark,
                borderHighlight = GraphiteAccentDark.copy(alpha = 0.45f),
                glassRimTop = GraphiteBorderDark,
                glassRimBottom = GraphiteBorderDark,
                codeBackground = Color(0xFF101216),
                codeHeader = Color(0xFF181B22),
                codeText = Color(0xFFE5E7EB),
                inputBackground = GraphiteSurfaceElevatedDark,
                chatBubbleUser = GraphiteAccentDark,
                chatBubbleAssistant = GraphiteSurfaceElevatedDark,
                divider = GraphiteBorderDark
            )
            ThemePalette.OCEAN_BLUE -> JarvisThemeColors(
                isDark = true,
                palette = palette,
                background = OceanBlueBackgroundDark,
                surface = OceanBlueSurfaceDark,
                surfaceElevated = OceanBlueSurfaceElevatedDark,
                surfacePill = Color(0xFF222A3A),
                cardSurface = OceanBlueSurfaceDark,
                cardBorder = OceanBlueBorderDark,
                primary = OceanBluePrimaryDark,
                onPrimary = Color.White,
                accent = OceanBlueAccentDark,
                secondary = OceanBlueSecondaryDark,
                textPrimary = Color(0xFFECEEF2),
                textSecondary = Color(0xFF9BA5B4),
                textMuted = Color(0xFF6B7280),
                border = OceanBlueBorderDark,
                borderHighlight = OceanBlueAccentDark.copy(alpha = 0.45f),
                glassRimTop = OceanBlueBorderDark,
                glassRimBottom = OceanBlueBorderDark,
                codeBackground = Color(0xFF101216),
                codeHeader = Color(0xFF181B22),
                codeText = Color(0xFFE5E7EB),
                inputBackground = OceanBlueSurfaceElevatedDark,
                chatBubbleUser = OceanBlueAccentDark,
                chatBubbleAssistant = OceanBlueSurfaceElevatedDark,
                divider = OceanBlueBorderDark
            )
            ThemePalette.ROYAL_PURPLE -> JarvisThemeColors(
                isDark = true,
                palette = palette,
                background = RoyalPurpleBackgroundDark,
                surface = RoyalPurpleSurfaceDark,
                surfaceElevated = RoyalPurpleSurfaceElevatedDark,
                surfacePill = Color(0xFF272534),
                cardSurface = RoyalPurpleSurfaceDark,
                cardBorder = RoyalPurpleBorderDark,
                primary = RoyalPurplePrimaryDark,
                onPrimary = Color.White,
                accent = RoyalPurpleAccentDark,
                secondary = RoyalPurpleSecondaryDark,
                textPrimary = Color(0xFFECEEF2),
                textSecondary = Color(0xFF9E9AAE),
                textMuted = Color(0xFF6F6C7E),
                border = RoyalPurpleBorderDark,
                borderHighlight = RoyalPurpleAccentDark.copy(alpha = 0.45f),
                glassRimTop = RoyalPurpleBorderDark,
                glassRimBottom = RoyalPurpleBorderDark,
                codeBackground = Color(0xFF101216),
                codeHeader = Color(0xFF181B22),
                codeText = Color(0xFFE5E7EB),
                inputBackground = RoyalPurpleSurfaceElevatedDark,
                chatBubbleUser = RoyalPurpleAccentDark,
                chatBubbleAssistant = RoyalPurpleSurfaceElevatedDark,
                divider = RoyalPurpleBorderDark
            )
            ThemePalette.AQUA_MINT -> JarvisThemeColors(
                isDark = true,
                palette = palette,
                background = AquaMintBackgroundDark,
                surface = AquaMintSurfaceDark,
                surfaceElevated = AquaMintSurfaceElevatedDark,
                surfacePill = Color(0xFF212D2B),
                cardSurface = AquaMintSurfaceDark,
                cardBorder = AquaMintBorderDark,
                primary = AquaMintPrimaryDark,
                onPrimary = Color(0xFF0A1413),
                accent = AquaMintAccentDark,
                secondary = AquaMintSecondaryDark,
                textPrimary = Color(0xFFECEEF2),
                textSecondary = Color(0xFF96A6A3),
                textMuted = Color(0xFF667572),
                border = AquaMintBorderDark,
                borderHighlight = AquaMintAccentDark.copy(alpha = 0.45f),
                glassRimTop = AquaMintBorderDark,
                glassRimBottom = AquaMintBorderDark,
                codeBackground = Color(0xFF101216),
                codeHeader = Color(0xFF181B22),
                codeText = Color(0xFFE5E7EB),
                inputBackground = AquaMintSurfaceElevatedDark,
                chatBubbleUser = AquaMintAccentDark,
                chatBubbleAssistant = AquaMintSurfaceElevatedDark,
                divider = AquaMintBorderDark
            )
        }
    } else {
        when (palette) {
            ThemePalette.GRAPHITE -> JarvisThemeColors(
                isDark = false,
                palette = palette,
                background = GraphiteBackgroundLight,
                surface = GraphiteSurfaceLight,
                surfaceElevated = GraphiteSurfaceElevatedLight,
                surfacePill = Color(0xFFE5E8EE),
                cardSurface = GraphiteSurfaceLight,
                cardBorder = GraphiteBorderLight,
                primary = GraphitePrimaryLight,
                onPrimary = Color.White,
                accent = GraphiteAccentLight,
                secondary = GraphiteSecondaryLight,
                textPrimary = Color(0xFF111318),
                textSecondary = Color(0xFF4B5563),
                textMuted = Color(0xFF6B7280),
                border = GraphiteBorderLight,
                borderHighlight = GraphiteAccentLight.copy(alpha = 0.35f),
                glassRimTop = GraphiteBorderLight,
                glassRimBottom = GraphiteBorderLight,
                codeBackground = Color(0xFFF3F4F6),
                codeHeader = Color(0xFFE5E7EB),
                codeText = Color(0xFF111827),
                inputBackground = Color(0xFFFFFFFF),
                chatBubbleUser = GraphiteAccentLight,
                chatBubbleAssistant = Color(0xFFFFFFFF),
                divider = GraphiteBorderLight
            )
            ThemePalette.OCEAN_BLUE -> JarvisThemeColors(
                isDark = false,
                palette = palette,
                background = OceanBlueBackgroundLight,
                surface = OceanBlueSurfaceLight,
                surfaceElevated = OceanBlueSurfaceElevatedLight,
                surfacePill = Color(0xFFE2EAFB),
                cardSurface = Color(0xFFFFFFFF),
                cardBorder = OceanBlueBorderLight,
                primary = OceanBluePrimaryLight,
                onPrimary = Color.White,
                accent = OceanBlueAccentLight,
                secondary = OceanBlueSecondaryLight,
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF475569),
                textMuted = Color(0xFF64748B),
                border = OceanBlueBorderLight,
                borderHighlight = OceanBlueAccentLight.copy(alpha = 0.35f),
                glassRimTop = OceanBlueBorderLight,
                glassRimBottom = OceanBlueBorderLight,
                codeBackground = Color(0xFFF3F4F6),
                codeHeader = Color(0xFFE5E7EB),
                codeText = Color(0xFF111827),
                inputBackground = Color(0xFFFFFFFF),
                chatBubbleUser = OceanBlueAccentLight,
                chatBubbleAssistant = Color(0xFFFFFFFF),
                divider = OceanBlueBorderLight
            )
            ThemePalette.ROYAL_PURPLE -> JarvisThemeColors(
                isDark = false,
                palette = palette,
                background = RoyalPurpleBackgroundLight,
                surface = RoyalPurpleSurfaceLight,
                surfaceElevated = RoyalPurpleSurfaceElevatedLight,
                surfacePill = Color(0xFFECE8FA),
                cardSurface = Color(0xFFFFFFFF),
                cardBorder = RoyalPurpleBorderLight,
                primary = RoyalPurplePrimaryLight,
                onPrimary = Color.White,
                accent = RoyalPurpleAccentLight,
                secondary = RoyalPurpleSecondaryLight,
                textPrimary = Color(0xFF14121D),
                textSecondary = Color(0xFF4B475C),
                textMuted = Color(0xFF6D6880),
                border = RoyalPurpleBorderLight,
                borderHighlight = RoyalPurpleAccentLight.copy(alpha = 0.35f),
                glassRimTop = RoyalPurpleBorderLight,
                glassRimBottom = RoyalPurpleBorderLight,
                codeBackground = Color(0xFFF3F4F6),
                codeHeader = Color(0xFFE5E7EB),
                codeText = Color(0xFF111827),
                inputBackground = Color(0xFFFFFFFF),
                chatBubbleUser = RoyalPurplePrimaryLight,
                chatBubbleAssistant = Color(0xFFFFFFFF),
                divider = RoyalPurpleBorderLight
            )
            ThemePalette.AQUA_MINT -> JarvisThemeColors(
                isDark = false,
                palette = palette,
                background = AquaMintBackgroundLight,
                surface = AquaMintSurfaceLight,
                surfaceElevated = AquaMintSurfaceElevatedLight,
                surfacePill = Color(0xFFE1F2EF),
                cardSurface = Color(0xFFFFFFFF),
                cardBorder = AquaMintBorderLight,
                primary = AquaMintPrimaryLight,
                onPrimary = Color.White,
                accent = AquaMintAccentLight,
                secondary = AquaMintSecondaryLight,
                textPrimary = Color(0xFF0E1D1B),
                textSecondary = Color(0xFF425955),
                textMuted = Color(0xFF647D79),
                border = AquaMintBorderLight,
                borderHighlight = AquaMintAccentLight.copy(alpha = 0.35f),
                glassRimTop = AquaMintBorderLight,
                glassRimBottom = AquaMintBorderLight,
                codeBackground = Color(0xFFF3F4F6),
                codeHeader = Color(0xFFE5E7EB),
                codeText = Color(0xFF111827),
                inputBackground = Color(0xFFFFFFFF),
                chatBubbleUser = AquaMintAccentLight,
                chatBubbleAssistant = Color(0xFFFFFFFF),
                divider = AquaMintBorderLight
            )
        }
    }
}
