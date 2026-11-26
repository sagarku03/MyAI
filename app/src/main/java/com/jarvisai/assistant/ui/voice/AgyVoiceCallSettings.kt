package com.jarvisai.assistant.ui.voice

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvisai.assistant.voice.agy.AgyVoicePreferences
import com.jarvisai.assistant.voice.agy.VoiceCallLauncher

private val AgyCallAccent = Color(0xFF38BDF8)

/**
 * Antigravity Voice Call settings card:
 * - Quick Settings tile + Home screen shortcut ("Call from anywhere")
 * - Floating Call Window over other apps (`SYSTEM_ALERT_WINDOW`)
 */
@Composable
fun AgyVoiceCallSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { AgyVoicePreferences.init(context) }
    val bubbleOn by AgyVoicePreferences.bubble.collectAsState()
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) canDraw = Settings.canDrawOverlays(context)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val openOverlaySettings = {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        Unit
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.06f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HeadsetMic, null, tint = AgyCallAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Call from anywhere", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Start an Antigravity voice call in your active chat from the Quick Settings tile or a home screen shortcut.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (VoiceCallLauncher.canRequestTile) {
                        OutlinedButton(onClick = {
                            if (!VoiceCallLauncher.requestAddTile(context)) {
                                Toast.makeText(context, "Add it by editing Quick Settings tiles", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Add tile", color = AgyCallAccent) }
                    }
                    OutlinedButton(onClick = {
                        if (!VoiceCallLauncher.pinShortcut(context)) {
                            Toast.makeText(context, "Long-press Agentra icon to drag Voice call shortcut", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Add to home screen", color = AgyCallAccent) }
                }
                if (!VoiceCallLauncher.canRequestTile) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pull down Quick Settings and edit tiles to add 'Voice call'.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.06f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.BubbleChart, null, tint = AgyCallAccent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Floating call window",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = bubbleOn,
                        onCheckedChange = { on ->
                            AgyVoicePreferences.setBubble(on)
                            if (on && !canDraw) openOverlaySettings()
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Shows a draggable call orb and expandable mini panel over other apps while an Antigravity voice call is active.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
                if (bubbleOn && !canDraw) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = openOverlaySettings) {
                        Text("Allow Display over other apps", color = Color(0xFFFBBF24))
                    }
                }
            }
        }
    }
}
