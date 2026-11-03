package com.jarvisai.assistant.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

data class CommunicationActionResult(
    val executed: Boolean,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String = "",
    val message: String,
    val spokenHinglishSummary: String
)

/**
 * Handles communication intents (WhatsApp, Telegram, Gmail compose, SMS, Phone call)
 * using legitimate Android system intents with strict confirmation safeguards.
 */
class CommunicationHelper(private val context: Context) {

    companion object {
        private const val TAG = "CommunicationHelper"
        const val WHATSAPP_PKG = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PKG = "com.whatsapp.w4b"
        const val TELEGRAM_PKG = "org.telegram.messenger"
        const val GMAIL_PKG = "com.google.android.gm"
    }

    /**
     * Prepares or sends a WhatsApp message.
     * If userConfirmed is false, prompts the user for confirmation.
     */
    fun handleWhatsAppMessage(
        recipientNumberOrName: String,
        messageText: String,
        userConfirmed: Boolean
    ): CommunicationActionResult {
        val cleanMsg = messageText.trim()
        val cleanRecipient = recipientNumberOrName.trim()

        if (!userConfirmed) {
            val targetDesc = if (cleanRecipient.isNotBlank()) "to $cleanRecipient" else ""
            return CommunicationActionResult(
                executed = false,
                requiresConfirmation = true,
                confirmationPrompt = "Kya aap WhatsApp par yeh message bhejna chahte hain: \"$cleanMsg\" $targetDesc?",
                message = "WhatsApp message requires user confirmation before sending.",
                spokenHinglishSummary = "Aapne WhatsApp message bhejne ko kaha hai. Kya main ise confirm kar doon?"
            )
        }

        return try {
            val cleanPhone = cleanRecipient.replace(Regex("[^0-9]"), "")
            val intent = if (cleanPhone.length >= 10) {
                // Direct WhatsApp API URI with phone number
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(cleanMsg)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                // Send intent to choose contact or share text
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(WHATSAPP_PKG)
                    putExtra(Intent.EXTRA_TEXT, cleanMsg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                CommunicationActionResult(
                    executed = true,
                    message = "Opened WhatsApp with prepared message.",
                    spokenHinglishSummary = "Maine WhatsApp open kar diya hai message ke saath."
                )
            } else {
                // Try fallback to browser
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                CommunicationActionResult(
                    executed = true,
                    message = "WhatsApp app not found; opened WhatsApp web.",
                    spokenHinglishSummary = "WhatsApp app nahi mila, isliye browser mein open kiya hai."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp intent failed: ${e.message}")
            CommunicationActionResult(
                executed = false,
                message = "Failed to launch WhatsApp: ${e.localizedMessage}",
                spokenHinglishSummary = "WhatsApp open karne mein problem aayi."
            )
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
                    spokenHinglishSummary = "Maine Telegram open kar diya hai."
                )
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/share/url?url=${Uri.encode(cleanMsg)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                CommunicationActionResult(
                    executed = true,
                    message = "Opened Telegram link in browser.",
                    spokenHinglishSummary = "Telegram web link browser mein open kar diya hai."
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
