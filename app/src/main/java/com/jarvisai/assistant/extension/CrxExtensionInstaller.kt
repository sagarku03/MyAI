package com.jarvisai.assistant.extension

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
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
 * - `.crx` (CRX2 & CRX3 binary headers stripped to ZIP, with authentic public-key/crx_id ID extraction)
 * - `.zip` (standard Chrome extension archive, including single-nested root folder support)
 * - `.user.js` (Tampermonkey / Violentmonkey userscript auto-wrapped as MV3 extension)
 * - Chrome Web Store URL or 32-char Extension ID (via clients2.google.com CRX endpoint)
 */
object CrxExtensionInstaller {

    data class StagedExtensionPreview(
        val stagingDir: File,
        val extension: BrowserExtension,
        val sourceType: String,
        val updateUrl: String,
        val isAlreadyInstalled: Boolean
    )

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

    fun buildChromeWebStoreDownloadUrl(extensionId: String, prod: String = "chromium"): String {
        val cleanId = extensionId.trim().lowercase()
        return "https://clients2.google.com/service/update2/crx" +
            "?response=redirect" +
            "&os=linux&arch=x64&os_arch=x86_64&nacl_arch=x86-64" +
            "&prod=$prod&prodchannel=unknown&prodversion=134.0.0.0" +
            "&lang=en-US&acceptformat=crx2,crx3" +
            "&x=id%3D${cleanId}%26installsource%3Dondemand%26uc"
    }

    /**
     * Converts the first 16 bytes of a SHA-256 digest (or 16-byte CRX3 `crx_id`)
     * into Chromium's lowercase `[a-p]{32}` extension ID representation.
     */
    fun bytes16ToChromeId(bytes16: ByteArray): String? {
        if (bytes16.size < 16) return null
        return buildString(32) {
            for (i in 0 until 16) {
                val b = bytes16[i].toInt() and 0xFF
                append(('a'.code + ((b ushr 4) and 0x0F)).toChar())
                append(('a'.code + (b and 0x0F)).toChar())
            }
        }
    }

    /**
     * Extracts the authentic 32-char Chrome Extension ID from a manifest `"key"` field (Base64 DER).
     */
    fun extractExtensionIdFromManifestKey(base64Key: String?): String? {
        val clean = base64Key?.trim().orEmpty()
        if (clean.isEmpty()) return null
        return runCatching {
            val derBytes = Base64.decode(clean, Base64.DEFAULT)
            if (derBytes.isEmpty()) return null
            val digest = MessageDigest.getInstance("SHA-256").digest(derBytes)
            bytes16ToChromeId(digest)
        }.getOrNull()
    }

    /**
     * Extracts the authentic 32-char Chrome Extension ID from a `.crx` binary's CRX3 protobuf header
     * (`SignedData.crx_id` or `AsymmetricKeyProof.public_key`) or CRX2 public key.
     */
    fun extractCrxExtensionId(rawBytes: ByteArray): String? {
        if (rawBytes.size < 16) return null
        if (rawBytes[0] != 0x43.toByte() ||
            rawBytes[1] != 0x72.toByte() ||
            rawBytes[2] != 0x32.toByte() ||
            rawBytes[3] != 0x34.toByte()
        ) {
            return null
        }
        val buf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.getInt(4)
        return when (version) {
            2 -> {
                val pubKeyLen = buf.getInt(8)
                if (pubKeyLen <= 0 || 16L + pubKeyLen > rawBytes.size) return null
                val pubKey = rawBytes.copyOfRange(16, 16 + pubKeyLen)
                val digest = MessageDigest.getInstance("SHA-256").digest(pubKey)
                bytes16ToChromeId(digest)
            }
            3 -> {
                val headerSize = buf.getInt(8)
                if (headerSize <= 0 || 12L + headerSize > rawBytes.size) return null
                val headerBytes = rawBytes.copyOfRange(12, 12 + headerSize)
                extractCrx3IdFromProtobufHeader(headerBytes)
            }
            else -> null
        }
    }

    private fun extractCrx3IdFromProtobufHeader(headerBytes: ByteArray): String? {
        var pos = 0
        var fallbackPubKey: ByteArray? = null
        while (pos < headerBytes.size) {
            val (tag, nextPos) = readVarint(headerBytes, pos) ?: break
            pos = nextPos
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            when (wireType) {
                2 -> {
                    val (lenLong, afterLen) = readVarint(headerBytes, pos) ?: break
                    val len = lenLong.toInt()
                    if (len < 0 || afterLen + len > headerBytes.size) break
                    val payload = headerBytes.copyOfRange(afterLen, afterLen + len)
                    pos = afterLen + len
                    if (fieldNum == 10000) {
                        extractCrxIdFromSignedData(payload)?.let { return it }
                    } else if ((fieldNum == 2 || fieldNum == 3) && fallbackPubKey == null) {
                        fallbackPubKey = extractField1BytesFromProto(payload)
                    }
                }
                0 -> {
                    val (_, afterVal) = readVarint(headerBytes, pos) ?: break
                    pos = afterVal
                }
                1 -> pos += 8
                5 -> pos += 4
                else -> break
            }
        }
        val key = fallbackPubKey ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return bytes16ToChromeId(digest)
    }

    private fun extractCrxIdFromSignedData(signedDataBytes: ByteArray): String? {
        val rawId = extractField1BytesFromProto(signedDataBytes) ?: return null
        return if (rawId.size == 16) bytes16ToChromeId(rawId) else null
    }

    private fun extractField1BytesFromProto(protoBytes: ByteArray): ByteArray? {
        var pos = 0
        while (pos < protoBytes.size) {
            val (tag, nextPos) = readVarint(protoBytes, pos) ?: break
            pos = nextPos
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 0x7L).toInt()
            when (wireType) {
                2 -> {
                    val (lenLong, afterLen) = readVarint(protoBytes, pos) ?: break
                    val len = lenLong.toInt()
                    if (len < 0 || afterLen + len > protoBytes.size) break
                    val payload = protoBytes.copyOfRange(afterLen, afterLen + len)
                    pos = afterLen + len
                    if (fieldNum == 1) return payload
                }
                0 -> {
                    val (_, afterVal) = readVarint(protoBytes, pos) ?: break
                    pos = afterVal
                }
                1 -> pos += 8
                5 -> pos += 4
                else -> break
            }
        }
        return null
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < bytes.size && shift < 64) {
            val b = bytes[pos++].toInt() and 0xFF
            result = result or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) return result to pos
            shift += 7
        }
        return null
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

    private suspend fun downloadChromeWebStoreArchive(extId: String): Result<Pair<ByteArray, String>> = withContext(Dispatchers.IO) {
        val candidates = listOf(
            buildChromeWebStoreDownloadUrl(extId, prod = "chromium"),
            buildChromeWebStoreDownloadUrl(extId, prod = "chromiumcrx")
        )
        var lastError: String = "Chrome Web Store download failed"
        for (downloadUrl in candidates) {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
                    )
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { resp ->
                    if (resp.code == 204) {
                        lastError = "Chrome Web Store returned HTTP 204 (extension unavailable or restricted for this channel)"
                        return@use
                    }
                    if (!resp.isSuccessful) {
                        lastError = "Chrome Web Store returned HTTP ${resp.code}"
                        return@use
                    }
                    val bytes = resp.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        lastError = "Empty CRX archive from Chrome Web Store"
                        return@use
                    }
                    if (!looksLikeBinaryArchive(bytes) && findPkZipMagicOffset(bytes) == null) {
                        lastError = "Store returned non-archive response (${resp.header("Content-Type") ?: "unknown"})"
                        return@use
                    }
                    return@withContext Result.success(bytes to downloadUrl)
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
            }
        }
        Result.failure(IllegalStateException(lastError))
    }

    suspend fun installFromChromeWebStore(
        context: Context,
        urlOrId: String
    ): Result<BrowserExtension> = withContext(Dispatchers.IO) {
        val extId = extractChromeWebStoreId(urlOrId)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Chrome Web Store URL or extension ID"))
        val downloaded = downloadChromeWebStoreArchive(extId).getOrElse { return@withContext Result.failure(it) }
        installFromArchiveBytes(
            context = context,
            rawBytes = downloaded.first,
            explicitId = extId,
            sourceType = "webstore",
            updateUrl = downloaded.second
        )
    }

    suspend fun stageFromChromeWebStore(
        context: Context,
        urlOrId: String
    ): Result<StagedExtensionPreview> = withContext(Dispatchers.IO) {
        val extId = extractChromeWebStoreId(urlOrId)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Chrome Web Store URL or extension ID"))
        val downloaded = downloadChromeWebStoreArchive(extId).getOrElse { return@withContext Result.failure(it) }
        stageFromArchiveBytes(
            context = context,
            rawBytes = downloaded.first,
            explicitId = extId,
            sourceType = "webstore",
            updateUrl = downloaded.second
        )
    }

    suspend fun stageFromUri(
        context: Context,
        uri: Uri
    ): Result<StagedExtensionPreview> = withContext(Dispatchers.IO) {
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
                stageFromUserScript(context, bytes.decodeToString(), suggestedId = baseName)
            } else {
                val isCrx = fileName.endsWith(".crx", ignoreCase = true) ||
                    (bytes.size >= 4 && bytes[0] == 0x43.toByte() && bytes[1] == 0x72.toByte())
                val srcType = if (isCrx) "crx" else "zip"
                val suggestedSlug = fileName
                    .removeSuffix(".crx")
                    .removeSuffix(".zip")
                    .lowercase()
                    .replace(Regex("[^a-z0-9-]"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
                    .takeIf { it.isNotBlank() }
                stageFromArchiveBytes(
                    context = context,
                    rawBytes = bytes,
                    explicitId = null,
                    fallbackId = suggestedSlug,
                    sourceType = srcType
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
        val staged = stageFromUri(context, uri).getOrElse { return@withContext Result.failure(it) }
        finalizeStagedInstall(context, staged)
    }

    fun stageFromArchiveBytes(
        context: Context,
        rawBytes: ByteArray,
        explicitId: String? = null,
        fallbackId: String? = null,
        sourceType: String = "zip",
        updateUrl: String = ""
    ): Result<StagedExtensionPreview> {
        return runCatching {
            val zipBytes = stripCrxHeaderToZipBytes(rawBytes)
            val stageRoot = File(context.cacheDir, "ext_stage_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                unpackZipSafely(zipBytes, stageRoot)
                val packageRoot = locateManifestRoot(stageRoot)
                    ?: throw IllegalArgumentException("Archive does not contain a valid manifest.json")

                val manifestFile = File(packageRoot, "manifest.json")
                val manifestJson = JSONObject(manifestFile.readText())
                val rawName = manifestJson.optString("name", "").trim()
                if (rawName.isBlank()) {
                    throw IllegalArgumentException("manifest.json is missing required 'name' field")
                }

                val crxHeaderId = extractCrxExtensionId(rawBytes)
                val manifestKeyId = extractExtensionIdFromManifestKey(manifestJson.optString("key", ""))
                val stableId = (explicitId?.takeIf { it.isNotBlank() }
                    ?: crxHeaderId
                    ?: manifestKeyId
                    ?: manifestJson.optString("id").takeIf { it.isNotBlank() }
                    ?: fallbackId?.takeIf { it.isNotBlank() }
                    ?: deriveIdFromBytesAndName(rawName, rawBytes))
                    .lowercase()
                    .replace(Regex("[^a-z0-9-]"), "-")
                    .replace(Regex("-+"), "-")
                    .trim('-')
                    .ifBlank { deriveIdFromBytesAndName(rawName, rawBytes) }

                // If manifest lived in a nested directory, move its contents cleanly to stageRoot
                if (packageRoot.canonicalFile != stageRoot.canonicalFile) {
                    val flatDir = File(context.cacheDir, "ext_stage_flat_${System.currentTimeMillis()}").apply { mkdirs() }
                    packageRoot.copyRecursively(flatDir, overwrite = true)
                    stageRoot.deleteRecursively()
                    flatDir.renameTo(stageRoot)
                }

                writeMetadataFile(
                    dir = stageRoot,
                    id = stableId,
                    sourceType = sourceType,
                    updateUrl = updateUrl,
                    enabled = true,
                    allowInPrivate = false
                )
                val parsedExt = BrowserExtension.fromManifest(stageRoot, manifestJson)
                val alreadyInstalled = File(File(context.filesDir, "extensions"), stableId).exists()
                StagedExtensionPreview(
                    stagingDir = stageRoot,
                    extension = parsedExt,
                    sourceType = sourceType,
                    updateUrl = updateUrl,
                    isAlreadyInstalled = alreadyInstalled
                )
            } catch (e: Exception) {
                stageRoot.deleteRecursively()
                throw e
            }
        }
    }

    fun stageFromUserScript(
        context: Context,
        scriptSource: String,
        suggestedId: String = "custom-userscript"
    ): Result<StagedExtensionPreview> {
        return runCatching {
            val meta = parseUserScriptMetadata(scriptSource)
            val name = meta.name.ifBlank { suggestedId.replace('-', ' ') }
            val version = meta.version.ifBlank { "1.0.0" }
            val description = meta.description.ifBlank { "Userscript installed via Agentra Browser" }
            val runAt = when (meta.runAt.lowercase()) {
                "document-start" -> "document_start"
                "document-end" -> "document_end"
                else -> "document_idle"
            }
            val matches = meta.matches.ifEmpty { listOf("<all_urls>") }

            val stableId = "us-" + (suggestedId.lowercase().replace(Regex("[^a-z0-9-]"), "-").replace(Regex("-+"), "-").trim('-').ifBlank {
                name.lowercase().replace(Regex("[^a-z0-9-]"), "-").replace(Regex("-+"), "-").trim('-')
            }).ifBlank { "script" }

            val stageDir = File(context.cacheDir, "ext_stage_us_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                File(stageDir, "userscript.js").writeText(buildWrappedUserScript(stableId, name, version, description, scriptSource))

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
                                if (meta.excludes.isNotEmpty()) {
                                    put("exclude_matches", JSONArray(meta.excludes))
                                }
                                put("js", JSONArray(listOf("userscript.js")))
                                put("run_at", runAt)
                            }
                        )
                    )
                }
                File(stageDir, "manifest.json").writeText(manifestJson.toString(2))
                writeMetadataFile(
                    dir = stageDir,
                    id = stableId,
                    sourceType = "userscript",
                    updateUrl = "",
                    enabled = true,
                    allowInPrivate = false
                )
                val ext = BrowserExtension.fromManifest(stageDir, manifestJson)
                val alreadyInstalled = File(File(context.filesDir, "extensions"), stableId).exists()
                StagedExtensionPreview(
                    stagingDir = stageDir,
                    extension = ext,
                    sourceType = "userscript",
                    updateUrl = "",
                    isAlreadyInstalled = alreadyInstalled
                )
            } catch (e: Exception) {
                stageDir.deleteRecursively()
                throw e
            }
        }
    }

    fun finalizeStagedInstall(
        context: Context,
        preview: StagedExtensionPreview,
        pinOnInstall: Boolean = false
    ): Result<BrowserExtension> {
        return runCatching {
            val targetDir = File(File(context.filesDir, "extensions").apply { mkdirs() }, preview.extension.id)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()
            preview.stagingDir.copyRecursively(targetDir, overwrite = true)
            writeMetadataFile(
                dir = targetDir,
                id = preview.extension.id,
                sourceType = preview.sourceType,
                updateUrl = preview.updateUrl,
                enabled = true,
                allowInPrivate = preview.extension.allowInPrivate,
                pinned = pinOnInstall || preview.extension.pinned,
                allowUserScripts = preview.extension.allowUserScripts
            )
            val manager = BrowserExtensionManager.getInstance(context)
            manager.loadInstalledExtensions(context)
            if (pinOnInstall) {
                manager.togglePinned(preview.extension.id, true)
            }
            val manifestJson = JSONObject(File(targetDir, "manifest.json").readText())
            BrowserExtension.fromManifest(targetDir, manifestJson)
        }.also {
            discardStagedInstall(preview)
        }
    }

    fun discardStagedInstall(preview: StagedExtensionPreview?) {
        runCatching { preview?.stagingDir?.deleteRecursively() }
    }

    fun installFromArchiveBytes(
        context: Context,
        rawBytes: ByteArray,
        explicitId: String? = null,
        sourceType: String = "zip",
        updateUrl: String = ""
    ): Result<BrowserExtension> {
        val staged = stageFromArchiveBytes(
            context = context,
            rawBytes = rawBytes,
            explicitId = explicitId,
            sourceType = sourceType,
            updateUrl = updateUrl
        ).getOrElse { return Result.failure(it) }
        return finalizeStagedInstall(context, staged)
    }

    fun installFromUserScript(
        context: Context,
        scriptSource: String,
        suggestedId: String = "custom-userscript"
    ): Result<BrowserExtension> {
        val staged = stageFromUserScript(context, scriptSource, suggestedId)
            .getOrElse { return Result.failure(it) }
        return finalizeStagedInstall(context, staged)
    }

    private fun buildWrappedUserScript(
        stableId: String,
        name: String,
        version: String,
        description: String,
        scriptSource: String
    ): String {
        return """
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
                    scriptHandler: "Agentra TamperBridge",
                    version: "1.1"
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
                window.GM_listValues = function() {
                    var prefix = '__jarvis_gm_' + extId + '_';
                    var out = [];
                    try {
                        for (var i = 0; i < localStorage.length; i++) {
                            var key = localStorage.key(i);
                            if (key && key.indexOf(prefix) === 0) out.push(key.substring(prefix.length));
                        }
                    } catch(e) {}
                    return out;
                };
                window.GM_xmlhttpRequest = function(details) {
                    if (!details || !details.url) return;
                    fetch(details.url, {
                        method: details.method || 'GET',
                        headers: details.headers || undefined,
                        body: details.data || undefined
                    }).then(function(resp) {
                        return resp.text().then(function(txt) {
                            var result = { status: resp.status, statusText: resp.statusText, responseText: txt, response: txt, finalUrl: resp.url };
                            if (typeof details.onload === 'function') details.onload(result);
                        });
                    }).catch(function(err) {
                        if (typeof details.onerror === 'function') details.onerror({ error: String(err) });
                    });
                };
                window.GM_registerMenuCommand = function(caption, onClick) {
                    return caption;
                };
                window.GM_notification = function(textOrOpts) {
                    var msg = (typeof textOrOpts === 'string') ? textOrOpts : ((textOrOpts && (textOrOpts.text || textOrOpts.title)) || '');
                    if (msg && window.__jarvis_chrome_bridge) {
                        window.__jarvis_chrome_bridge.showNotification(String(msg));
                    }
                };
                window.GM_openInTab = function(url) { window.open(url, '_blank'); };
                window.GM_log = function() { console.log.apply(console, arguments); };
            })();
            $scriptSource
        """.trimIndent()
    }

    data class UserScriptMeta(
        val name: String = "",
        val version: String = "1.0.0",
        val description: String = "",
        val matches: List<String> = emptyList(),
        val excludes: List<String> = emptyList(),
        val runAt: String = "document-idle"
    )

    fun parseUserScriptMetadata(source: String): UserScriptMeta {
        var name = ""
        var version = "1.0.0"
        var description = ""
        var runAt = "document-idle"
        val matches = mutableListOf<String>()
        val excludes = mutableListOf<String>()

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
                "@exclude", "@exclude-match" -> excludes.add(normalizeUserScriptMatch(value))
                "@run-at" -> runAt = value
            }
        }
        return UserScriptMeta(
            name = name,
            version = version,
            description = description,
            matches = matches.distinct(),
            excludes = excludes.distinct(),
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
        allowInPrivate: Boolean = false,
        pinned: Boolean = false,
        allowUserScripts: Boolean = false
    ) {
        val json = JSONObject().apply {
            put("id", id)
            put("sourceType", sourceType)
            put("updateUrl", updateUrl)
            put("enabled", enabled)
            put("allowInPrivate", allowInPrivate)
            put("pinned", pinned)
            put("allowUserScripts", allowUserScripts)
            put("installedAt", System.currentTimeMillis())
        }
        File(dir, BrowserExtension.META_FILE_NAME).writeText(json.toString(2))
    }

    private fun deriveIdFromBytesAndName(name: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes + name.toByteArray())
        return bytes16ToChromeId(digest) ?: "jarvisext"
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
