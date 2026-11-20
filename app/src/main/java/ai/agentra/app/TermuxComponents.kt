package ai.agentra.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.jarvisai.assistant.service.TermuxService as CoreTermuxService
import com.termux.app.TermuxOpenHelper
import com.termux.app.TermuxSystemReceiver as CoreTermuxSystemReceiver

/**
 * Component aliases under `ai.agentra.app.*` matching the byte-relocated `com.termux.app.*` targets
 * inside Termux scripts (e.g., `termux-open`, `termux-setup-storage`, `termux-wake-lock`, `am`).
 */
class TermuxService : CoreTermuxService()

class TermuxOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent != null) {
            TermuxOpenHelper.handleOpenIntent(this, intent)
        }
        finish()
    }
}

class TermuxOpenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null) {
            TermuxOpenHelper.handleOpenIntent(context, intent)
        }
    }
}

class TermuxSystemReceiver : BroadcastReceiver() {
    private val delegate = CoreTermuxSystemReceiver()
    override fun onReceive(context: Context, intent: Intent?) {
        delegate.onReceive(context, intent)
    }
}
