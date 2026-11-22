package com.jarvisai.assistant.terminal

enum class RootfsMirror(
    val id: String,
    val displayName: String,
    val url: String,
    val description: String
) {
    GITHUB_MAIN(
        id = "GITHUB_MAIN",
        displayName = "Agentra Primary Release (GitHub)",
        url = "https://github.com/sagarku03/MyAI/releases/download/rootfs/agentra-rootfs.zip",
        description = "Agentra full Node.js, Python, Git, Zsh, OpenSSH & CLI toolchain (~400 MB)"
    ),
    HUGGING_FACE(
        id = "HUGGING_FACE",
        displayName = "Fallback Mirror (HuggingFace)",
        url = "https://huggingface.co/datasets/Santoshkurmi/antigem-rootfs/resolve/main/bootrapz_2026-11-19.zip",
        description = "Fallback CDN mirror if primary GitHub release is unreachable"
    ),
    TERMUX_OFFICIAL(
        id = "TERMUX_OFFICIAL",
        displayName = "Minimal Termux Bootstrap (GitHub)",
        url = "https://github.com/termux/termux-packages/releases/download/bootstrap-2024.10.04-r1%2Bapt-android-7/bootstrap-aarch64.zip",
        description = "Lightweight core Termux bootstrap (~35 MB base)"
    );

    companion object {
        fun fromId(id: String?): RootfsMirror {
            return entries.firstOrNull { it.id == id } ?: GITHUB_MAIN
        }
    }
}

data class DiscoveredPackageInfo(
    val url: String,
    val releaseTag: String,
    val assetName: String,
    val arch: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val packageName: String,
    val isLocal: Boolean = false
)

data class DeviceHardwareInfo(
    val primaryAbi: String,
    val supported64Abis: List<String>,
    val supported32Abis: List<String>,
    val is64BitSupported: Boolean,
    val vaBits: Int,
    val hasCpuAtomics: Boolean,
    val kernelRelease: String,
    val hardwareWarning: String? = null
)

sealed class LocalInstallerState {
    object Idle : LocalInstallerState()
    data class Discovering(
        val message: String = "Checking device hardware and bootstrap sources..."
    ) : LocalInstallerState()
    data class AwaitingConfirmation(
        val packageInfo: DiscoveredPackageInfo
    ) : LocalInstallerState()
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressFraction: Float,
        val speedText: String,
        val currentPackageName: String
    ) : LocalInstallerState()
    data class Extracting(
        val extractedFilesCount: Int,
        val totalFilesEstimate: Int,
        val progressFraction: Float,
        val currentFileName: String
    ) : LocalInstallerState()
    data class Configuring(
        val stepDescription: String,
        val progressFraction: Float
    ) : LocalInstallerState()
    data class Verifying(
        val testName: String
    ) : LocalInstallerState()
    data class Success(
        val message: String,
        val prefixPath: String,
        val totalDiskUsageFormatted: String
    ) : LocalInstallerState()
    data class Error(
        val errorMessage: String,
        val canRetry: Boolean = true
    ) : LocalInstallerState()
}
