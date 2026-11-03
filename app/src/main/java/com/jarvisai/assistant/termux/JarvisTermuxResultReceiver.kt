package com.jarvisai.assistant.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

data class TermuxCommandResult(
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errorMessage: String? = null
)

class JarvisTermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val execId = intent.getIntExtra("jarvis_exec_id", -1)
        if (execId < 0) return

        val resultBundle: Bundle? = intent.getBundleExtra("result")
        val stdout = resultBundle?.getString("stdout") ?: intent.getStringExtra("stdout") ?: ""
        val stderr = resultBundle?.getString("stderr") ?: intent.getStringExtra("stderr") ?: ""
        val exitCode = resultBundle?.getInt("exitCode", 0) ?: intent.getIntExtra("exitCode", 0)
        val errCode = resultBundle?.getInt("errCode", 0) ?: intent.getIntExtra("errCode", 0)
        val errmsg = resultBundle?.getString("errmsg") ?: intent.getStringExtra("errmsg")

        Log.d(TAG, "Termux result received for id=$execId, exitCode=$exitCode, stdout=${stdout.take(50)}")

        val deferred = pendingRequests.remove(execId)
        val commandResult = TermuxCommandResult(
            success = exitCode == 0 && errCode == 0,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode,
            errorMessage = errmsg
        )
        deferred?.complete(commandResult)
    }

    companion object {
        private const val TAG = "JarvisTermuxReceiver"
        val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<TermuxCommandResult>>()
    }
}
