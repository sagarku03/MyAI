package com.jarvisai.assistant.ui.screens.browser

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import java.util.UUID

object JarvisBrowserColors {
    val BgDark = Color(0xFF0D0E12)
    val Surface = Color(0xFF15171C)
    val SurfaceElevated = Color(0xFF1D2027)
    val CardBorder = Color(0xFF2A2E38)
    val Coral = Color(0xFF5B8DEF)
    val CoralSoft = Color(0x225B8DEF)
    val TextPrimary = Color(0xFFECEEF2)
    val TextSecondary = Color(0xFF98A0AE)
    val GreenSecure = Color(0xFF22C55E)
    val PrivateAccent = Color(0xFF8B74EB)
    val PrivateSurface = Color(0xFF181620)
    val Gold = Color(0xFFEAB308)
    val SkyBlue = Color(0xFF5B8DEF)
}

data class JarvisTab(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val url: String = "https://www.google.com",
    val title: String = "Start Page",
    val isPrivate: Boolean = false,
    val isHomePage: Boolean = true,
    val isExtensionPage: Boolean = false,
    val extensionId: String? = null,
    val desktopMode: Boolean = false,
    val groupId: String? = null,
    val blockedCount: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val thumbnail: Bitmap? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TabSwitcherMode {
    NORMAL,
    PRIVATE,
    GROUPS
}

enum class LibraryTabType(val label: String) {
    FAVORITES("Favorites"),
    HISTORY("History"),
    DOWNLOADS("Downloads"),
    PLAYLIST("Playlist")
}

enum class BrowserOverlayMode {
    NONE,
    MENU,
    TAB_SWITCHER,
    LIBRARY,
    EXTENSIONS_QUICK,
    EXTENSIONS_MANAGE,
    EXTENSIONS_DEV,
    EXTENSION_POPUP,
    EXTENSION_DETAILS,
    WORKER_INSPECTOR,
    SETTINGS,
    READER_MODE,
    PLAYLIST_PLAYER,
    TRANSLATE_PICKER
}

fun captureWebViewThumbnail(view: android.webkit.WebView?): Bitmap? {
    if (view == null || view.width <= 0 || view.height <= 0) return null
    return runCatching {
        val targetW = 360
        val ratio = view.height.toFloat() / view.width.toFloat()
        val targetH = (targetW * ratio).toInt().coerceIn(240, 640)
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
        val canvas = android.graphics.Canvas(bmp)
        val scale = targetW.toFloat() / view.width.toFloat()
        canvas.scale(scale, scale)
        view.draw(canvas)
        bmp
    }.getOrNull()
}
