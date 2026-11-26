package com.jarvisai.assistant.crash

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.example.BuildConfig
import com.jarvisai.assistant.model.isRobolectricTestEnvironment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Global uncaught exception handler that persists a diagnostic report and launches
 * [CrashActivity] in a dedicated isolated `:crash` process so recovery works even after OOM.
 */
object CrashHandler {
    private const val CRASH_PROCESS_SUFFIX = ":crash"
    private const val MAX_REPORT_CHARS = 200_000

    @Volatile
    private var installed = false

    fun install(app: Application) {
        if (isRobolectricTestEnvironment() || isCrashProcess(app) || installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val shown = runCatching {
                val report = buildReport(app, thread, error)
                reportFile(app).apply { parentFile?.mkdirs() }.writeText(report)
                app.startActivity(
                    Intent(app, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }.isSuccess
            if (!shown) {
                previous?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    fun isCrashProcess(context: Context): Boolean = processName(context).endsWith(CRASH_PROCESS_SUFFIX)

    fun reportFile(context: Context): File = File(context.filesDir, "crash/last_crash.txt")

    private fun processName(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }
                .getOrDefault(context.packageName)
        }

    private fun buildReport(context: Context, thread: Thread, error: Throwable): String {
        val trace = runCatching {
            StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        }.getOrElse { error.toString() }
        val rt = Runtime.getRuntime()
        val mb = 1024L * 1024L
        val report = buildString {
            appendLine("Agentra Crash Report")
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.APPLICATION_ID} ${BuildConfig.BUILD_TYPE}")
            appendLine("Package: ${context.packageName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine("Memory: used ${(rt.totalMemory() - rt.freeMemory()) / mb} MB of max ${rt.maxMemory() / mb} MB")
            appendLine()
            append(trace)
        }
        return if (report.length > MAX_REPORT_CHARS) report.take(MAX_REPORT_CHARS) + "\n… (truncated)" else report
    }
}
