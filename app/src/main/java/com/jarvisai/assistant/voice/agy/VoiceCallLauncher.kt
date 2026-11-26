package com.jarvisai.assistant.voice.agy

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-tap Antigravity voice calls from outside the app (Quick Settings tile & launcher shortcut).
 */
object VoiceCallLauncher {
    private const val TAG = "VoiceCallLauncher"
    const val EXTRA_START_CALL = "ai.agentra.voicecall.EXTRA_START_CALL"
    const val ACTION_CALL = "ai.agentra.voicecall.CALL"
    private const val SHORTCUT_ID = "agentra_voice_call"
    private const val PENDING_MAX_MS = 3 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _pending = MutableStateFlow(0L)
    val pending: StateFlow<Long> = _pending.asStateFlow()

    private val _appVisible = MutableStateFlow(false)
    val appVisible: StateFlow<Boolean> = _appVisible.asStateFlow()

    /** Registered by MainActivity / JarvisViewModel while initialized so tiles/shortcuts can start calls in background. */
    @Volatile var backgroundPrepareVoiceCall: (suspend (Context) -> Pair<String, Int>)? = null
    @Volatile var isHubReady: ((Context) -> Boolean)? = null
    @Volatile var currentChatWorking: StateFlow<Boolean>? = null
    @Volatile var currentChatId: StateFlow<String?>? = null

    fun setAppVisible(visible: Boolean) {
        _appVisible.value = visible
    }

    fun requestStart() {
        _pending.value = SystemClock.elapsedRealtime()
    }

    fun consumePending(): Boolean {
        val at = _pending.value
        _pending.value = 0L
        return at > 0L && SystemClock.elapsedRealtime() - at < PENDING_MAX_MS
    }

    fun launch(activity: Activity) {
        val app = activity.applicationContext
        AgyVoicePreferences.init(app)
        if (AgyVoiceCall.state.value.active) {
            AgyVoiceCall.setMinimized(false)
            openApp(activity, startCall = false)
            return
        }
        val prepare = backgroundPrepareVoiceCall
        val micAllowed = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val hubUp = isHubReady?.invoke(app) == true
        if (prepare == null || !hubUp || !micAllowed || !AgyVoicePreferences.picked.value) {
            openApp(activity, startCall = true)
            return
        }
        AgyVoiceBubbleBridge.openPanelForNextCall()
        AgyVoiceCall.start(app, "Voice call") { prepare(app) }
        reportFailure(app)
    }

    private fun openApp(activity: Activity, startCall: Boolean) {
        val intent = Intent(activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (startCall) {
            intent.putExtra(EXTRA_START_CALL, true)
        }
        runCatching { activity.startActivity(intent) }.onFailure { Log.w(TAG, "open app: ${it.message}") }
    }

    private fun reportFailure(app: Context) {
        scope.launch {
            val st = withTimeoutOrNull(90_000) {
                AgyVoiceCall.state.first {
                    it.phase == AgyVoiceCall.Phase.LIVE ||
                        it.phase == AgyVoiceCall.Phase.FAILED ||
                        !it.active
                }
            } ?: return@launch
            if (st.phase == AgyVoiceCall.Phase.FAILED && !_appVisible.value) {
                Toast.makeText(
                    app,
                    "Voice call failed: ${st.error ?: "Open Agentra for details"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun shortcut(context: Context): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel("Voice call")
            .setLongLabel("Antigravity Voice Call")
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_voice_call))
            .setIntent(Intent(context, VoiceCallLaunchActivity::class.java).setAction(ACTION_CALL))
            .build()

    fun publishShortcut(context: Context) {
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut(context)) }
            .onFailure { Log.w(TAG, "shortcut: ${it.message}") }
    }

    fun pinShortcut(context: Context): Boolean = runCatching {
        ShortcutManagerCompat.isRequestPinShortcutSupported(context) &&
            ShortcutManagerCompat.requestPinShortcut(context, shortcut(context), null)
    }.getOrDefault(false)

    val canRequestTile: Boolean get() = Build.VERSION.SDK_INT >= 33

    fun requestAddTile(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return runCatching {
            val sbm = context.getSystemService(StatusBarManager::class.java) ?: return false
            sbm.requestAddTileService(
                ComponentName(context, VoiceCallTileService::class.java),
                "Voice call",
                Icon.createWithResource(context, R.drawable.ic_voice_call),
                context.mainExecutor
            ) { result -> Log.i(TAG, "add tile: $result") }
            true
        }.getOrDefault(false)
    }
}

class VoiceCallLaunchActivity : Activity() {
    private var handled = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true
        runCatching { VoiceCallLauncher.launch(this) }
            .onFailure { Log.w("VoiceCallLauncher", "launch: ${it.message}", it) }
        finish()
        overridePendingTransition(0, 0)
    }
}

class VoiceCallTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        showState()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (AgyVoiceCall.state.value.inCall) {
            AgyVoiceCall.end()
            showState()
            return
        }
        val open = {
            val intent = Intent(this, VoiceCallLaunchActivity::class.java)
                .setAction(VoiceCallLauncher.ACTION_CALL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        78,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { open() } else open()
    }

    private fun showState() {
        val inCall = AgyVoiceCall.state.value.inCall
        qsTile?.apply {
            state = if (inCall) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Voice call"
            if (Build.VERSION.SDK_INT >= 29) {
                subtitle = if (inCall) "In call · tap to end" else "Tap to call"
            }
            updateTile()
        }
    }

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, VoiceCallTileService::class.java)) }
        }
    }
}
