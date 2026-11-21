package com.jarvisai.assistant.data.ssh

import android.content.Context
import android.util.Base64
import android.util.Log
import com.jarvisai.assistant.data.pc.PcConnect
import com.jarvisai.assistant.data.preferences.AuthPreferences
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * Trust-On-First-Use (TOFU) host key repository backed by SharedPreferences,
 * synchronized with PcConnect paired PC host keys when available.
 */
object PinnedHostKeys {
    private const val PREFS_NAME = "agentra_ssh_known_hosts"
    private const val TAG = "PinnedHostKeys"

    fun repository(context: Context, pairedHostKeyType: String? = null, pairedHostKeyBase64: String? = null): HostKeyRepository {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                val b64 = Base64.encodeToString(key, Base64.NO_WRAP)
                if (!pairedHostKeyBase64.isNullOrBlank()) {
                    val cleanPaired = pairedHostKeyBase64.trim()
                    if (cleanPaired == b64) return HostKeyRepository.OK
                }
                val id = hostKeyId(host)
                val stored = prefs.getString(id, null)
                return when {
                    stored == null -> {
                        // Trust On First Use
                        prefs.edit().putString(id, b64).apply()
                        Log.i(TAG, "TOFU pinned SSH host key for $host (${fingerprint(key)})")
                        HostKeyRepository.OK
                    }
                    stored == b64 -> HostKeyRepository.OK
                    else -> {
                        Log.w(TAG, "SSH host key mismatch for $host!")
                        HostKeyRepository.CHANGED
                    }
                }
            }

            override fun add(hostkey: HostKey, ui: UserInfo?) {
                val b64 = hostkey.key ?: return
                prefs.edit().putString(hostKeyId(hostkey.host), b64).apply()
            }

            override fun remove(host: String, type: String?) {
                prefs.edit().remove(hostKeyId(host)).apply()
            }

            override fun remove(host: String, type: String?, key: ByteArray?) {
                prefs.edit().remove(hostKeyId(host)).apply()
            }

            override fun getKnownHostsRepositoryID(): String = "agentra_tofu_hosts"

            override fun getHostKey(): Array<HostKey> = emptyArray()

            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
    }

    fun clearHost(context: Context, host: String) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(hostKeyId(host)).apply()
    }

    fun clearAll(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun hostKeyId(host: String): String = "hk_${host.trim().lowercase()}"

    private fun fingerprint(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

/**
 * Hardened JSch session factory supporting password auth, private-key auth,
 * PcConnect paired host key pinning, and TOFU host key verification.
 */
object SshSessionFactory {
    private const val TAG = "SshSessionFactory"

    fun init(context: Context) {
        // Ensure PcConnect is warmed up so paired PCs can provide pinned host keys
        runCatching { PcConnect.init(context.applicationContext) }
    }

    fun openSession(
        context: Context,
        host: String,
        port: Int = 8022,
        user: String = "root",
        password: String = "",
        privateKeyPem: String = "",
        keyPassphrase: String = "",
        connectTimeoutMs: Int = 8_000
    ): Session {
        val appCtx = context.applicationContext
        val jsch = JSch()

        val prefs = AuthPreferences.getInstance(appCtx)
        val effectiveKey = privateKeyPem.ifBlank { prefs.getSshPrivateKeySync() }
        val effectivePassphrase = keyPassphrase.ifBlank { prefs.getSshKeyPassphraseSync() }

        if (effectiveKey.isNotBlank()) {
            runCatching {
                val passBytes = if (effectivePassphrase.isNotEmpty()) effectivePassphrase.toByteArray(Charsets.UTF_8) else null
                jsch.addIdentity("agentra_ssh_id", effectiveKey.toByteArray(Charsets.UTF_8), null, passBytes)
            }.onFailure { err ->
                Log.w(TAG, "Failed loading SSH private key: ${err.message}")
            }
        }

        val pairedPc = PcConnect.getActivePairedPc()?.takeIf { pc ->
            pc.orderedAddresses().any { it.equals(host, ignoreCase = true) }
        }
        jsch.hostKeyRepository = PinnedHostKeys.repository(
            context = appCtx,
            pairedHostKeyType = pairedPc?.hostKeyType,
            pairedHostKeyBase64 = pairedPc?.hostKey
        )

        val session = jsch.getSession(user, host, port)
        if (password.isNotEmpty()) {
            session.setPassword(password)
        }

        val config = Properties().apply {
            put("StrictHostKeyChecking", "yes")
            if (pairedPc != null && pairedPc.hostKeyType.isNotBlank()) {
                put("server_host_key", pairedPc.hostKeyType)
            }
            if (password.isBlank() && effectiveKey.isBlank()) {
                put("PreferredAuthentications", "publickey,keyboard-interactive,password,none")
            } else if (effectiveKey.isNotBlank()) {
                put("PreferredAuthentications", "publickey,password,keyboard-interactive")
            } else {
                put("PreferredAuthentications", "password,keyboard-interactive,publickey")
            }
            put("ServerAliveInterval", "15")
            put("ServerAliveCountMax", "3")
        }
        session.setConfig(config)
        session.timeout = connectTimeoutMs

        try {
            session.connect(connectTimeoutMs)
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            if (msg.contains("HostKey has been changed", ignoreCase = true)) {
                throw IllegalStateException("SSH host key changed for $host:$port. Clear pinned SSH keys in Settings if intentional.", e)
            }
            throw e
        }
        return session
    }

    fun execCommand(
        context: Context,
        host: String,
        port: Int,
        user: String,
        password: String,
        command: String,
        privateKeyPem: String = "",
        keyPassphrase: String = "",
        timeoutMs: Int = 8_000
    ): Pair<Int, String> {
        var session: Session? = null
        var channel: ChannelExec? = null
        return try {
            session = openSession(context, host, port, user, password, privateKeyPem, keyPassphrase, timeoutMs)
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            channel.outputStream = out
            channel.setErrStream(err)
            channel.connect(timeoutMs)

            val deadline = System.currentTimeMillis() + timeoutMs
            while (!channel.isClosed && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            val code = if (channel.isClosed) channel.exitStatus else -1
            val combined = buildString {
                val stdout = out.toString("UTF-8")
                val stderr = err.toString("UTF-8")
                if (stdout.isNotEmpty()) append(stdout)
                if (stderr.isNotEmpty()) {
                    if (isNotEmpty()) append('\n')
                    append(stderr)
                }
            }
            code to combined
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    fun testConnection(
        context: Context,
        host: String,
        port: Int,
        user: String,
        password: String
    ): Result<String> = runCatching {
        val (code, output) = execCommand(
            context = context,
            host = host,
            port = port,
            user = user,
            password = password,
            command = "echo OK && command -v tmux || true"
        )
        if (code != 0 && output.isBlank()) {
            error("SSH command failed with exit code $code")
        }
        output.trim().ifBlank { "Connected ($user@$host:$port)" }
    }
}
