package com.jarvisai.assistant.cactus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CactusIntegrationRegressionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CactusNativeRuntime.loadConfig(context)
    }

    @After
    fun tearDown() {
        runCatching { File(context.filesDir, "cactus").deleteRecursively() }
        runCatching { File(context.filesDir, "models").deleteRecursively() }
    }

    @Test
    fun catalog_separatesInProcessSdkModelsFromNativeCactAndExternalModels() {
        assertEquals("cactus:qwen3-0.6b", CactusModelCatalog.DEFAULT_LLM_ID)
        assertEquals("cactus:lfm2.5-vl-450m", CactusModelCatalog.DEFAULT_VLM_ID)

        val sdkModels = CactusModelCatalog.inProcessSdkModels()
        assertTrue(sdkModels.any { it.id == "cactus:qwen3-0.6b" && it.sdkSlug == "qwen3-0.6" })
        assertTrue(sdkModels.any { it.id == "cactus:lfm2.5-vl-450m" && it.sdkSlug == "lfm2-vl-450m" && it.supportsVision })
        assertTrue(sdkModels.any { it.id == "cactus:whisper-tiny" && it.sdkSlug == "whisper-tiny" })
        assertTrue(sdkModels.any { it.id == "cactus:qwen3-embedding-0.6b" && it.sdkSlug == "qwen3-0.6-embed" })
        assertTrue(sdkModels.all { it.directZipUrl.startsWith("https://") })

        val v4Models = CactusModelCatalog.nativeCactModels()
        assertEquals(setOf("cactus:needle3", "cactus:whistle"), v4Models.map { it.id }.toSet())

        assertEquals(CactusModelRuntime.EXTERNAL_OPENAI, CactusModelCatalog.findById("cactus:mirage")?.runtime)
        assertEquals(CactusModelRuntime.EXTERNAL_OPENAI, CactusModelCatalog.findById("cactus:gemma-4-e2b")?.runtime)

        // Legacy alias resolution preserves user preferences seamlessly
        assertEquals("cactus:qwen3-0.6b", CactusModelCatalog.findById("qwen3-0.6")?.id)
        assertEquals("cactus:qwen3-0.6b", CactusModelCatalog.findById("qwen3-0.6b")?.id)
        assertEquals("cactus:lfm2.5-vl-450m", CactusModelCatalog.findById("lfm2-vl-450m")?.id)
        assertEquals("cactus:qwen3-embedding-0.6b", CactusModelCatalog.findById("qwen3-0.6-embed")?.id)
    }

    @Test
    fun parseWhistleCliTranscript_extractsCleanTextAndDropsRawJsonMetadata() {
        val rawSpeechJson = """{"text":"Barely advanced to Moby South Corceptive.","language":"en","ttft_ms":51.7,"decode_tps":191.4}"""
        assertEquals("Barely advanced to Moby South Corceptive.", CactusAiService.parseWhistleCliTranscript(rawSpeechJson))

        val silentJson = """{"text":"","language":"","ttft_ms":0.0,"decode_tps":0.0}"""
        assertNull(CactusAiService.parseWhistleCliTranscript(silentJson))

        val helpDump = "usage: ./cactus [--model needle3.cact] [--serve] [--threads N] [--fail-input-overflow]"
        assertNull(CactusAiService.parseWhistleCliTranscript(helpDump))

        assertEquals("Hello Jarvis", CactusAiService.parseWhistleCliTranscript("  Hello Jarvis  "))
    }

    @Test
    fun needleConfidenceGating_rejectsLowConfidenceHallucinationsAndAcceptsClearCalls() {
        val hallucinatedQuantum = """
            {"type":"call","success":true,"function_calls":[{"name":"launch_app","arguments":{"app_name":"Quantum Computing"}}],"suppressed_calls":[],"reasoning":"guess","confidence":0.1883,"decode_tps":174.2}
        """.trimIndent()
        val envLow = CactusAiService.parseNeedleCallEnvelope(hallucinatedQuantum)
        assertNotNull(envLow)
        assertFalse(CactusAiService.shouldAutoExecuteNeedleCall(envLow!!, enablePhoneTools = true))
        assertFalse(CactusAiService.looksLikeScreenDecisionJson(hallucinatedQuantum))

        val casualGreeting = """
            {"type":"call","success":true,"function_calls":[{"name":"get_device_telemetry","arguments":{}}],"suppressed_calls":[],"reasoning":"casual","confidence":0.3266,"decode_tps":249.4}
        """.trimIndent()
        val envGreeting = CactusAiService.parseNeedleCallEnvelope(casualGreeting)
        assertNotNull(envGreeting)
        assertFalse(CactusAiService.shouldAutoExecuteNeedleCall(envGreeting!!, enablePhoneTools = true))

        val clearFlashlight = """
            {"type":"call","success":true,"function_calls":[{"name":"set_flashlight","arguments":{"enabled":true}}],"suppressed_calls":[],"reasoning":"flashlight","confidence":0.9650,"decode_tps":105.1}
        """.trimIndent()
        val envHigh = CactusAiService.parseNeedleCallEnvelope(clearFlashlight)
        assertNotNull(envHigh)
        assertTrue(CactusAiService.shouldAutoExecuteNeedleCall(envHigh!!, enablePhoneTools = true))
        assertFalse(CactusAiService.shouldAutoExecuteNeedleCall(envHigh, enablePhoneTools = false))

        assertTrue(CactusAiService.looksLikeScreenDecisionJson("""{"action":"TAP_BADGE","badge_id":3,"thought":"click button"}"""))
    }

    @Test
    fun probeServerEndpoint_avoidsFalsePositivesAndIdentifiesNativeVsOpenAi() {
        // 1. Generic non-Cactus 404 server on localhost must NOT be marked ONLINE
        runHttpMockServer(
            responder = { _, _ -> "HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 9\r\n\r\nnot found" }
        ) { port ->
            val probe = CactusNativeRuntime.probeServerEndpoint("http://127.0.0.1:$port")
            assertFalse(probe.isOnline)
            assertEquals(CactusServerApiStyle.NONE, probe.apiStyle)
        }

        // 2. Real Cactus v4 `--serve` server: 404 on /v1/models & /health, HTTP 400 on POST /complete {}
        runHttpMockServer(
            responder = { method, path ->
                if (method == "POST" && path == "/complete") {
                    val body = """{"error":"POST /complete needs a JSON body with an input field"}"""
                    "HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body"
                } else {
                    "HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                }
            }
        ) { port ->
            val probe = CactusNativeRuntime.probeServerEndpoint("http://127.0.0.1:$port")
            assertTrue(probe.isOnline)
            assertEquals(CactusServerApiStyle.CACTUS_NATIVE, probe.apiStyle)
        }

        // 3. OpenAI-compatible server responding 200 JSON on /v1/models
        runHttpMockServer(
            responder = { _, path ->
                if (path == "/v1/models") {
                    val body = """{"object":"list","data":[{"id":"qwen3-0.6"}]}"""
                    "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n\r\n$body"
                } else {
                    "HTTP/1.1 404 Not Found\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                }
            }
        ) { port ->
            val probe = CactusNativeRuntime.probeServerEndpoint("http://127.0.0.1:$port")
            assertTrue(probe.isOnline)
            assertEquals(CactusServerApiStyle.OPENAI, probe.apiStyle)
            assertEquals("qwen3-0.6", probe.modelName)
        }
    }

    @Test
    fun runtimeReadinessAndZipFlattening_guardBareBinaryAndRobolectricSafely() {
        runBlocking {
            // Initialize must never crash on JVM / Robolectric even when arm64 libcactus.so isn't loadable
            CactusNativeRuntime.initialize(context)

            val binFile = CactusNativeRuntime.getLocalBinaryFile(context).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(20_000) { 1 })
            }
            assertNotNull(binFile)

            // Bare binary without models must NOT enable STT or fast UI screen automation
            assertFalse(CactusNativeRuntime.isOfflineSttReady(context))
            assertFalse(CactusNativeRuntime.isFastLocalInferenceReady(context))

            // Only needle3.cact still must NOT hijack generic screen navigation
            val needleSpec = CactusModelCatalog.findById("cactus:needle3")!!
            val needleDir = CactusNativeRuntime.getModelDir(context, needleSpec).apply { mkdirs() }
            File(needleDir, "needle3.cact").writeBytes(ByteArray(120_000) { 2 })
            assertFalse(CactusNativeRuntime.isFastLocalInferenceReady(context))
            assertFalse(CactusNativeRuntime.isOfflineSttReady(context))

            // Downloaded whistle.cact + binary turns offline STT ready on
            val whistleSpec = CactusModelCatalog.findById("cactus:whistle")!!
            val whistleDir = CactusNativeRuntime.getModelDir(context, whistleSpec).apply { mkdirs() }
            File(whistleDir, "whistle.cact").writeBytes(ByteArray(120_000) { 3 })
            assertTrue(CactusNativeRuntime.isOfflineSttReady(context))

            // Nested zip folder flattening moves weights/config.json to root of targetDir
            val nestedRoot = File(context.filesDir, "models/test-flatten").apply { mkdirs() }
            val inner = File(nestedRoot, "qwen3-0.6-int4").apply { mkdirs() }
            File(inner, "config.json").writeText("{}")
            File(inner, "model.bin").writeBytes(ByteArray(1024))
            CactusNativeRuntime.flattenSingleSubdirIfNeeded(nestedRoot)
            assertTrue(File(nestedRoot, "config.json").exists())
            assertTrue(File(nestedRoot, "model.bin").exists())
            assertFalse(inner.exists())
            assertTrue(nestedRoot.deleteRecursively())
        }
    }

    @Test
    fun stripThinkingMarkup_removesCompleteAndPartialQwen3ThinkBlocks() {
        assertEquals(
            "Namaste! Kaise madad karu?",
            CactusInProcessEngine.stripThinkingMarkup("<think>internal reasoning</think>\nNamaste! Kaise madad karu?")
        )
        assertEquals(
            "Final answer.",
            CactusInProcessEngine.stripThinkingMarkup("Final answer.<think>dangling")
        )
        assertEquals(
            "",
            CactusInProcessEngine.stripThinkingMarkup("<think>still thinking...")
        )
    }

    private fun runHttpMockServer(
        responder: (method: String, path: String) -> String,
        block: (port: Int) -> Unit
    ) {
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val acceptThread = thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    sock.use { client ->
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        var contentLen = 0
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLen = header.substringAfter(":").trim().toIntOrNull() ?: 0
                            }
                        }
                        if (contentLen > 0) {
                            val buf = CharArray(contentLen)
                            reader.read(buf, 0, contentLen)
                        }
                        val parts = requestLine.split(" ")
                        val method = parts.getOrNull(0).orEmpty()
                        val path = parts.getOrNull(1).orEmpty()
                        val rawResp = responder(method, path)
                        client.getOutputStream().write(rawResp.toByteArray(Charsets.UTF_8))
                        client.getOutputStream().flush()
                    }
                }
            }
        }
        try {
            block(server.localPort)
        } finally {
            runCatching { server.close() }
            acceptThread.join(1000L)
        }
    }
}
