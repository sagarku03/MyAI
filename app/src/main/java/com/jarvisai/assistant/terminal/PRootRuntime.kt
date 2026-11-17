package com.jarvisai.assistant.terminal

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

enum class LinuxRuntimeMode(val id: String, val label: String) {
    PROOT_TERMUX("proot_termux", "PRoot Termux (Hybrid)"),
    PROOT_UBUNTU("proot_ubuntu", "Ubuntu 24.04 (PRoot)"),
    DIRECT_NATIVE("direct_native", "Direct Sandbox Shell");

    companion object {
        fun fromId(id: String?): LinuxRuntimeMode {
            return entries.firstOrNull { it.id == id } ?: PROOT_TERMUX
        }
    }
}

data class PRootInvocation(
    val executablePath: String,
    val argv: List<String>,
    val hostWorkingDir: File,
    val guestWorkingDir: String,
    val hostEnv: Map<String, String>,
    val mode: LinuxRuntimeMode
)

/**
 * Option A Virtualization Engine: PRoot Hybrid Virtual Bind-Mount & Ubuntu RootFS Runtime.
 *
 * Maps app-private filesDir ({usr, home}) onto virtual `/data/data/com.termux/files/{usr,home}`
 * inside guest processes without modifying external `com.termux` storage or requiring SharedUserId.
 * Falls back automatically to Direct Sandbox / `/system/bin/sh` if PRoot binary is not yet provisioned.
 */
object PRootRuntime {
    private const val TAG = "PRootRuntime"
    private const val PREFS_NAME = "jarvis_local_env_cache"
    private const val KEY_RUNTIME_MODE = "linux_runtime_mode"

    const val VIRTUAL_TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val VIRTUAL_TERMUX_HOME = "/data/data/com.termux/files/home"
    const val UBUNTU_HOME_BIND = "/root/jarvis-home"

    fun getProotRuntimeDir(context: Context): File =
        File(context.filesDir, "proot-runtime").apply { runCatching { mkdirs() } }

    fun getUbuntuRootfsDir(context: Context): File =
        File(context.filesDir, "rootfs/ubuntu").apply { runCatching { mkdirs() } }

    fun getRuntimeMode(context: Context): LinuxRuntimeMode {
        if (LocalEnvironmentManager.isTermuxFlavor(context)) {
            return LinuxRuntimeMode.DIRECT_NATIVE
        }
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_RUNTIME_MODE, LinuxRuntimeMode.PROOT_TERMUX.id)
        return LinuxRuntimeMode.fromId(raw)
    }

    fun setRuntimeMode(context: Context, mode: LinuxRuntimeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RUNTIME_MODE, mode.id)
            .apply()
    }

    fun resolveProotBinary(context: Context): File? {
        val prefixBin = LocalEnvironmentManager.getBinDir(context)
        val runtimeBin = File(getProotRuntimeDir(context), "bin")
        val nativeDir = context.applicationInfo?.nativeLibraryDir?.let { File(it) }

        val candidates = listOfNotNull(
            nativeDir?.let { File(it, "libproot.so") },
            File(runtimeBin, "proot"),
            File(prefixBin, "proot")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 10_000L) {
                ensureExecutable(candidate)
                if (candidate.canExecute() || candidate.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    return candidate
                }
            }
        }
        return null
    }

    fun resolveLoaderBinary(context: Context): File? {
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        val runtimeDir = getProotRuntimeDir(context)
        val nativeDir = context.applicationInfo?.nativeLibraryDir?.let { File(it) }

        val candidates = listOfNotNull(
            File(runtimeDir, "libexec/proot/loader"),
            File(prefixDir, "libexec/proot/loader"),
            nativeDir?.let { File(it, "libproot-loader.so") }
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                ensureExecutable(candidate)
                return candidate
            }
        }
        return null
    }

    fun resolveLoader32Binary(context: Context): File? {
        val prefixDir = LocalEnvironmentManager.getPrefixDir(context)
        val runtimeDir = getProotRuntimeDir(context)
        val candidates = listOf(
            File(runtimeDir, "libexec/proot/loader32"),
            File(prefixDir, "libexec/proot/loader32")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                ensureExecutable(candidate)
                return candidate
            }
        }
        return null
    }

    fun isProotReady(context: Context): Boolean = resolveProotBinary(context) != null

    fun isUbuntuRootfsInstalled(context: Context): Boolean {
        val rootfs = getUbuntuRootfsDir(context)
        return File(rootfs, "bin/bash").exists() ||
            File(rootfs, "usr/bin/bash").exists() ||
            File(rootfs, "bin/sh").exists()
    }

    /**
     * Returns true only when PRoot binary is present and the selected PRoot target rootfs is valid,
     * ensuring seamless automatic fallback to direct native shell when not provisioned.
     */
    fun shouldUsePRoot(context: Context): Boolean {
        if (LocalEnvironmentManager.isTermuxFlavor(context)) return false
        val mode = getRuntimeMode(context)
        if (mode == LinuxRuntimeMode.DIRECT_NATIVE) return false
        if (!isProotReady(context)) return false
        return when (mode) {
            LinuxRuntimeMode.PROOT_UBUNTU -> isUbuntuRootfsInstalled(context)
            LinuxRuntimeMode.PROOT_TERMUX -> {
                val binDir = LocalEnvironmentManager.getBinDir(context)
                File(binDir, "bash").exists() || File(binDir, "sh").exists()
            }
            LinuxRuntimeMode.DIRECT_NATIVE -> false
        }
    }

    fun buildActiveInvocation(
        context: Context,
        command: String? = null,
        workingDir: String? = null,
        loginShell: Boolean = true
    ): PRootInvocation? {
        if (!shouldUsePRoot(context)) return null
        return when (getRuntimeMode(context)) {
            LinuxRuntimeMode.PROOT_UBUNTU -> buildUbuntuProotInvocation(context, command, workingDir, loginShell)
            LinuxRuntimeMode.PROOT_TERMUX -> buildTermuxProotInvocation(context, command, workingDir, loginShell)
            LinuxRuntimeMode.DIRECT_NATIVE -> null
        }
    }

    fun mapHostToTermuxGuestPath(
        rawPath: String?,
        hostPrefix: File,
        hostHome: File
    ): String {
        val clean = rawPath?.trim().orEmpty()
        if (clean.isBlank()) return "$VIRTUAL_TERMUX_HOME/jarvis-workspace/projects"
        if (clean.startsWith(VIRTUAL_TERMUX_HOME) || clean.startsWith(VIRTUAL_TERMUX_PREFIX)) {
            return clean
        }
        val homeAbs = hostHome.absolutePath
        if (clean == homeAbs || clean.startsWith("$homeAbs/")) {
            return VIRTUAL_TERMUX_HOME + clean.removePrefix(homeAbs)
        }
        val prefixAbs = hostPrefix.absolutePath
        if (clean == prefixAbs || clean.startsWith("$prefixAbs/")) {
            return VIRTUAL_TERMUX_PREFIX + clean.removePrefix(prefixAbs)
        }
        if (clean.startsWith("/sdcard") || clean.startsWith("/storage")) {
            return clean
        }
        return VIRTUAL_TERMUX_HOME
    }

    fun buildTermuxProotInvocation(
        context: Context,
        command: String? = null,
        workingDir: String? = null,
        loginShell: Boolean = true
    ): PRootInvocation? {
        val prootBin = resolveProotBinary(context) ?: return null
        val hostPrefix = LocalEnvironmentManager.getPrefixDir(context)
        val hostHome = LocalEnvironmentManager.getHomeDir(context)
        val hostTmp = LocalEnvironmentManager.getTmpDir(context)
        val hostWorkingDir = LocalEnvironmentManager.resolveWritableWorkingDir(context, workingDir)

        LocalEnvironmentManager.ensureGitAndTlsConfigured(hostPrefix, hostHome)
        LocalEnvironmentManager.ensureDnsAndHostsConfigured(hostPrefix)

        // Ensure standard home/.npm-global & .local/bin exist for npm global CLI tools
        runCatching { File(hostHome, ".npm-global/bin").mkdirs() }
        runCatching { File(hostHome, ".local/bin").mkdirs() }

        val guestWorkingDir = mapHostToTermuxGuestPath(
            hostWorkingDir.absolutePath,
            hostPrefix,
            hostHome
        )

        val hostEnv = buildHostEnvForProot(context, hostPrefix, hostHome, hostTmp)
        val argv = buildTermuxProotArgv(
            context = context,
            hostPrefix = hostPrefix,
            hostHome = hostHome,
            guestWorkingDir = guestWorkingDir,
            command = command,
            loginShell = loginShell
        )

        return PRootInvocation(
            executablePath = prootBin.absolutePath,
            argv = argv,
            hostWorkingDir = hostWorkingDir,
            guestWorkingDir = guestWorkingDir,
            hostEnv = hostEnv,
            mode = LinuxRuntimeMode.PROOT_TERMUX
        )
    }

    fun buildUbuntuProotInvocation(
        context: Context,
        command: String? = null,
        workingDir: String? = null,
        loginShell: Boolean = true
    ): PRootInvocation? {
        val prootBin = resolveProotBinary(context) ?: return null
        val rootfs = getUbuntuRootfsDir(context)
        if (!isUbuntuRootfsInstalled(context)) return null

        val hostPrefix = LocalEnvironmentManager.getPrefixDir(context)
        val hostHome = LocalEnvironmentManager.getHomeDir(context)
        val hostTmp = LocalEnvironmentManager.getTmpDir(context)
        val hostWorkingDir = LocalEnvironmentManager.resolveWritableWorkingDir(context, workingDir)

        // Ensure Ubuntu rootfs DNS & hosts are present
        runCatching {
            val etc = File(rootfs, "etc").apply { mkdirs() }
            val resolv = File(etc, "resolv.conf")
            if (!resolv.exists() || resolv.length() == 0L) {
                resolv.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            }
            val hosts = File(etc, "hosts")
            if (!hosts.exists() || hosts.length() == 0L) {
                hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")
            }
        }

        val guestWorkingDir = if (hostWorkingDir.absolutePath.startsWith(hostHome.absolutePath)) {
            UBUNTU_HOME_BIND + hostWorkingDir.absolutePath.removePrefix(hostHome.absolutePath)
        } else {
            "$UBUNTU_HOME_BIND/jarvis-workspace/projects"
        }

        val hostEnv = buildHostEnvForProot(context, hostPrefix, hostHome, hostTmp)
        val argv = mutableListOf(
            "--link2symlink",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${hostHome.absolutePath}:$UBUNTU_HOME_BIND"
        )
        if (File("/dev/urandom").exists()) {
            argv.addAll(listOf("-b", "/dev/urandom:/dev/random"))
        }
        if (File("/sdcard").exists()) {
            argv.addAll(listOf("-b", "/sdcard:/sdcard"))
        }
        argv.addAll(listOf("-w", guestWorkingDir))

        val shellBin = if (File(rootfs, "bin/bash").exists() || File(rootfs, "usr/bin/bash").exists()) {
            "/bin/bash"
        } else {
            "/bin/sh"
        }
        val envBin = if (File(rootfs, "usr/bin/env").exists()) "/usr/bin/env" else "env"
        argv.addAll(
            listOf(
                envBin,
                "-i",
                "HOME=/root",
                "USER=root",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=C.UTF-8",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$UBUNTU_HOME_BIND/.local/bin:$UBUNTU_HOME_BIND/.npm-global/bin",
                "JARVIS_MCP_URL=http://127.0.0.1:${LocalServerManager.getHubPort(context)}",
                "JARVIS_BROWSER_MCP_URL=http://127.0.0.1:${com.example.BuildConfig.BROWSER_MCP_PORT}",
                "npm_config_prefix=$UBUNTU_HOME_BIND/.npm-global",
                shellBin
            )
        )
        if (command != null) {
            argv.addAll(listOf("-c", command))
        } else if (loginShell && shellBin == "/bin/bash") {
            argv.add("--login")
        }

        return PRootInvocation(
            executablePath = prootBin.absolutePath,
            argv = argv,
            hostWorkingDir = hostWorkingDir,
            guestWorkingDir = guestWorkingDir,
            hostEnv = hostEnv,
            mode = LinuxRuntimeMode.PROOT_UBUNTU
        )
    }

    private fun buildHostEnvForProot(
        context: Context,
        hostPrefix: File,
        hostHome: File,
        hostTmp: File
    ): Map<String, String> {
        val env = linkedMapOf<String, String>()
        val runtimeLibDir = File(getProotRuntimeDir(context), "lib")
        val libPaths = buildList {
            if (runtimeLibDir.exists()) add(runtimeLibDir.absolutePath)
            add("${hostPrefix.absolutePath}/lib")
            add("${hostHome.absolutePath}/.local/lib")
        }.joinToString(":")

        env["HOME"] = hostHome.absolutePath
        env["PREFIX"] = hostPrefix.absolutePath
        env["TMPDIR"] = hostTmp.absolutePath
        env["PROOT_TMP_DIR"] = hostTmp.absolutePath
        env["LD_LIBRARY_PATH"] = libPaths
        env["PATH"] = "${getProotRuntimeDir(context).absolutePath}/bin:${hostPrefix.absolutePath}/bin:/system/bin:/system/xbin"
        env["TERM"] = "xterm-256color"
        env["LANG"] = "en_US.UTF-8"

        resolveLoaderBinary(context)?.let { env["PROOT_LOADER"] = it.absolutePath }
        resolveLoader32Binary(context)?.let { env["PROOT_LOADER_32"] = it.absolutePath }
        // CRITICAL: Never pass LD_PRELOAD to PRoot itself (conflicts with ptrace syscall trapping)
        env.remove("LD_PRELOAD")
        return env
    }

    private fun buildTermuxProotArgv(
        context: Context,
        hostPrefix: File,
        hostHome: File,
        guestWorkingDir: String,
        command: String?,
        loginShell: Boolean
    ): List<String> {
        val argv = mutableListOf(
            "--link2symlink",
            "-0",
            "-b", "${hostPrefix.absolutePath}:$VIRTUAL_TERMUX_PREFIX",
            "-b", "${hostHome.absolutePath}:$VIRTUAL_TERMUX_HOME",
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys"
        )

        val resolvConf = File(hostPrefix, "etc/resolv.conf")
        if (resolvConf.exists()) {
            argv.addAll(listOf("-b", "${resolvConf.absolutePath}:/etc/resolv.conf"))
        }
        val hostsFile = File(hostPrefix, "etc/hosts")
        if (hostsFile.exists()) {
            argv.addAll(listOf("-b", "${hostsFile.absolutePath}:/etc/hosts"))
        }
        if (File("/dev/urandom").exists()) {
            argv.addAll(listOf("-b", "/dev/urandom:/dev/random"))
        }
        if (File("/proc/self/fd").exists()) {
            argv.addAll(listOf("-b", "/proc/self/fd:/dev/fd"))
        }
        if (File("/sdcard").exists()) {
            argv.addAll(listOf("-b", "/sdcard:/sdcard"))
        }

        argv.addAll(listOf("-w", guestWorkingDir))

        val envExecutable = if (File(hostPrefix, "bin/env").exists()) {
            "$VIRTUAL_TERMUX_PREFIX/bin/env"
        } else {
            "/system/bin/env"
        }
        val guestShell = when {
            File(hostPrefix, "bin/bash").exists() -> "$VIRTUAL_TERMUX_PREFIX/bin/bash"
            File(hostPrefix, "bin/sh").exists() -> "$VIRTUAL_TERMUX_PREFIX/bin/sh"
            else -> "/system/bin/sh"
        }

        argv.addAll(
            listOf(
                envExecutable,
                "-i",
                "HOME=$VIRTUAL_TERMUX_HOME",
                "PREFIX=$VIRTUAL_TERMUX_PREFIX",
                "TMPDIR=$VIRTUAL_TERMUX_PREFIX/tmp",
                "PATH=$VIRTUAL_TERMUX_PREFIX/bin:$VIRTUAL_TERMUX_PREFIX/bin/applets:$VIRTUAL_TERMUX_HOME/.local/bin:$VIRTUAL_TERMUX_HOME/.npm-global/bin:/system/bin:/system/xbin",
                "LD_LIBRARY_PATH=$VIRTUAL_TERMUX_PREFIX/lib:$VIRTUAL_TERMUX_HOME/.local/lib",
                "TERM=xterm-256color",
                "COLORTERM=truecolor",
                "LANG=en_US.UTF-8",
                "LC_ALL=en_US.UTF-8",
                "GIT_CONFIG_NOSYSTEM=1",
                "GIT_CONFIG_GLOBAL=$VIRTUAL_TERMUX_HOME/.gitconfig",
                "JARVIS_MCP_URL=http://127.0.0.1:${LocalServerManager.getHubPort(context)}",
                "JARVIS_BROWSER_MCP_URL=http://127.0.0.1:${com.example.BuildConfig.BROWSER_MCP_PORT}",
                "npm_config_prefix=$VIRTUAL_TERMUX_HOME/.npm-global"
            )
        )

        if (File(hostPrefix, "etc/tls/cert.pem").exists()) {
            val virtualCert = "$VIRTUAL_TERMUX_PREFIX/etc/tls/cert.pem"
            argv.add("SSL_CERT_FILE=$virtualCert")
            argv.add("CURL_CA_BUNDLE=$virtualCert")
            argv.add("GIT_SSL_CAINFO=$virtualCert")
        }
        if (File(hostPrefix, "libexec/git-core").exists()) {
            argv.add("GIT_EXEC_PATH=$VIRTUAL_TERMUX_PREFIX/libexec/git-core")
        }
        if (File(hostPrefix, "share/git-core/templates").exists()) {
            argv.add("GIT_TEMPLATE_DIR=$VIRTUAL_TERMUX_PREFIX/share/git-core/templates")
        }

        argv.add(guestShell)
        if (command != null) {
            argv.addAll(listOf("-c", command))
        } else if (loginShell && guestShell.endsWith("/bash")) {
            argv.add("-l")
        }
        return argv
    }

    /**
     * Parses Termux `SYMLINKS.txt` lines formatted as `target←linkPath` (`\u2190`).
     */
    fun parseTermuxSymlinksTxt(content: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val sepIdx = line.indexOf('\u2190')
            if (sepIdx <= 0 || sepIdx >= line.length - 1) return@forEach
            val target = line.substring(0, sepIdx).trim()
            val linkPath = line.substring(sepIdx + 1).trim().removePrefix("./")
            if (target.isNotEmpty() && linkPath.isNotEmpty()) {
                result.add(target to linkPath)
            }
        }
        return result
    }

    internal fun ensureExecutable(file: File) {
        if (!file.exists()) return
        runCatching {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            Os.chmod(file.absolutePath, 493) // 0755
        }
    }

    internal fun createSymlinkCompat(target: String, linkFile: File) {
        try {
            linkFile.parentFile?.mkdirs()
            val linkPath = linkFile.toPath()
            Files.deleteIfExists(linkPath)
            runCatching {
                Os.symlink(target, linkFile.absolutePath)
            }
            if (!Files.isSymbolicLink(linkPath)) {
                Files.deleteIfExists(linkPath)
                Files.createSymbolicLink(linkPath, Paths.get(target))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed creating symlink ${linkFile.absolutePath} -> $target: ${e.message}")
        }
    }
}
