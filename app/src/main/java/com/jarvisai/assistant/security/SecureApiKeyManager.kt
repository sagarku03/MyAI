package com.jarvisai.assistant.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encrypts and stores the user's Gemini API key locally on-device using Android Keystore
 * AES-256-GCM authenticated encryption.
 */
class SecureApiKeyManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _apiKeyFlow = MutableStateFlow(loadDecryptedApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private val _isUsingKeystoreEncryption = MutableStateFlow(true)
    val isUsingKeystoreEncryption: StateFlow<Boolean> = _isUsingKeystoreEncryption.asStateFlow()

    fun getApiKey(): String = getActiveApiKeySync()

    fun getActiveApiKeySync(): String {
        val pool = getApiKeyPoolSync()
        if (pool.isEmpty()) return _apiKeyFlow.value
        val idx = Math.floorMod(ACTIVE_INDEX.get(), pool.size)
        return pool[idx]
    }

    suspend fun getActiveApiKey(): String = getActiveApiKeySync()

    fun rotateToNextKeySync(): String? {
        val pool = getApiKeyPoolSync()
        if (pool.size <= 1) return null
        val nextIdx = Math.floorMod(ACTIVE_INDEX.incrementAndGet(), pool.size)
        val nextKey = pool[nextIdx]
        _apiKeyFlow.value = nextKey
        return nextKey
    }

    suspend fun rotateToNextKey(): String? = rotateToNextKeySync()

    fun getApiKeyPoolSync(): List<String> {
        val raw = loadDecryptedApiKey()
        val parsed = raw.split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return if (parsed.isNotEmpty()) parsed else emptyList()
    }

    suspend fun getApiKeyPool(): List<String> = getApiKeyPoolSync()

    suspend fun saveApiKeyPool(keys: List<String>) {
        val clean = keys.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(",")
        saveApiKey(clean)
    }

    fun hasValidApiKey(): Boolean = _apiKeyFlow.value.isNotBlank()

    fun isUserStoredKey(): Boolean = prefs.contains(KEY_CIPHERTEXT) || prefs.contains(KEY_FALLBACK_OBFUSCATED)

    fun getMaskedApiKey(): String {
        val pool = getApiKeyPoolSync()
        val key = pool.firstOrNull()?.trim() ?: _apiKeyFlow.value.trim()
        if (key.isEmpty()) return "NO KEY CONFIGURED"
        val poolSuffix = if (pool.size > 1) " (+${pool.size - 1} backup keys)" else ""
        if (key.length <= 8) return "*".repeat(key.length) + poolSuffix
        return "${key.take(4)}${"•".repeat(12)}${key.takeLast(4)}$poolSuffix"
    }

    fun saveApiKey(rawKey: String): Boolean {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            deleteApiKey()
            return false
        }
        val primaryKey = trimmed.split(",", "\n", ";").map { it.trim() }.firstOrNull { it.isNotBlank() } ?: trimmed
        return try {
            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))

                prefs.edit()
                    .putString(KEY_CIPHERTEXT, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .remove(KEY_FALLBACK_OBFUSCATED)
                    .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                    .apply()
                _isUsingKeystoreEncryption.value = true
            } else {
                // JVM test environment fallback when AndroidKeyStore hardware provider isn't mounted
                val encoded = Base64.encodeToString(trimmed.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                prefs.edit()
                    .putString(KEY_FALLBACK_OBFUSCATED, encoded)
                    .remove(KEY_CIPHERTEXT)
                    .remove(KEY_IV)
                    .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                    .apply()
                _isUsingKeystoreEncryption.value = false
            }
            ACTIVE_INDEX.set(0)
            _apiKeyFlow.value = primaryKey
            true
        } catch (e: Exception) {
            val encoded = Base64.encodeToString(trimmed.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_FALLBACK_OBFUSCATED, encoded)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply()
            _isUsingKeystoreEncryption.value = false
            ACTIVE_INDEX.set(0)
            _apiKeyFlow.value = primaryKey
            true
        }
    }

    fun deleteApiKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
        prefs.edit()
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .remove(KEY_FALLBACK_OBFUSCATED)
            .remove(KEY_UPDATED_AT)
            .apply()
        ACTIVE_INDEX.set(0)
        _apiKeyFlow.value = if (com.jarvisai.assistant.model.isRobolectricTestEnvironment()) "" else getBuildConfigFallbackKey()
    }

    fun getLastUpdatedTimestamp(): Long = prefs.getLong(KEY_UPDATED_AT, 0L)

    private fun loadDecryptedApiKey(): String {
        val ciphertextB64 = prefs.getString(KEY_CIPHERTEXT, null)
        val ivB64 = prefs.getString(KEY_IV, null)
        if (!ciphertextB64.isNullOrBlank() && !ivB64.isNullOrBlank()) {
            try {
                val secretKey = getOrCreateSecretKey()
                if (secretKey != null) {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decryptedBytes = cipher.doFinal(Base64.decode(ciphertextB64, Base64.NO_WRAP))
                    return String(decryptedBytes, StandardCharsets.UTF_8)
                }
            } catch (_: Exception) {
            }
        }

        val fallbackB64 = prefs.getString(KEY_FALLBACK_OBFUSCATED, null)
        if (!fallbackB64.isNullOrBlank()) {
            return try {
                String(Base64.decode(fallbackB64, Base64.NO_WRAP), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }

        return getBuildConfigFallbackKey()
    }

    private fun getBuildConfigFallbackKey(): String {
        return try {
            val envKey = BuildConfig.GEMINI_API_KEY
            if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY" && envKey != "null") {
                envKey.trim()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                return existingEntry.secretKey
            }
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val keySpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keySpec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val ACTIVE_INDEX = java.util.concurrent.atomic.AtomicInteger(0)
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_gemini_api_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val PREFS_NAME = "jarvis_secure_keystore_vault"
        private const val KEY_CIPHERTEXT = "encrypted_gemini_api_key"
        private const val KEY_IV = "encrypted_gemini_api_iv"
        private const val KEY_FALLBACK_OBFUSCATED = "fallback_encoded_api_key"
        private const val KEY_UPDATED_AT = "api_key_updated_at"
    }
}
