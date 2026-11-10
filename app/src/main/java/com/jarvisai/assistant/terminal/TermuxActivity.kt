package com.jarvisai.assistant.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvisai.assistant.service.TermuxService
import com.jarvisai.assistant.ui.components.LocalTerminalDialog

class TermuxActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocalTerminalBridge.instance.init(applicationContext)
        TermuxService.start(this)

        setContent {
            LocalTerminalDialog(onDismiss = { finish() })
        }
    }
}
