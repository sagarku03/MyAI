package com.jarvisai.assistant.service

import android.graphics.Bitmap
import android.util.Base64
import com.jarvisai.assistant.data.network.GeminiApiService
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class OpticalSolverMode(
    val id: String,
    val title: String,
    val emoji: String,
    val subtitle: String,
    val systemPrompt: String
) {
    MATH(
        id = "MATH",
        title = "Math & Calculus",
        emoji = "🧮",
        subtitle = "Step-by-step equations, integrals, geometry & physics",
        systemPrompt = """
            You are JARVIS Optical Math & Physics Engine.
            Analyze the photographed mathematical or physics problem with 100% precision.
            Structure your response strictly as follows:
            1. **Problem Detected**: Exact transcription of the equation/problem.
            2. **Step-by-Step Solution**: Clear, numbered algebraic/calculus steps in English + natural Hinglish explanation.
            3. **Final Answer**: Boxed/highlighted final numerical or symbolic result.
            4. **Verification Check**: Quick sanity check proving the answer is correct.
        """.trimIndent()
    ),
    CODE_DEBUG(
        id = "CODE_DEBUG",
        title = "Code Debugger",
        emoji = "💻",
        subtitle = "Scan monitor stacktraces, compiler errors & algorithms",
        systemPrompt = """
            You are JARVIS Principal Systems Debugger.
            Analyze the photographed code screen, terminal stacktrace, or compiler error.
            Structure your response strictly as follows:
            1. **Detected Language & Error**: Exact exception/bug identified.
            2. **Root Cause Analysis**: Why this crash or logic bug happens (explained clearly in Hinglish + technical English).
            3. **Production Fix**: Complete, copy-ready corrected code block.
            4. **Time & Space Complexity**: Big-O analysis if an algorithm is shown.
        """.trimIndent()
    ),
    BILL_SPLIT(
        id = "BILL_SPLIT",
        title = "Receipt & Bill Split",
        emoji = "🧾",
        subtitle = "Scan restaurant bills, GST/tax & split among friends",
        systemPrompt = """
            You are JARVIS Financial & Receipt OCR Auditor.
            Scan the photographed bill, invoice, or restaurant receipt.
            Structure your response strictly as follows:
            1. **Merchant & Date**: Store/Restaurant name and bill date.
            2. **Itemized Breakdown**: List every item with its price.
            3. **Taxes & Charges**: Subtotal, CGST/SGST/VAT, Service Charge.
            4. **Grand Total**: Exact total payable.
            5. **Smart Split Table**: Calculate exact per-person share for 2, 3, 4, and 5 people (rounded to nearest rupee/cent).
        """.trimIndent()
    ),
    CIRCUIT(
        id = "CIRCUIT",
        title = "Hardware & Circuit",
        emoji = "🔌",
        subtitle = "Identify PCB components, resistors, ICs & wiring",
        systemPrompt = """
            You are JARVIS Stark Hardware & Electronics Engineer.
            Analyze the photographed electronic circuit, PCB, breadboard, or schematic.
            Structure your response strictly as follows:
            1. **Components Identified**: List ICs, microcontrollers (Arduino/ESP32), resistors (color code value), capacitors, sensors.
            2. **Circuit Function & Topology**: Explain how the circuit works in Hinglish + Engineering terms.
            3. **Pinout / Wiring Diagnostics**: Highlight any potential short-circuits, missing pull-up resistors, or wiring improvements.
        """.trimIndent()
    )
}

data class OpticalSolverResult(
    val mode: OpticalSolverMode,
    val rawMarkdownSolution: String,
    val spokenSummary: String,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

class OpticalSolverManager(
    private val geminiApiService: GeminiApiService
) {
    private val _isSolving = MutableStateFlow(false)
    val isSolving: StateFlow<Boolean> = _isSolving.asStateFlow()

    private val _selectedMode = MutableStateFlow(OpticalSolverMode.MATH)
    val selectedMode: StateFlow<OpticalSolverMode> = _selectedMode.asStateFlow()

    private val _latestResult = MutableStateFlow<OpticalSolverResult?>(null)
    val latestResult: StateFlow<OpticalSolverResult?> = _latestResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setMode(mode: OpticalSolverMode) {
        _selectedMode.value = mode
        _errorMessage.value = null
    }

    fun clearResult() {
        _latestResult.value = null
        _errorMessage.value = null
    }

    suspend fun solveImage(
        bitmap: Bitmap,
        apiKey: String,
        modelId: String = "gemini-3.8-flash",
        customUserNote: String = "",
        splitCount: Int = 4
    ): Result<OpticalSolverResult> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            val err = "Gemini API Key missing hai. Settings > AI Models & API Key me apni free key dalein."
            _errorMessage.value = err
            return@withContext Result.failure(IllegalStateException(err))
        }

        _isSolving.value = true
        _errorMessage.value = null
        val startMs = System.currentTimeMillis()

        try {
            val activeMode = _selectedMode.value
            val base64Image = encodeBitmapToBase64(bitmap)

            val extraContext = buildString {
                append(activeMode.systemPrompt)
                if (activeMode == OpticalSolverMode.BILL_SPLIT) {
                    append("\nIMPORTANT: Specifically highlight the exact split for $splitCount people.")
                }
                if (customUserNote.isNotBlank()) {
                    append("\nAdditional User Instruction: $customUserNote")
                }
                append("\nAt the very end, add a line starting with `SPOKEN_SUMMARY:` followed by a 1-2 sentence natural Hinglish summary of the final answer to be spoken aloud.")
            }

            val responseText = geminiApiService.generateVisionResponse(
                apiKey = apiKey,
                model = modelId,
                prompt = extraContext,
                imageBase64 = base64Image,
                mimeType = "image/jpeg"
            )

            val spokenSummary = extractSpokenSummary(responseText, activeMode)
            val cleanedSolution = responseText
                .replace(Regex("SPOKEN_SUMMARY:.*", RegexOption.IGNORE_CASE), "")
                .trim()

            val result = OpticalSolverResult(
                mode = activeMode,
                rawMarkdownSolution = cleanedSolution.ifBlank { responseText },
                spokenSummary = spokenSummary,
                latencyMs = System.currentTimeMillis() - startMs
            )
            _latestResult.value = result
            Result.success(result)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Optical analysis failed."
            _errorMessage.value = msg
            Result.failure(e)
        } finally {
            _isSolving.value = false
        }
    }

    private fun extractSpokenSummary(fullText: String, mode: OpticalSolverMode): String {
        val match = Regex("SPOKEN_SUMMARY:\\s*(.+)", RegexOption.IGNORE_CASE).find(fullText)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        return when (mode) {
            OpticalSolverMode.MATH -> "Sir, maine math equation step-by-step solve kar di hai. Final answer screen par hai."
            OpticalSolverMode.CODE_DEBUG -> "Sir, code ka bug mil gaya hai aur maine clean fix ready kar diya hai."
            OpticalSolverMode.BILL_SPLIT -> "Sir, bill scan ho gaya hai aur per-person split calculate kar diya gaya hai."
            OpticalSolverMode.CIRCUIT -> "Sir, circuit components aur schematic ka breakdown ready hai."
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1280
        val ratio = minOf(maxDim.toFloat() / bitmap.width.coerceAtLeast(1), maxDim.toFloat() / bitmap.height.coerceAtLeast(1), 1f)
        val scaled = if (ratio < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
