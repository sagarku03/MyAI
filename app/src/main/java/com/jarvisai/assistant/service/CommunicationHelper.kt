package com.jarvisai.assistant.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class ResolvedWhatsAppContact(
    val displayName: String,
    val phoneNumber: String
)

data class CommunicationActionResult(
    val executed: Boolean,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String = "",
    val message: String,
    val spokenHinglishSummary: String,
    val resolvedRecipientName: String = "",
    val resolvedPhoneNumber: String = "",
    val preparedMessageText: String = "",
    val messageSentVerified: Boolean = false,
    val capabilityStatus: CapabilityResultStatus = if (executed) CapabilityResultStatus.REQUIRES_USER_ACTION else CapabilityResultStatus.FAILED
)

/**
 * Handles communication intents (WhatsApp, Telegram, Gmail compose, SMS, Phone call)
 * using legitimate Android system intents, Contacts resolution, and strict confirmation safeguards (Section 10).
 */
class CommunicationHelper(private val context: Context) {

    companion object {
        private const val TAG = "CommunicationHelper"
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"
        const val TELEGRAM_PKG = "org.telegram.messenger"
        const val GMAIL_PKG = "com.google.android.gm"

        @Volatile
        var testContactResolverOverride: ((String) -> List<ResolvedWhatsAppContact>)? = null
    }

    /**
     * Resolves a contact name from Android Contacts (`ContactsContract.CommonDataKinds.Phone`)
     * when `READ_CONTACTS` permission is granted (Section 10.3).
     */
    fun resolveContactsByName(contactQuery: String): List<ResolvedWhatsAppContact> {
        val cleanQuery = contactQuery.trim()
        if (cleanQuery.isBlank()) return emptyList()

        testContactResolverOverride?.let { return it(cleanQuery) }

        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPerm) return emptyList()

        val matches = mutableListOf<ResolvedWhatsAppContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$cleanQuery%")

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && matches.size < 10) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                    val normalizedPhone = normalizePhoneNumberForWhatsApp(rawNumber)
                    if (name.isNotBlank() && normalizedPhone.length >= 10) {
                        if (matches.none { it.phoneNumber == normalizedPhone }) {
                            matches.add(ResolvedWhatsAppContact(name, normalizedPhone))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contact resolution error for '$cleanQuery': ${e.localizedMessage}")
        }
        return matches
    }

    internal fun normalizePhoneNumberForWhatsApp(rawPhone: String): String {
        val digits = rawPhone.replace(Regex("[^0-9]"), "")
        return when {
            digits.length == 10 -> "91$digits" // Default India country prefix for 10-digit mobile numbers
            digits.length == 11 && digits.startsWith("0") -> "91${digits.substring(1)}"
            else -> digits
        }
    }

    /**
     * Prepares or sends a WhatsApp message (Section 10).
     * - Resolves contact name to phone number when available.
     * - Handles multiple contact matches with a clarification prompt.
     * - Never claims the message was sent if it was only prepared in WhatsApp composer.
     */
    fun handleWhatsAppMessage(
        recipientNumberOrName: String,
        messageText: String,
        userConfirmed: Boolean,
        autoClickSendIfAccessible: Boolean = false
    ): CommunicationActionResult {
        val cleanMsg = messageText.trim()
        val cleanRecipient = recipientNumberOrName.trim()

        // If neither message nor recipient is provided, simply open WhatsApp
        if (cleanMsg.isBlank() && cleanRecipient.isBlank()) {
            return openWhatsAppHome()
        }

        // If recipient is specified but message is empty (e.g., "Jarvis, WhatsApp par Rahul ko message bhejo"),
        // ask for the message body or open chat directly if confirmed
        if (cleanMsg.isBlank() && cleanRecipient.isNotBlank() && !userConfirmed) {
            return CommunicationActionResult(
                executed = false,
                requiresConfirmation = true,
                confirmationPrompt = "Aap WhatsApp par $cleanRecipient ko kya message bhejna chahte hain?",
                message = "Message content is required for WhatsApp message to $cleanRecipient.",
                spokenHinglishSummary = "$cleanRecipient ko WhatsApp par kya message bhejna hai, woh batayein.",
                resolvedRecipientName = cleanRecipient,
                capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION
            )
        }

        // Resolve recipient phone number (either direct digits or via ContactsContract)
        val directDigits = cleanRecipient.replace(Regex("[^0-9]"), "")
        var resolvedName = cleanRecipient
        var resolvedPhone = ""

        if (directDigits.length >= 10) {
            resolvedPhone = normalizePhoneNumberForWhatsApp(directDigits)
        } else if (cleanRecipient.isNotBlank()) {
            val contactMatches = resolveContactsByName(cleanRecipient)
            if (contactMatches.size == 1) {
                resolvedName = contactMatches.first().displayName
                resolvedPhone = contactMatches.first().phoneNumber
            } else if (contactMatches.size > 1 && !userConfirmed) {
                val namesList = contactMatches.take(3).joinToString(", ") { "${it.displayName} (${it.phoneNumber.takeLast(4)})" }
                return CommunicationActionResult(
                    executed = false,
                    requiresConfirmation = true,
                    confirmationPrompt = "Contacts mein '$cleanRecipient' naam ke multiple numbers mile: $namesList. Aap kisko message bhejna chahte hain?",
                    message = "Multiple contacts matched '$cleanRecipient': $namesList",
                    spokenHinglishSummary = "Contacts mein $cleanRecipient naam ke ek se zyada number mile hain ($namesList). Kripya confirm karein.",
                    resolvedRecipientName = cleanRecipient,
                    preparedMessageText = cleanMsg,
                    capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION
                )
            } else if (contactMatches.isNotEmpty()) {
                resolvedName = contactMatches.first().displayName
                resolvedPhone = contactMatches.first().phoneNumber
            }
        }

        if (!userConfirmed) {
            val targetDesc = if (resolvedName.isNotBlank()) "to $resolvedName" else ""
            val report = ActionVerificationEngine.verifyWhatsAppAction(
                recipient = resolvedName.ifBlank { "contact" },
                messageText = cleanMsg,
                intentLaunched = false,
                autoClickedSendViaAccessibility = false,
                requiresConfirmation = true
            )
            return CommunicationActionResult(
                executed = false,
                requiresConfirmation = true,
                confirmationPrompt = "Kya aap WhatsApp par yeh message bhejna chahte hain: \"$cleanMsg\" $targetDesc?",
                message = report.userMessage,
                spokenHinglishSummary = "Aapne WhatsApp par ${resolvedName.ifBlank { "contact" }} ko \"$cleanMsg\" bhejne ko kaha hai. Kripya confirm karein — kya main ise open/send kar doon?",
                resolvedRecipientName = resolvedName,
                resolvedPhoneNumber = resolvedPhone,
                preparedMessageText = cleanMsg,
                capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION
            )
        }

        return try {
            val pm = context.packageManager
            val installedWhatsAppPkg = when {
                isPackageAvailable(pm, WHATSAPP_PKG) -> WHATSAPP_PKG
                isPackageAvailable(pm, WHATSAPP_BUSINESS_PKG) -> WHATSAPP_BUSINESS_PKG
                else -> null
            }

            val intent = if (resolvedPhone.length >= 10) {
                // Direct WhatsApp API URI with phone number + encoded text
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$resolvedPhone&text=${Uri.encode(cleanMsg)}")
                    if (installedWhatsAppPkg != null) {
                        setPackage(installedWhatsAppPkg)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                // Send intent to choose contact or share text in WhatsApp
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(installedWhatsAppPkg ?: WHATSAPP_PKG)
                    putExtra(Intent.EXTRA_TEXT, cleanMsg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            val canResolve = intent.resolveActivity(pm) != null || installedWhatsAppPkg != null
            if (canResolve) {
                context.startActivity(intent)
                ConversationContextManager.setActiveForegroundApp(
                    appName = "WhatsApp",
                    packageName = installedWhatsAppPkg ?: WHATSAPP_PKG,
                    verified = true,
                    taskDescription = "WhatsApp message to ${resolvedName.ifBlank { "contact" }}"
                )

                // Automatically click Send button in WhatsApp composer when userConfirmed or autoClickSendIfAccessible is true
                var sentViaAccessibility = false
                val shouldAutoSend = userConfirmed || autoClickSendIfAccessible
                if (shouldAutoSend && JarvisAccessibilityService.isAccessibilityEnabled(context) && cleanMsg.isNotBlank()) {
                    try {
                        kotlinx.coroutines.runBlocking {
                            kotlinx.coroutines.delay(1200L)
                        }
                    } catch (_: Exception) {}

                    val clickRes = JarvisAccessibilityService.clickElement(
                        context = context,
                        targetText = "Send",
                        contentDescription = "Send",
                        viewId = "com.whatsapp:id/send"
                    )
                    sentViaAccessibility = clickRes.executed
                    if (!sentViaAccessibility) {
                        try {
                            kotlinx.coroutines.runBlocking {
                                kotlinx.coroutines.delay(800L)
                            }
                        } catch (_: Exception) {}
                        val retryRes = JarvisAccessibilityService.clickElement(
                            context = context,
                            targetText = "Send",
                            contentDescription = "Send",
                            viewId = "com.whatsapp:id/send"
                        )
                        sentViaAccessibility = retryRes.executed
                    }
                }

                val report = ActionVerificationEngine.verifyWhatsAppAction(
                    recipient = resolvedName.ifBlank { "selected chat" },
                    messageText = cleanMsg,
                    intentLaunched = true,
                    autoClickedSendViaAccessibility = sentViaAccessibility,
                    requiresConfirmation = false
                )

                CommunicationActionResult(
                    executed = true,
                    message = report.userMessage,
                    spokenHinglishSummary = report.hinglishMessage,
                    resolvedRecipientName = resolvedName,
                    resolvedPhoneNumber = resolvedPhone,
                    preparedMessageText = cleanMsg,
                    messageSentVerified = sentViaAccessibility,
                    capabilityStatus = report.capabilityStatus
                )
            } else {
                // Fallback to https://api.whatsapp.com/send or web.whatsapp.com in browser
                val webUri = if (resolvedPhone.length >= 10) {
                    Uri.parse("https://api.whatsapp.com/send?phone=$resolvedPhone&text=${Uri.encode(cleanMsg)}")
                } else {
                    Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(cleanMsg)}")
                }
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                ConversationContextManager.setActiveForegroundApp("WhatsApp", WHATSAPP_PKG, verified = false)
                CommunicationActionResult(
                    executed = true,
                    message = "Opened WhatsApp deep link with prepared message \"$cleanMsg\".",
                    spokenHinglishSummary = "Maine WhatsApp open kar ke message \"$cleanMsg\" prepare kar diya hai.",
                    resolvedRecipientName = resolvedName,
                    resolvedPhoneNumber = resolvedPhone,
                    preparedMessageText = cleanMsg,
                    messageSentVerified = false,
                    capabilityStatus = CapabilityResultStatus.REQUIRES_USER_ACTION
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp intent failed: ${e.message}")
            CommunicationActionResult(
                executed = false,
                message = "Failed to launch WhatsApp: ${e.localizedMessage}",
                spokenHinglishSummary = "WhatsApp open karne mein problem aayi.",
                resolvedRecipientName = resolvedName,
                preparedMessageText = cleanMsg,
                capabilityStatus = CapabilityResultStatus.FAILED
            )
        }
    }

    private fun openWhatsAppHome(): CommunicationActionResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(WHATSAPP_PKG)
                ?: pm.getLaunchIntentForPackage(WHATSAPP_BUSINESS_PKG)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ConversationContextManager.setActiveForegroundApp("WhatsApp", WHATSAPP_PKG, verified = true)
                CommunicationActionResult(
                    executed = true,
                    message = "Opened WhatsApp.",
                    spokenHinglishSummary = "WhatsApp open ho gaya hai.",
                    capabilityStatus = CapabilityResultStatus.SUCCESS
                )
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                CommunicationActionResult(
                    executed = true,
                    message = "Opened WhatsApp Web.",
                    spokenHinglishSummary = "WhatsApp browser mein open kar diya hai.",
                    capabilityStatus = CapabilityResultStatus.PARTIAL_SUCCESS
                )
            }
        } catch (e: Exception) {
            CommunicationActionResult(
                executed = false,
                message = "Failed to open WhatsApp: ${e.localizedMessage}",
                spokenHinglishSummary = "WhatsApp open nahi ho paya.",
                capabilityStatus = CapabilityResultStatus.FAILED
            )
        }
    }

    private fun isPackageAvailable(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            pm.getLaunchIntentForPackage(pkg) != null
        }
    }

    /**
     * Prepares or sends a Telegram message.
     */
    fun handleTelegramMessage(
        messageText: String,
        userConfirmed: Boolean
    ): CommunicationActionResult {
        val cleanMsg = messageText.trim()

        if (!userConfirmed) {
            return CommunicationActionResult(
                executed = false,
                requiresConfirmation = true,
                confirmationPrompt = "Kya aap Telegram par yeh message bhejna chahte hain: \"$cleanMsg\"?",
                message = "Telegram message requires confirmation.",
                spokenHinglishSummary = "Telegram par message bhejne se pehle aapka confirmation zaroori hai."
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(TELEGRAM_PKG)
                putExtra(Intent.EXTRA_TEXT, cleanMsg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                CommunicationActionResult(
                    executed = true,
                    message = "Opened Telegram with prepared message.",
                    spokenHinglishSummary = "Maine Telegram open kar diya hai.",
                    preparedMessageText = cleanMsg
                )
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/share/url?url=${Uri.encode(cleanMsg)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                CommunicationActionResult(
                    executed = true,
                    message = "Opened Telegram link in browser.",
                    spokenHinglishSummary = "Telegram web link browser mein open kar diya hai.",
                    preparedMessageText = cleanMsg
                )
            }
        } catch (e: Exception) {
            CommunicationActionResult(
                executed = false,
                message = "Failed to open Telegram: ${e.localizedMessage}",
                spokenHinglishSummary = "Telegram open karne mein problem aayi."
            )
        }
    }

    /**
     * Composes an email in Gmail or default mail client.
     */
    fun handleEmailCompose(
        recipientEmail: String,
        subject: String,
        body: String,
        userConfirmed: Boolean
    ): CommunicationActionResult {
        val cleanTo = recipientEmail.trim()
        val cleanSubject = subject.trim()
        val cleanBody = body.trim()

        if (!userConfirmed) {
            return CommunicationActionResult(
                executed = false,
                requiresConfirmation = true,
                confirmationPrompt = "Kya aap yeh email bhejna chahte hain to '$cleanTo'? Subject: '$cleanSubject'.",
                message = "Email compose requires confirmation before opening or sending.",
                spokenHinglishSummary = "Email compose karne se pehle kya aap confirm karte hain?"
            )
        }

        return try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${Uri.encode(cleanTo)}")
                if (cleanSubject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, cleanSubject)
                if (cleanBody.isNotBlank()) putExtra(Intent.EXTRA_TEXT, cleanBody)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(emailIntent)
            CommunicationActionResult(
                executed = true,
                message = "Opened Email client with recipient '$cleanTo' and subject '$cleanSubject'.",
                spokenHinglishSummary = "Maine email compose screen open kar di hai."
            )
        } catch (e: Exception) {
            CommunicationActionResult(
                executed = false,
                message = "Could not open email application: ${e.localizedMessage}",
                spokenHinglishSummary = "Email app open karne mein error aaya."
            )
        }
    }

    /**
     * Prepares an SMS message.
     */
    fun handleSmsCompose(
        phoneNumber: String,
        messageBody: String,
        userConfirmed: Boolean
    ): CommunicationActionResult {
        val cleanPhone = phoneNumber.trim().replace(Regex("[^0-9+]"), "")
        val cleanMsg = messageBody.trim()

        if (!userConfirmed) {
            return CommunicationActionResult(
                executed = false,
                requiresConfirmation = true,
                confirmationPrompt = "Kya aap number '$cleanPhone' par yeh SMS bhejna chahte hain: \"$cleanMsg\"?",
                message = "SMS compose requires confirmation.",
                spokenHinglishSummary = "SMS bhejne ke liye confirmation chahiye."
            )
        }

        return try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(cleanPhone)}")
                putExtra("sms_body", cleanMsg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            CommunicationActionResult(
                executed = true,
                message = "Opened SMS application with number '$cleanPhone'.",
                spokenHinglishSummary = "Maine SMS app open kar diya hai."
            )
        } catch (e: Exception) {
            CommunicationActionResult(
                executed = false,
                message = "Failed to launch SMS app: ${e.localizedMessage}",
                spokenHinglishSummary = "SMS app open karne mein problem aayi."
            )
        }
    }

    /**
     * Prepares a phone dialer call.
     */
    fun handlePhoneDial(phoneNumber: String): CommunicationActionResult {
        val cleanPhone = phoneNumber.trim().replace(Regex("[^0-9+*#]"), "")
        if (cleanPhone.length < 3) {
            return CommunicationActionResult(
                executed = false,
                message = "Invalid phone number provided.",
                spokenHinglishSummary = "Kripya valid phone number provide karein."
            )
        }

        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(cleanPhone)}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            CommunicationActionResult(
                executed = true,
                message = "Opened Phone Dialer for number $cleanPhone.",
                spokenHinglishSummary = "Maine dialer mein number $cleanPhone dial kar diya hai."
            )
        } catch (e: Exception) {
            CommunicationActionResult(
                executed = false,
                message = "Could not open dialer: ${e.localizedMessage}",
                spokenHinglishSummary = "Phone dialer open nahi ho paya."
            )
        }
    }
}
