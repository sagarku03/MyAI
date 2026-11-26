package com.jarvisai.assistant.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class JarvisTermuxBridge(private val context: Context) {

    private val idCounter = AtomicInteger(1000)

    fun isTermuxInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasRunCommandPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            TERMUX_RUN_COMMAND_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if the Android 16 Zero-Permission Termux Local Loopback Bridge (`127.0.0.1:2238`)
     * is running inside Termux.
     */
    fun isLocalBridgeDaemonActive(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", LOCAL_BRIDGE_PORT), 220)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a command directly inside external Termux via the authenticated localhost bridge (`127.0.0.1:2238`),
     * bypassing Android 16's hidden `com.termux.permission.RUN_COMMAND` restriction completely.
     */
    private suspend fun executeViaLocalBridgeDaemon(
        command: String,
        timeoutSeconds: Long
    ): TermuxCommandResult? = withContext(Dispatchers.IO) {
        try {
            val bridgeToken = getOrCreateBridgeToken(context)
            val url = URL("http://127.0.0.1:$LOCAL_BRIDGE_PORT/exec")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1500
                readTimeout = (timeoutSeconds * 1000L).toInt().coerceAtLeast(3000)
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Agentra-Token", bridgeToken)
            }
            val payload = JSONObject().apply {
                put("command", command)
                put("timeout", timeoutSeconds)
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            }.orEmpty()

            if (body.isBlank()) return@withContext null
            val json = JSONObject(body)
            val exitCode = json.optInt("exitCode", if (status == 200) 0 else 1)
            val stdout = json.optString("stdout", "")
            val stderr = json.optString("stderr", "")
            TermuxCommandResult(
                success = exitCode == 0,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                errorMessage = if (exitCode == 0) null else "EXIT_$exitCode"
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun executeCommand(
        command: String,
        background: Boolean = true,
        timeoutSeconds: Long = 15
    ): TermuxCommandResult {
        // Strategy 1: Android 16 Zero-Permission Local Loopback Bridge inside Termux (127.0.0.1:2238)
        val bridgeResult = executeViaLocalBridgeDaemon(command, timeoutSeconds)
        if (bridgeResult != null) {
            return bridgeResult
        }

        val localLinuxReady = com.jarvisai.assistant.terminal.LocalEnvironmentManager.isEnvironmentInstalled(context)
        if (!isTermuxInstalled() || !hasRunCommandPermission()) {
            // Strategy 2: Seamlessly run inside Built-in Jarvis Linux Core if available (0 Android permissions required)
            if (!isTermuxInstalled() || localLinuxReady) {
                return try {
                    com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.init(context.applicationContext)
                    val localResult = com.jarvisai.assistant.terminal.LocalTerminalBridge.instance.executeCommand(
                        command = command,
                        timeoutMs = timeoutSeconds * 1000L
                    )
                    TermuxCommandResult(
                        success = localResult.exitCode == 0,
                        stdout = localResult.output,
                        stderr = if (localResult.exitCode != 0) localResult.output else "",
                        exitCode = localResult.exitCode,
                        errorMessage = if (localResult.exitCode != 0) "EXIT_${localResult.exitCode}" else null
                    )
                } catch (e: Exception) {
                    val reason = if (!isTermuxInstalled()) {
                        "Termux app is not installed and built-in shell failed: ${e.message}"
                    } else {
                        "Termux RUN_COMMAND permission not granted and built-in shell failed: ${e.message}"
                    }
                    TermuxCommandResult(
                        success = false,
                        stdout = "",
                        stderr = reason,
                        exitCode = -2,
                        errorMessage = if (!isTermuxInstalled()) "TERMUX_NOT_INSTALLED" else "PERMISSION_DENIED"
                    )
                }
            }
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "com.termux.permission.RUN_COMMAND permission is not granted and built-in Agentra Linux Core is not installed yet.",
                exitCode = -2,
                errorMessage = "PERMISSION_DENIED"
            )
        }

        val execId = idCounter.incrementAndGet()
        val deferred = CompletableDeferred<TermuxCommandResult>()
        JarvisTermuxResultReceiver.pendingRequests[execId] = deferred

        try {
            val resultIntent = Intent(context, JarvisTermuxResultReceiver::class.java).apply {
                putExtra("jarvis_exec_id", execId)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, execId, resultIntent, flags)

            val intent = Intent(TERMUX_RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PACKAGE, TERMUX_SERVICE_CLASS)
                putExtra(EXTRA_RUN_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-c", command))
                putExtra(EXTRA_RUN_COMMAND_WORKDIR, "/data/data/com.termux/files/home")
                putExtra(EXTRA_RUN_COMMAND_BACKGROUND, background)
                putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            val result = withTimeoutOrNull(timeoutSeconds * 1000) {
                deferred.await()
            }

            return result ?: TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "Command execution timed out after ${timeoutSeconds}s in Termux.",
                exitCode = -3,
                errorMessage = "TIMEOUT"
            )
        } catch (se: SecurityException) {
            JarvisTermuxResultReceiver.pendingRequests.remove(execId)
            Log.e(TAG, "SecurityException executing Termux command", se)
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = "Termux blocked external execution. Please run in Termux: $TERMUX_ALLOW_EXTERNAL_CMD",
                exitCode = -2,
                errorMessage = "TERMUX_EXTERNAL_APPS_DISABLED"
            )
        } catch (e: Exception) {
            JarvisTermuxResultReceiver.pendingRequests.remove(execId)
            Log.e(TAG, "Error executing Termux command", e)
            return TermuxCommandResult(
                success = false,
                stdout = "",
                stderr = e.localizedMessage ?: "Unknown execution error",
                exitCode = -4,
                errorMessage = e.message
            )
        }
    }

    private fun getPackageInfoCompat(packageName: String): android.content.pm.PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getInstallerPackageCompat(packageName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(packageName).installingPackageName.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName).orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun getDiagnostics(): TermuxBridgeDiagnostics {
        val termuxInfo = getPackageInfoCompat(TERMUX_PACKAGE)
        val installed = termuxInfo != null
        val permGranted = hasRunCommandPermission()
        val allFilesGranted = hasAllFilesAccess()
        val bridgeDaemonUp = isLocalBridgeDaemonActive()
        val pkg = context.packageName
        val isPermissionDeclaredByTermux = runCatching {
            context.packageManager.getPermissionInfo(TERMUX_RUN_COMMAND_PERMISSION, 0)
            true
        }.getOrDefault(false)
        val termuxInstaller = if (installed) getInstallerPackageCompat(TERMUX_PACKAGE) else ""
        val isPlayStoreTermux = termuxInstaller == "com.android.vending" || termuxInstaller == "com.google.android.feedback"
        val jarvisInfo = getPackageInfoCompat(pkg)
        val wasJarvisInstalledBeforeTermux = if (jarvisInfo != null && termuxInfo != null) {
            jarvisInfo.firstInstallTime < termuxInfo.firstInstallTime
        } else {
            false
        }
        val blockingReason = when {
            bridgeDaemonUp -> "✅ Android 16 Termux Local Bridge (:2238 • Isolated) ACTIVE! Agentra bina kisi Android permission ke Termux me commands aur files chala sakta hai."
            permGranted -> "✅ External Termux RUN_COMMAND permission active hai."
            !installed -> "Aapke phone me abhi external Termux (com.termux) app installed nahi hai! Isliye Android 16 Settings > App permissions me 'Run commands in Termux' ka option nahi dikh raha. Niche button se Official F-Droid Termux install karein."
            !isPermissionDeclaredByTermux || isPlayStoreTermux -> "Aapka installed Termux Play Store / old build hai jo RUN_COMMAND permission expose nahi karta. Official F-Droid Termux install karein ya niche '1-Tap Android 16 Termux Bridge' use karein."
            wasJarvisInstalledBeforeTermux -> "Android 16 Package Order Issue: Agentra pehle install hua tha aur Termux baad me! Isi wajah se Android 16 Settings me permission option hide ho gaya hai. Solution: Niche '⚡ 1-Tap Android 16 Termux Bridge (:2238)' button dabayein (0 permission needed) YA Agentra APK ko bina uninstall kiye upar se update/re-install karein."
            else -> "Android 16 Restricted Settings: Android 16 Settings > App permissions me custom Termux permission chhupa deta hai. Sabse aasan tarika: Niche '⚡ 1-Tap Android 16 Termux Bridge (:2238)' button tap karein aur Termux me paste kar dein (bina kisi Settings permission ke 100% kaam karega!)."
        }
        return TermuxBridgeDiagnostics(
            isTermuxInstalled = installed,
            hasRunCommandPermission = permGranted,
            hasAllFilesAccess = allFilesGranted,
            isLocalBridgeActive = bridgeDaemonUp,
            isPermissionDeclaredByTermux = isPermissionDeclaredByTermux,
            isPlayStoreTermux = isPlayStoreTermux,
            wasJarvisInstalledBeforeTermux = wasJarvisInstalledBeforeTermux,
            blockingReasonSummary = blockingReason,
            adbGrantCommand = "adb shell appops set $pkg ACCESS_RESTRICTED_SETTINGS allow && adb shell pm grant $pkg $TERMUX_RUN_COMMAND_PERMISSION",
            termuxPropertiesCommand = buildAndroid16BridgeCommand(context)
        )
    }

    data class TermuxBridgeDiagnostics(
        val isTermuxInstalled: Boolean,
        val hasRunCommandPermission: Boolean,
        val hasAllFilesAccess: Boolean = false,
        val isLocalBridgeActive: Boolean = false,
        val isPermissionDeclaredByTermux: Boolean = false,
        val isPlayStoreTermux: Boolean = false,
        val wasJarvisInstalledBeforeTermux: Boolean = false,
        val blockingReasonSummary: String = "",
        val adbGrantCommand: String,
        val termuxPropertiesCommand: String
    )

    companion object {
        private const val TAG = "JarvisTermuxBridge"
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val LOCAL_BRIDGE_PORT = com.jarvisai.assistant.data.remote.JarvisHubConfig.ISOLATED_TERMUX_EXEC_PORT // 2238
        private const val BRIDGE_PREFS_NAME = "agentra_termux_bridge_prefs"
        private const val KEY_BRIDGE_TOKEN = "bridge_secret_token"

        const val TERMUX_ALLOW_EXTERNAL_CMD =
            "mkdir -p ~/.termux && grep -qxF 'allow-external-apps = true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings"

        fun getOrCreateBridgeToken(context: Context): String {
            val prefs = context.applicationContext.getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            val stored = com.jarvisai.assistant.security.SecurePrefsCipher.decrypt(prefs.getString(KEY_BRIDGE_TOKEN, ""))
            if (stored.length == 32 && stored.all { it in '0'..'9' || it in 'a'..'f' }) {
                return stored
            }
            val bytes = ByteArray(16)
            java.security.SecureRandom().nextBytes(bytes)
            val generated = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_BRIDGE_TOKEN, com.jarvisai.assistant.security.SecurePrefsCipher.encrypt(generated)).apply()
            return generated
        }

        /**
         * One-liner setup command for Android 16 that enables `allow-external-apps = true` AND starts
         * a token-authenticated isolated local HTTP bridge inside Termux on `127.0.0.1:2238` inside `~/.agentra/`
         * so Agentra never collides with AntiGem and rejects unauthorized localhost callers.
         */
        fun buildAndroid16BridgeCommand(context: Context): String {
            val token = getOrCreateBridgeToken(context)
            return "mkdir -p ~/.termux ~/.agentra ~/projects && (grep -qxF 'allow-external-apps = true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps = true' >> ~/.termux/termux.properties) && termux-reload-settings 2>/dev/null; " +
                "umask 077 && cat << 'EOF' > ~/.agentra/agentra_bridge_2238.py\n" +
                "import http.server, json, subprocess, os, hmac\n" +
                "BRIDGE_TOKEN = \"$token\"\n" +
                "class H(http.server.BaseHTTPRequestHandler):\n" +
                "    def log_message(self, *a): pass\n" +
                "    def do_POST(self):\n" +
                "        tok = self.headers.get('X-Agentra-Token', '')\n" +
                "        if not hmac.compare_digest(tok, BRIDGE_TOKEN):\n" +
                "            out = b'{\"exitCode\":401,\"stdout\":\"\",\"stderr\":\"Unauthorized\"}'\n" +
                "            self.send_response(401)\n" +
                "            self.send_header('Content-Type', 'application/json')\n" +
                "            self.send_header('Content-Length', str(len(out)))\n" +
                "            self.end_headers()\n" +
                "            self.wfile.write(out)\n" +
                "            return\n" +
                "        n = int(self.headers.get('Content-Length', 0))\n" +
                "        req = json.loads(self.rfile.read(n).decode('utf-8'))\n" +
                "        cmd = req.get('command', '')\n" +
                "        cwd = req.get('cwd', os.path.expanduser('~/projects'))\n" +
                "        if not os.path.isdir(cwd): cwd = os.path.expanduser('~')\n" +
                "        t = int(req.get('timeout', 30))\n" +
                "        try:\n" +
                "            p = subprocess.run(['/data/data/com.termux/files/usr/bin/bash', '-lc', cmd], cwd=cwd, capture_output=True, text=True, timeout=t)\n" +
                "            res = {'exitCode': p.returncode, 'stdout': p.stdout, 'stderr': p.stderr}\n" +
                "        except Exception as e:\n" +
                "            res = {'exitCode': -1, 'stdout': '', 'stderr': str(e)}\n" +
                "        out = json.dumps(res).encode('utf-8')\n" +
                "        self.send_response(200)\n" +
                "        self.send_header('Content-Type', 'application/json')\n" +
                "        self.send_header('Content-Length', str(len(out)))\n" +
                "        self.end_headers()\n" +
                "        self.wfile.write(out)\n" +
                "http.server.ThreadingHTTPServer(('127.0.0.1', 2238), H).serve_forever()\n" +
                "EOF\n" +
                "chmod 600 ~/.agentra/agentra_bridge_2238.py && pkg install -y python >/dev/null 2>&1 || true; pkill -f jarvis_bridge_2238.py 2>/dev/null || true; pkill -f agentra_bridge_2238.py 2>/dev/null || true; nohup python3 ~/.agentra/agentra_bridge_2238.py >/dev/null 2>&1 & " +
                "(grep -q 'agentra_bridge_2238.py' ~/.bashrc 2>/dev/null || echo 'pgrep -f agentra_bridge_2238.py >/dev/null || nohup python3 ~/.agentra/agentra_bridge_2238.py >/dev/null 2>&1 &' >> ~/.bashrc) && echo '✅ Agentra Authenticated Termux Bridge Active on port 2238 (~/.agentra)!'"
        }

        private const val TERMUX_SERVICE_CLASS = "com.termux.app.RunCommandService"
        private const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        private const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    }
}
