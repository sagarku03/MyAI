package com.jarvisai.assistant.util

import android.system.Os
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Centralized India Standard Time (IST / Asia/Kolkata, UTC+05:30) utility for Jarvis AI.
 * Guarantees consistent Indian timezone across logs, UI timestamps, subprocess environments,
 * and AI runtime prompts regardless of device VPN or OS locale quirks.
 */
object IndiaTimeUtils {

    const val IST_ZONE_ID = "Asia/Kolkata"
    val IST_TIME_ZONE: TimeZone = TimeZone.getTimeZone(IST_ZONE_ID)

    fun enforceProcessIndiaTimeZone() {
        runCatching {
            Os.setenv("TZ", IST_ZONE_ID, true)
        }
        runCatching {
            TimeZone.setDefault(IST_TIME_ZONE)
        }
    }

    fun formatter(pattern: String, locale: Locale = Locale.US): SimpleDateFormat {
        return SimpleDateFormat(pattern, locale).apply {
            timeZone = IST_TIME_ZONE
        }
    }

    fun format(pattern: String, timestampMs: Long = System.currentTimeMillis(), locale: Locale = Locale.US): String {
        return formatter(pattern, locale).format(Date(timestampMs))
    }

    fun format(pattern: String, date: Date, locale: Locale = Locale.US): String {
        return formatter(pattern, locale).format(date)
    }

    fun calendarNow(): Calendar = Calendar.getInstance(IST_TIME_ZONE)

    fun calendarAt(timestampMs: Long): Calendar = Calendar.getInstance(IST_TIME_ZONE).apply {
        timeInMillis = timestampMs
    }

    fun nowLogTimestamp(): String = format("yyyy/MM/dd HH:mm:ss")

    fun nowLogMillis(): String = format("HH:mm:ss.SSS")

    fun nowBannerTimestamp(): String = format("yyyy-MM-dd HH:mm:ss 'IST (Asia/Kolkata)'")

    fun nowFileStamp(): String = format("yyyy-MM-dd_HH-mm-ss")

    fun nowTime12h(): String = format("h:mm a")

    fun nowTime24h(): String = format("HH:mm:ss")

    /**
     * Authoritative real-time clock block injected into AI system instructions & prompts
     * so Jarvis AI and Antigravity ALWAYS report accurate India Standard Time (IST).
     */
    fun buildAiRuntimeClockDirective(): String {
        val now = Date()
        val fullDate = format("EEEE, dd MMMM yyyy", now)
        val time12 = format("h:mm:ss a", now)
        val time24 = format("HH:mm:ss", now)
        return "[INDIA REAL-TIME CLOCK ($IST_ZONE_ID / IST UTC+05:30)] " +
            "Date: $fullDate | Time: $time12 ($time24 IST). " +
            "CRITICAL TIMEZONE RULE: User is in India (IST, Asia/Kolkata, UTC+05:30). " +
            "Whenever asked for the current time, date, or day (e.g. 'abhi time kya hua hai'), " +
            "ALWAYS report THIS exact India time/date in IST ($time12 IST) — NEVER report UTC or any foreign VPN country time."
    }
}
