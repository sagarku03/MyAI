package com.jarvisai.assistant.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.MainActivity

/**
 * JarvisTileService — Android Quick Settings Tile.
 * Placed inside Android status bar swipe-down tile menu.
 * Enables 1-tap Jarvis summon, floating Arc Reactor overlay toggling, or Sora IDE launch.
 */
@RequiresApi(Build.VERSION_CODES.N)
class JarvisTileService : TileService() {

    companion object {
        private const val TAG = "JarvisTileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        Log.i(TAG, "Quick Settings Jarvis Tile Clicked.")

        val isOverlayActive = JarvisOverlayHudService.hudState.value.isOverlayActive
        if (!isOverlayActive) {
            if (JarvisOverlayHudService.canDrawOverlays(this)) {
                val intent = Intent(this, JarvisOverlayHudService::class.java).apply {
                    action = JarvisOverlayHudService.ACTION_SHOW_HUD
                }
                startService(intent)
            } else {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivityAndCollapse(mainIntent)
            }
        } else {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(mainIntent)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isOverlayActive = JarvisOverlayHudService.hudState.value.isOverlayActive
        val isAgentBusy = JarvisOverlayHudService.hudState.value.isAgentBusy

        if (isAgentBusy) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Jarvis (Busy)"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Coding Agent Active"
            }
        } else if (isOverlayActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Jarvis (Online)"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "HUD Active"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Jarvis AI"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Tap to Summon"
            }
        }

        tile.updateTile()
    }
}
