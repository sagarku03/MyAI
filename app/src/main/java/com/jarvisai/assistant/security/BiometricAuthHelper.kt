package com.jarvisai.assistant.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class BiometricAuthResult {
    data object Authenticated : BiometricAuthResult()
    data class Failed(val reason: String) : BiometricAuthResult()
    data object Cancelled : BiometricAuthResult()
}

/**
 * Hardware biometric and device credential helper using Android's platform BiometricPrompt
 * and KeyguardManager APIs. Works directly with ComponentActivity without requiring FragmentActivity.
 */
object BiometricAuthHelper {

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun isAuthenticationAvailable(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isDeviceSecure == true) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val biometricManager = context.getSystemService(BiometricManager::class.java)
            val result = biometricManager?.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            if (result == BiometricManager.BIOMETRIC_SUCCESS) {
                return true
            }
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }

    fun authenticateHighRiskAction(
        context: Context,
        title: String = "Confirm High-Risk Action",
        subtitle: String = "Verify your identity to execute this sensitive command",
        onLaunchCredentialIntent: (Intent) -> Unit,
        onResult: (BiometricAuthResult) -> Unit
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onResult(BiometricAuthResult.Failed("Unable to access host Activity for biometric prompt."))
            return
        }

        if (!isAuthenticationAvailable(activity)) {
            onResult(
                BiometricAuthResult.Failed(
                    "No biometric sensor or screen lock is configured on this device. Set up a PIN/biometric in Android Settings or disable Biometric Gate."
                )
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cancellationSignal = CancellationSignal()
                val builder = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription("JARVIS Safety Gate requires biometric or device lock verification.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                } else {
                    builder.setNegativeButton("Cancel", activity.mainExecutor) { _, _ ->
                        onResult(BiometricAuthResult.Cancelled)
                    }
                }

                val prompt = builder.build()
                prompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            onResult(BiometricAuthResult.Authenticated)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                            ) {
                                onResult(BiometricAuthResult.Cancelled)
                            } else {
                                val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                @Suppress("DEPRECATION")
                                val fallbackIntent = keyguardManager?.createConfirmDeviceCredentialIntent(title, subtitle)
                                if (fallbackIntent != null) {
                                    onLaunchCredentialIntent(fallbackIntent)
                                } else {
                                    onResult(
                                        BiometricAuthResult.Failed(
                                            errString?.toString() ?: "Biometric error ($errorCode)"
                                        )
                                    )
                                }
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // User can retry inside the system biometric sheet
                        }
                    }
                )
                return
            } catch (e: Exception) {
                val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                @Suppress("DEPRECATION")
                val fallbackIntent = keyguardManager?.createConfirmDeviceCredentialIntent(title, subtitle)
                if (fallbackIntent != null) {
                    onLaunchCredentialIntent(fallbackIntent)
                    return
                }
                onResult(BiometricAuthResult.Failed(e.message ?: "Biometric authentication failed."))
                return
            }
        }

        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        @Suppress("DEPRECATION")
        val fallbackIntent = keyguardManager?.createConfirmDeviceCredentialIntent(title, subtitle)
        if (fallbackIntent != null) {
            onLaunchCredentialIntent(fallbackIntent)
        } else {
            onResult(BiometricAuthResult.Failed("Biometric prompt requires Android 9+ or secure device lock."))
        }
    }

    suspend fun authenticate(
        activity: Activity,
        title: String = "Confirm High-Risk Action",
        subtitle: String = "Verify your identity to execute this sensitive command",
        description: String = "JARVIS Safety Gate requires biometric or device lock verification."
    ): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        if (!isAuthenticationAvailable(activity)) {
            continuation.resume(
                Result.failure(
                    IllegalStateException("No biometric sensor or device screen lock is configured on this device.")
                )
            )
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation {
                    if (!cancellationSignal.isCanceled) {
                        cancellationSignal.cancel()
                    }
                }

                val builder = BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription(description)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                } else {
                    builder.setNegativeButton(
                        "Cancel",
                        activity.mainExecutor
                    ) { _, _ ->
                        if (continuation.isActive) {
                            continuation.resume(Result.success(false))
                        }
                    }
                }

                val prompt = builder.build()
                prompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            if (continuation.isActive) {
                                continuation.resume(Result.success(true))
                            }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    Result.failure(
                                        SecurityException(errString?.toString() ?: "Biometric error ($errorCode)")
                                    )
                                )
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // User can retry within the system prompt until error/cancellation
                        }
                    }
                )
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resume(Result.failure(e))
                }
            }
        } else {
            val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isDeviceSecure == true) {
                continuation.resume(Result.success(true))
            } else {
                continuation.resume(
                    Result.failure(IllegalStateException("Biometric prompt requires Android 9+ or secure lockscreen."))
                )
            }
        }
    }
}
