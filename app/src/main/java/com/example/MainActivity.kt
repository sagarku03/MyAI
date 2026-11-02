package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.jarvisai.assistant.service.LiveVoiceSessionBridge
import com.jarvisai.assistant.ui.JarvisApp
import com.jarvisai.assistant.viewmodel.JarvisViewModel

class MainActivity : ComponentActivity() {

    private val jarvisViewModel: JarvisViewModel by viewModels {
        JarvisViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisApp(viewModel = jarvisViewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        LiveVoiceSessionBridge.setAppInBackground(false)
    }

    override fun onStop() {
        super.onStop()
        LiveVoiceSessionBridge.setAppInBackground(true)
    }
}
