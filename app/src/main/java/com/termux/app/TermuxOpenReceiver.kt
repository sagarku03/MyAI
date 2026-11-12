package com.termux.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.jarvisai.assistant.browser.JarvisBrowserActivity
import java.io.File

/**
 * Activity and BroadcastReceiver handling `termux-open`, `termux-open-url`, and `xdg-open`
 * intents dispatched from inside the Linux PTY shell.
 */
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

object TermuxOpenHelper {
    private const val TAG = "TermuxOpen"

    fun handleOpenIntent(context: Context, intent: Intent) {
        try {
            val dataUri = intent.data
            val extraFile = intent.getStringExtra("file")
                ?: intent.getStringExtra("path")
                ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            val contentType = intent.getStringExtra("content-type") ?: intent.type
            val sendChooser = intent.getBooleanExtra("chooser", false)

            val rawTarget = dataUri?.toString()?.takeIf { it.isNotBlank() }
                ?: extraFile?.trim()
                ?: return

            // 1. Check if HTTP/HTTPS URL -> Route to In-App AI Browser or System Browser
            if (rawTarget.startsWith("http://", ignoreCase = true) ||
                rawTarget.startsWith("https://", ignoreCase = true)
            ) {
                try {
                    val browserIntent = Intent(context, JarvisBrowserActivity::class.java).apply {
                        putExtra("url", rawTarget)
                        putExtra("initial_url", rawTarget)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    return
                } catch (_: Exception) {
                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rawTarget)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(viewIntent)
                    return
                }
            }

            // 2. Local File Path or file:// URI
            val filePath = if (rawTarget.startsWith("file://")) {
                Uri.parse(rawTarget).path ?: rawTarget.removePrefix("file://")
            } else {
                rawTarget
            }
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "Target file does not exist: $filePath")
                return
            }

            val ext = file.extension.lowercase()
            val resolvedMime = contentType?.takeIf { it.isNotBlank() }
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "*/*"

            val contentUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            val action = if (intent.action == Intent.ACTION_SEND) Intent.ACTION_SEND else Intent.ACTION_VIEW
            val openIntent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (action == Intent.ACTION_SEND) {
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = resolvedMime
                } else {
                    setDataAndType(contentUri, resolvedMime)
                }
            }

            val finalIntent = if (sendChooser) {
                Intent.createChooser(openIntent, file.name).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                openIntent
            }
            context.startActivity(finalIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle TermuxOpen intent: ${e.message}", e)
        }
    }
}
