package com.jarvisai.assistant.data.github

import android.content.Context
import com.jarvisai.assistant.terminal.LocalEnvironmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

data class GitHubRepoItem(
    val fullName: String,
    val description: String,
    val url: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val updatedAt: String
)

data class GitHubState(
    val checking: Boolean = false,
    val ghInstalled: Boolean? = null,
    val ghVersion: String = "",
    val signedIn: Boolean = false,
    val username: String = "",
    val gitUserName: String = "",
    val gitUserEmail: String = "",
    val repos: List<GitHubRepoItem> = emptyList(),
    val reposLoading: Boolean = false,
    val reposLoaded: Boolean = false,
    val busyAction: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

/**
 * Manages GitHub CLI (`gh`), Git identity configuration, repository listing, and isolated
 * project cloning inside Agentra's isolated workspace (`jarvis-workspace/projects`).
 */
object GitHubManager {

    private val _state = MutableStateFlow(GitHubState())
    val state: StateFlow<GitHubState> = _state.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, isError = false) }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        _state.update { it.copy(checking = true) }

        val versionLines = mutableListOf<String>()
        val verExit = runCatching {
            LocalEnvironmentManager.executeCommandLive(ctx, "gh --version 2>&1", timeoutMs = 15_000L) {
                versionLines.add(it)
            }
        }.getOrDefault(-1)

        val installed = verExit == 0
        val versionStr = versionLines.firstOrNull { it.contains("gh version", ignoreCase = true) }?.trim().orEmpty()

        var signedIn = false
        var username = ""
        if (installed) {
            val authLines = mutableListOf<String>()
            val authExit = runCatching {
                LocalEnvironmentManager.executeCommandLive(ctx, "gh auth status 2>&1", timeoutMs = 15_000L) {
                    authLines.add(it)
                }
            }.getOrDefault(-1)
            val combined = authLines.joinToString("\n")
            signedIn = authExit == 0 || combined.contains("Logged in to github.com", ignoreCase = true)
            if (signedIn) {
                val m = Regex("""account\s+([A-Za-z0-9_-]+)""").find(combined)
                username = m?.groupValues?.getOrNull(1).orEmpty()
                if (username.isBlank()) {
                    val apiOut = mutableListOf<String>()
                    LocalEnvironmentManager.executeCommandLive(ctx, "gh api user --jq .login 2>/dev/null", timeoutMs = 10_000L) {
                        apiOut.add(it)
                    }
                    username = apiOut.firstOrNull()?.trim().orEmpty()
                }
            }
        }

        val gitNameOut = mutableListOf<String>()
        val gitEmailOut = mutableListOf<String>()
        runCatching {
            LocalEnvironmentManager.executeCommandLive(ctx, "git config --global user.name 2>/dev/null", timeoutMs = 8_000L) {
                gitNameOut.add(it)
            }
            LocalEnvironmentManager.executeCommandLive(ctx, "git config --global user.email 2>/dev/null", timeoutMs = 8_000L) {
                gitEmailOut.add(it)
            }
        }

        _state.update {
            it.copy(
                checking = false,
                ghInstalled = installed,
                ghVersion = versionStr,
                signedIn = signedIn,
                username = username,
                gitUserName = gitNameOut.firstOrNull()?.trim().orEmpty(),
                gitUserEmail = gitEmailOut.firstOrNull()?.trim().orEmpty()
            )
        }
    }

    suspend fun installGhCli() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        _state.update { it.copy(busyAction = true, message = "Installing GitHub CLI (gh)...", isError = false) }
        val log = mutableListOf<String>()
        val exit = runCatching {
            LocalEnvironmentManager.executeCommandLive(
                ctx,
                "if command -v pkg >/dev/null 2>&1; then pkg install -y gh git; elif command -v apt-get >/dev/null 2>&1; then apt-get update && apt-get install -y gh git; else echo 'Package manager not found'; exit 1; fi",
                timeoutMs = 180_000L
            ) { log.add(it) }
        }.getOrDefault(-1)

        _state.update {
            it.copy(
                busyAction = false,
                message = if (exit == 0) "GitHub CLI installed." else (log.lastOrNull() ?: "Install failed (exit $exit)"),
                isError = exit != 0
            )
        }
        refresh()
    }

    suspend fun signInWithToken(token: String) = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        val clean = token.trim()
        if (clean.isBlank()) {
            _state.update { it.copy(message = "Please enter a GitHub personal access token.", isError = true) }
            return@withContext
        }
        _state.update { it.copy(busyAction = true, message = "Authenticating with GitHub...", isError = false) }
        val escaped = clean.replace("'", "'\"'\"'")
        val out = mutableListOf<String>()
        val exit = runCatching {
            LocalEnvironmentManager.executeCommandLive(
                ctx,
                "printf '%s\\n' '$escaped' | gh auth login --with-token && gh auth setup-git",
                timeoutMs = 30_000L
            ) { out.add(it) }
        }.getOrDefault(-1)

        _state.update {
            it.copy(
                busyAction = false,
                message = if (exit == 0) "Signed in to GitHub and configured git credentials." else (out.lastOrNull() ?: "Sign-in failed (exit $exit)"),
                isError = exit != 0
            )
        }
        refresh()
        if (exit == 0) loadRepos()
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        _state.update { it.copy(busyAction = true) }
        runCatching {
            LocalEnvironmentManager.executeCommandLive(ctx, "gh auth logout -h github.com -y 2>&1 || true", timeoutMs = 15_000L) {}
        }
        _state.update {
            it.copy(
                busyAction = false,
                signedIn = false,
                username = "",
                repos = emptyList(),
                reposLoaded = false,
                message = "Signed out of GitHub.",
                isError = false
            )
        }
    }

    suspend fun saveGitIdentity(name: String, email: String) = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        _state.update { it.copy(busyAction = true) }
        val safeName = name.trim().replace("'", "'\"'\"'")
        val safeEmail = email.trim().replace("'", "'\"'\"'")
        val cmd = buildString {
            if (safeName.isNotBlank()) append("git config --global user.name '$safeName'; ")
            if (safeEmail.isNotBlank()) append("git config --global user.email '$safeEmail'; ")
            append("gh auth setup-git 2>/dev/null || true")
        }
        val exit = runCatching {
            LocalEnvironmentManager.executeCommandLive(ctx, cmd, timeoutMs = 15_000L) {}
        }.getOrDefault(-1)

        _state.update {
            it.copy(
                busyAction = false,
                gitUserName = name.trim(),
                gitUserEmail = email.trim(),
                message = if (exit == 0) "Git identity saved." else "Could not update git config.",
                isError = exit != 0
            )
        }
    }

    suspend fun loadRepos() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext
        _state.update { it.copy(reposLoading = true) }
        val out = StringBuilder()
        val exit = runCatching {
            LocalEnvironmentManager.executeCommandLive(
                ctx,
                "gh repo list --limit 40 --json nameWithOwner,description,url,isPrivate,defaultBranchRef,updatedAt 2>/dev/null",
                timeoutMs = 25_000L
            ) { out.append(it) }
        }.getOrDefault(-1)

        val parsed = if (exit == 0) {
            runCatching {
                val arr = JSONArray(out.toString().trim())
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    GitHubRepoItem(
                        fullName = o.optString("nameWithOwner"),
                        description = o.optString("description").takeIf { it != "null" }.orEmpty(),
                        url = o.optString("url"),
                        isPrivate = o.optBoolean("isPrivate", false),
                        defaultBranch = o.optJSONObject("defaultBranchRef")?.optString("name").orEmpty().ifBlank { "main" },
                        updatedAt = o.optString("updatedAt").take(10)
                    )
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        _state.update {
            it.copy(
                reposLoading = false,
                reposLoaded = true,
                repos = parsed
            )
        }
    }

    suspend fun cloneRepository(
        context: Context,
        repoUrl: String,
        targetFolderName: String,
        onLog: (String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanUrl = repoUrl.trim()
        val inferred = cleanUrl.trimEnd('/').substringAfterLast('/').removeSuffix(".git")
        val folder = targetFolderName.trim().ifBlank { inferred }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (folder.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid repository folder name"))
        }
        val projectsDir = LocalEnvironmentManager.getIsolatedProjectsDir(context)
        projectsDir.mkdirs()
        val destDir = File(projectsDir, folder)
        if (destDir.exists()) {
            return@withContext Result.failure(IllegalStateException("Folder '${destDir.name}' already exists in projects"))
        }

        val escapedUrl = cleanUrl.replace("'", "'\"'\"'")
        val escapedDest = destDir.absolutePath.replace("'", "'\"'\"'")
        onLog("Cloning into ${destDir.absolutePath}...")
        val exit = LocalEnvironmentManager.executeCommandLive(
            context = context,
            command = "git clone --progress '$escapedUrl' '$escapedDest' 2>&1",
            workingDir = projectsDir,
            timeoutMs = 300_000L,
            onLine = onLog
        )
        if (exit == 0 && destDir.exists()) {
            Result.success(destDir)
        } else {
            Result.failure(RuntimeException("git clone exited with code $exit"))
        }
    }
}
