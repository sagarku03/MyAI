package com.jarvisai.assistant.service

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.jarvisai.assistant.data.preferences.UserPreferencesRepository
import com.jarvisai.assistant.model.PersonalityMode
import com.jarvisai.assistant.model.WorkflowAction
import com.jarvisai.assistant.model.WorkflowActionType
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

data class WorkflowExecutionResult(
    val success: Boolean,
    val executedSteps: Int,
    val logs: List<String>
)

/**
 * Sequential native workflow execution engine for JARVIS AI custom routines.
 * Executes offline/native tasks without any third-party paid automators.
 */
class WorkflowEngine(
    private val context: Context,
    private val phoneActionManager: PhoneActionManager,
    private val phoneControlsManager: PhoneControlsManager,
    private val ttsManager: JarvisTtsManager? = null,
    private val preferencesRepository: UserPreferencesRepository? = null
) {
    companion object {
        private const val TAG = "WorkflowEngine"
    }

    private val cancelRequested = AtomicBoolean(false)

    fun cancelActiveWorkflow() {
        cancelRequested.set(true)
    }

    suspend fun executeWorkflow(
        actions: List<WorkflowAction>,
        stepDelayMs: Long = 600L
    ): WorkflowExecutionResult = coroutineScope {
        val logs = mutableListOf<String>()
        var executedCount = 0
        cancelRequested.set(false)

        for ((index, action) in actions.withIndex()) {
            coroutineContext.ensureActive()
            if (cancelRequested.get()) {
                logs.add("Workflow halted by Emergency Stop.")
                break
            }
            val stepLabel = "Step ${index + 1} (${action.type.name})"
            try {
                when (action.type) {
                    WorkflowActionType.OPEN_APP -> {
                        val appName = action.param1
                        val outcome = phoneActionManager.openInstalledAppByName(appName)
                        logs.add("$stepLabel: Opened app '$appName' -> ${outcome.spokenHinglishResponse}")
                        executedCount++
                    }
                    WorkflowActionType.START_TIMER -> {
                        val seconds = action.param1.toIntOrNull() ?: 300
                        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                            putExtra(AlarmClock.EXTRA_MESSAGE, action.param2.ifBlank { "JARVIS Workflow Timer" })
                            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                            logs.add("$stepLabel: Set timer for $seconds seconds.")
                            executedCount++
                        } else {
                            logs.add("$stepLabel: Timer activity not available on device.")
                        }
                    }
                    WorkflowActionType.SHOW_REMINDER -> {
                        val message = action.param1
                        logs.add("$stepLabel: Reminder: $message")
                        ttsManager?.speak("Reminder: $message")
                        executedCount++
                    }
                    WorkflowActionType.SET_PERSONALITY -> {
                        val rawMode = action.param1.trim()
                        val resolvedMode = PersonalityMode.entries.find {
                            it.name.equals(rawMode, ignoreCase = true) ||
                                it.displayName.equals(rawMode, ignoreCase = true) ||
                                it.displayName.removeSuffix(" Mode").equals(rawMode, ignoreCase = true)
                        } ?: PersonalityMode.PROFESSIONAL
                        preferencesRepository?.updatePersonalityMode(resolvedMode)
                        logs.add("$stepLabel: Personality mode persisted as ${resolvedMode.name} (${resolvedMode.displayName}).")
                        executedCount++
                    }
                    WorkflowActionType.SPEAK_TEXT -> {
                        val textToSpeak = action.param1
                        ttsManager?.speak(textToSpeak)
                        logs.add("$stepLabel: Spoke: '$textToSpeak'")
                        executedCount++
                    }
                    WorkflowActionType.SEARCH_WEB -> {
                        val query = action.param1
                        val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(searchIntent)
                        logs.add("$stepLabel: Searched web for '$query'")
                        executedCount++
                    }
                    WorkflowActionType.TOGGLE_FLASHLIGHT -> {
                        val turnOn = action.param1.equals("on", ignoreCase = true) || action.param1.equals("true", ignoreCase = true)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                                val cameraId = cameraManager?.cameraIdList?.firstOrNull()
                                if (cameraId != null) {
                                    cameraManager.setTorchMode(cameraId, turnOn)
                                    logs.add("$stepLabel: Flashlight turned ${if (turnOn) "ON" else "OFF"}.")
                                    executedCount++
                                } else {
                                    logs.add("$stepLabel: No camera flash found.")
                                }
                            }
                        } catch (e: Exception) {
                            logs.add("$stepLabel: Failed to toggle flashlight: ${e.message}")
                        }
                    }
                }
                if (stepDelayMs > 0L) {
                    delay(stepDelayMs) // Brief step delay for smooth sequential execution
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action: ${e.message}")
                logs.add("$stepLabel Failed: ${e.message}")
            }
        }

        WorkflowExecutionResult(
            success = executedCount > 0 && !cancelRequested.get(),
            executedSteps = executedCount,
            logs = logs
        )
    }
}
