package com.jarvisai.assistant.extension

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Installs real Chrome extensions from:
 * - `.crx` (CRX2 & CRX3 binary headers stripped to ZIP)
 * - `.zip` (standard Chrome extension archive, including single-nested root folder support)
 * - `.user.js` (Tampermonkey / Violentmonkey userscript auto-wrapped as MV3 extension)
 * - Chrome Web Store URL or 32-char Extension ID (via clients2.google.com CRX endpoint)
 */
object CrxExtensionInstaller {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val CHROME_STORE_ID_REGEX = Regex("([a-p]{32})", RegexOption.IGNORE_CASE)

    fun extractChromeWebStoreId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.matches(Regex("^[a-pA-P]{32}$"))) {
            return trimmed.lowercase()
        }
        if (trimmed.contains("chromewebstore.google.com") || trimmed.contains("chrome.google.com/webstore")) {
            val pathPart = trimmed.substringBefore("?").substringBefore("#").trimEnd('/')
            val lastSeg = pathPart.substringAfterLast('/')
            if (lastSeg.matches(Regex("^[a-pA-P]{32}$"))) {
                return lastSeg.lowercase()
            }
            return CHROME_STORE_ID_REGEX.find(pathPart)?.groupValues?.getOrNull(1)?.lowercase()
        }
        return null
    }

    fun buildChromeWebStoreDownloadUrl(extensionId: String): String {
        val cleanId = extensionId.trim().lowercase()
        return "https://clients2.google.com/service/update2/crx" +
            "?response=redirect" +
            "&os=linux&arch=x64&os_arch=x86_64&nacl_arch=x86-64" +
            "&prod=chromium&prodchannel=unknown&prodversion=131.0.0.0" +
            "&lang=en-US&acceptformat=crx2,crx3" +
            "&x=id%3D${cleanId}%26installsource%3Dondemand%26uc"
    }

    /**
     * Strips CRX2 or CRX3 header (`Cr24`) and returns pure PKZIP bytes.
     * Supports plain ZIP files seamlessly (pass-through when starting with `PK`).
     */
    fun stripCrxHeaderToZipBytes(rawBytes: ByteArray): ByteArray {
        if (rawBytes.size < 16) {
            throw IllegalArgumentException("Invalid CRX/ZIP archive: file too small (${rawBytes.size} bytes)")
        }
        // Already plain PKZIP (`PK\x03\x04` or `PK\x05\x06`)
        if (rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte()) {
            return rawBytes
        }

        // Check `Cr24` magic
        if (rawBytes[0] == 0x43.toByte() &&
            rawBytes[1] == 0x72.toByte() &&
            rawBytes[2] == 0x32.toByte() &&
            rawBytes[3] == 0x34.toByte()
        ) {
            val buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
            val version = buf.getInt(4)
            val zipStartOffset = when (version) {
                3 -> {
                    val headerSize = buf.getInt(8)
                    if (headerSize < 0 || 12L + headerSize >= rawBytes.size) {
                        throw IllegalArgumentException("Corrupt CRX3 header length: $headerSize")
                    }
                    12 + headerSize
                }
                2 -> {
                    val pubKeyLen = buf.getInt(8)
                    val sigLen = buf.getInt(12)
                    if (pubKeyLen < 0 || sigLen < 0 || 16L + pubKeyLen + sigLen >= rawBytes.size) {
                        throw IllegalArgumentException("Corrupt CRX2 header lengths")
                    }
                    16 + pubKeyLen + sigLen
                }
                else -> {
                    findPkZipMagicOffset(rawBytes)
                        ?: throw IllegalArgumentException("Unsupported CRX format version: $version")
                }
            }
            return rawBytes.copyOfRange(zipStartOffset, rawBytes.size)
        }

        val fallbackOffset = findPkZipMagicOffset(rawBytes)
            ?: throw IllegalArgumentException("File is neither a valid .crx nor a .zip archive")
        return rawBytes.copyOfRange(fallbackOffset, rawBytes.size)
    }

    private fun findPkZipMagicOffset(bytes: ByteArray): Int? {
        val searchLimit = minOf(bytes.size - 4, 8192)
        for (i in 0 until searchLimit) {
            if (bytes[i] == 0x50.toByte() &&
                bytes[i + 1] == 0x4B.toByte() &&
                bytes[i + 2] == 0x03.toByte() &&
                bytes[i + 3] == 0x04.toByte()
            ) {
                return i
            }
        }
        return null
    }

    suspend fun installFromChromeWebStore(
        context: Context,
        urlOrId: String
    ): Result<BrowserExtension> = withContext(Dispatchers.IO) {
        val extId = extractChromeWebStoreId(urlOrId)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Chrome Web Store URL or extension ID"))
        val downloadUrl = buildChromeWebStoreDownloadUrl(extId)
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                .get()
                .build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Chrome Web Store returned HTTP ${resp.code}")
                    )
                }
                val bytes = resp.body?.bytes()
                    ?: return@withContext Result.failure(IllegalStateException("Empty CRX download"))
                installFromArchiveBytes(
                    context = context,
                    rawBytes = bytes,
                    explicitId = extId,
                    sourceType = "webstore",
                    updateUrl = downloadUrl
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun installFromUri(
        context: Context,
        uri: Uri
    ): Result<BrowserExtension> = withContext(Dispatchers.IO) {
        try {
            val fileName = resolveDisplayName(context, uri)
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                input.copyTo(out)
                out.toByteArray()
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected file"))

            if (fileName.endsWith(".user.js", ignoreCase = true) ||
                (!looksLikeBinaryArchive(bytes) && bytes.decodeToString().contains("==UserScript=="))
            ) {
                val baseName = fileName.removeSuffix(".user.js").ifBlank { "userscript" }
                installFromUserScript(context, bytes.decodeToString(), suggestedId = baseName)
            } else {
                val srcType = if (fileName.endsWith(".crx", ignoreCase = true)) "crx" else "zip"
                val suggestedId = fileName
                    .removeSuffix(".crx")
                    .removeSuffix(".zip")
                    .lowercase()
                    .replace(Regex("[^a-z0-9_-]"), "-")
                    .trim('-')
                installFromArchiveBytes(
                    context = context,
                    rawBytes = bytes,
                    explicitId = suggestedId.takeIf { it.isNotBlank() },
                    sourceType = srcType
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installFromArchiveBytes(
        context: Context,
        rawBytes: ByteArray,
        explicitId: String? = null,
        sourceType: String = "zip",
        updateUrl: String = ""
    ): Result<BrowserExtension> {
        return runCatching {
            val zipBytes = stripCrxHeaderToZipBytes(rawBytes)
            val tempDir = File(context.cacheDir, "ext_unpack_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                unpackZipSafely(zipBytes, tempDir)
                val packageRoot = locateManifestRoot(tempDir)
                    ?: throw IllegalArgumentException("Archive does not contain a valid manifest.json")

                val manifestFile = File(packageRoot, "manifest.json")
                val manifestJson = JSONObject(manifestFile.readText())
                val rawName = manifestJson.optString("name", "").trim()
                if (rawName.isBlank()) {
                    throw IllegalArgumentException("manifest.json is missing required 'name' field")
                }

                val stableId = (explicitId?.takeIf { it.isNotBlank() }
                    ?: manifestJson.optString("id").takeIf { it.isNotBlank() }
                    ?: deriveIdFromBytesAndName(rawName, rawBytes))
                    .lowercase()
                    .replace(Regex("[^a-z0-9._-]"), "-")

                val targetDir = File(File(context.filesDir, "extensions").apply { mkdirs() }, stableId)
                if (targetDir.exists()) targetDir.deleteRecursively()
                targetDir.mkdirs()
                packageRoot.copyRecursively(targetDir, overwrite = true)

                writeMetadataFile(
                    dir = targetDir,
                    id = stableId,
                    sourceType = sourceType,
                    updateUrl = updateUrl,
                    enabled = true,
                    allowInPrivate = false
                )

                val manager = BrowserExtensionManager.getInstance(context)
                manager.loadInstalledExtensions(context)
                BrowserExtension.fromManifest(targetDir, manifestJson)
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    fun installFromUserScript(
        context: Context,
        scriptSource: String,
        suggestedId: String = "custom-userscript"
    ): Result<BrowserExtension> {
        return runCatching {
            val meta = parseUserScriptMetadata(scriptSource)
            val name = meta.name.ifBlank { suggestedId.replace('-', ' ') }
            val version = meta.version.ifBlank { "1.0.0" }
            val description = meta.description.ifBlank { "Userscript installed via Jarvis Browser" }
            val runAt = when (meta.runAt.lowercase()) {
                "document-start" -> "document_start"
                "document-end" -> "document_end"
                else -> "document_idle"
            }
            val matches = meta.matches.ifEmpty { listOf("<all_urls>") }

            val stableId = "us-" + (suggestedId.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-').ifBlank {
                name.lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-')
            })

            val extDir = File(File(context.filesDir, "extensions").apply { mkdirs() }, stableId)
            if (extDir.exists()) extDir.deleteRecursively()
            extDir.mkdirs()

            val gmPolyfill = """
                // Jarvis Userscript GM_* Compatibility Wrapper
                (function() {
                    var extId = ${JSONObject.quote(stableId)};
                    window.unsafeWindow = window;
                    window.GM_info = {
                        script: {
                            name: ${JSONObject.quote(name)},
                            version: ${JSONObject.quote(version)},
                            description: ${JSONObject.quote(description)}
                        },
                        scriptHandler: "Jarvis TamperBridge",
                        version: "1.0"
                    };
                    window.GM_addStyle = function(css) {
                        var style = document.createElement('style');
                        style.textContent = css;
                        (document.head || document.documentElement).appendChild(style);
                        return style;
                    };
                    window.GM_getValue = function(k, defVal) {
                        try {
                            var raw = localStorage.getItem('__jarvis_gm_' + extId + '_' + k);
                            return raw !== null ? JSON.parse(raw) : defVal;
                        } catch (e) { return defVal; }
                    };
                    window.GM_setValue = function(k, val) {
                        try {
                            localStorage.setItem('__jarvis_gm_' + extId + '_' + k, JSON.stringify(val));
                        } catch (e) {}
                    };
                    window.GM_deleteValue = function(k) {
                        try { localStorage.removeItem('__jarvis_gm_' + extId + '_' + k); } catch (e) {}
                    };
                    window.GM_openInTab = function(url) { window.open(url, '_blank'); };
                    window.GM_log = function() { console.log.apply(console, arguments); };
                })();
                $scriptSource
            """.trimIndent()

            File(extDir, "userscript.js").writeText(gmPolyfill)

            val manifestJson = JSONObject().apply {
                put("manifest_version", 3)
                put("id", stableId)
                put("name", name)
                put("version", version)
                put("description", description)
                put("permissions", JSONArray(listOf("storage", "activeTab")))
                put(
                    "content_scripts",
                    JSONArray().put(
                        JSONObject().apply {
                            put("matches", JSONArray(matches))
                            put("js", JSONArray(listOf("userscript.js")))
                            put("run_at", runAt)
                        }
                    )
                )
            }
            File(extDir, "manifest.json").writeText(manifestJson.toString(2))
            writeMetadataFile(
                dir = extDir,
                id = stableId,
                sourceType = "userscript",
                updateUrl = "",
                enabled = true,
                allowInPrivate = false
            )

            val manager = BrowserExtensionManager.getInstance(context)
            manager.loadInstalledExtensions(context)
            BrowserExtension.fromManifest(extDir, manifestJson)
        }
    }

    data class UserScriptMeta(
        val name: String = "",
        val version: String = "1.0.0",
        val description: String = "",
        val matches: List<String> = emptyList(),
        val runAt: String = "document-idle"
    )

    fun parseUserScriptMetadata(source: String): UserScriptMeta {
        var name = ""
        var version = "1.0.0"
        var description = ""
        var runAt = "document-idle"
        val matches = mutableListOf<String>()

        var inBlock = false
        for (line in source.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.contains("==UserScript==")) {
                inBlock = true
                continue
            }
            if (trimmed.contains("==/UserScript==")) {
                break
            }
            if (!inBlock) continue
            val clean = trimmed.removePrefix("//").trim()
            if (!clean.startsWith("@")) continue
            val parts = clean.split(Regex("\\s+"), limit = 2)
            val key = parts[0].lowercase()
            val value = parts.getOrNull(1)?.trim().orEmpty()
            if (value.isEmpty()) continue
            when (key) {
                "@name" -> if (name.isEmpty()) name = value
                "@version" -> version = value
                "@description" -> if (description.isEmpty()) description = value
                "@match", "@include" -> matches.add(normalizeUserScriptMatch(value))
                "@run-at" -> runAt = value
            }
        }
        return UserScriptMeta(
            name = name,
            version = version,
            description = description,
            matches = matches.distinct(),
            runAt = runAt
        )
    }

    private fun normalizeUserScriptMatch(raw: String): String {
        if (raw == "*" || raw == "*://*/*" || raw == "<all_urls>") return "<all_urls>"
        if (!raw.contains("://")) return "*://$raw/*"
        return raw
    }

    private fun looksLikeBinaryArchive(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val isZip = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val isCrx = bytes[0] == 0x43.toByte() && bytes[1] == 0x72.toByte() &&
            bytes[2] == 0x32.toByte() && bytes[3] == 0x34.toByte()
        return isZip || isCrx
    }

    private fun unpackZipSafely(zipBytes: ByteArray, destDir: File) {
        val canonicalRoot = destDir.canonicalPath
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                val canonicalOut = outFile.canonicalPath
                if (!canonicalOut.startsWith(canonicalRoot + File.separator) && canonicalOut != canonicalRoot) {
                    throw SecurityException("Blocked unsafe ZIP entry path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun locateManifestRoot(tempDir: File): File? {
        if (File(tempDir, "manifest.json").exists()) return tempDir
        val subDirs = tempDir.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (sub in subDirs) {
            if (File(sub, "manifest.json").exists()) return sub
        }
        return tempDir.walkTopDown()
            .maxDepth(3)
            .firstOrNull { it.isFile && it.name == "manifest.json" }
            ?.parentFile
    }

    fun writeMetadataFile(
        dir: File,
        id: String,
        sourceType: String,
        updateUrl: String,
        enabled: Boolean,
        allowInPrivate: Boolean = false
    ) {
        val json = JSONObject().apply {
            put("id", id)
            put("sourceType", sourceType)
            put("updateUrl", updateUrl)
            put("enabled", enabled)
            put("allowInPrivate", allowInPrivate)
            put("installedAt", System.currentTimeMillis())
        }
        File(dir, BrowserExtension.META_FILE_NAME).writeText(json.toString(2))
    }

    private fun deriveIdFromBytesAndName(name: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes + name.toByteArray())
        // Generate authentic 32-char Chrome-compatible [a-p] Extension ID
        return buildString(32) {
            for (i in 0 until 16) {
                val b = digest[i].toInt() and 0xFF
                append(('a'.code + ((b ushr 4) and 0x0F)).toChar())
                append(('a'.code + (b and 0x0F)).toChar())
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "extension.crx"
    }
}
