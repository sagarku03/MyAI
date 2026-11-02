package com.example

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.JarvisDatabase
import com.jarvisai.assistant.data.network.GeminiApiService
import com.jarvisai.assistant.data.repository.JarvisRepository
import com.jarvisai.assistant.model.MemoryCategory
import com.jarvisai.assistant.model.SupportedGeminiModels
import com.jarvisai.assistant.model.SupportedLanguage
import com.jarvisai.assistant.model.VoiceGender
import com.jarvisai.assistant.security.SecureApiKeyManager
import com.jarvisai.assistant.service.AccessibilityControllerBridge
import com.jarvisai.assistant.service.AccessibilityOperationResult
import com.jarvisai.assistant.service.GeminiLiveVoiceManager
import com.jarvisai.assistant.service.JarvisAccessibilityService
import com.jarvisai.assistant.service.LiveAgentToolExecutor
import com.jarvisai.assistant.service.PhoneActionManager
import com.jarvisai.assistant.service.PhoneControlsManager
import com.jarvisai.assistant.service.ScreenContextSnapshot
import com.jarvisai.assistant.service.ScreenElementSnapshot
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: JarvisDatabase
    private lateinit var repository: JarvisRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JarvisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JarvisRepository(db.jarvisDao())
        JarvisAccessibilityService.testOverrideEnabledState = null
        JarvisAccessibilityService.testOverrideRootNodeProvider = null
        JarvisAccessibilityService.testGlobalActionHandler = null
    }

    @After
    fun tearDown() {
        JarvisAccessibilityService.testOverrideEnabledState = null
        JarvisAccessibilityService.testOverrideRootNodeProvider = null
        JarvisAccessibilityService.testGlobalActionHandler = null
        db.close()
    }

    @Test
    fun `read app_name from context matches JARVIS AI`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS AI", appName)
    }

    @Test
    fun `chat session and message persistence works in Room`() = runBlocking {
        val sessionId = repository.createNewChatSession("Propulsion Diagnostics", "HINGLISH")
        repository.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = "Check thruster calibration"
            )
        )
        repository.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = "model",
                content = "All thrusters operating at 99.4% efficiency."
            )
        )

        val messages = repository.getMessagesForSessionOnce(sessionId)
        assertEquals(2, messages.size)
        assertEquals("Check thruster calibration", messages[0].content)
        assertEquals("All thrusters operating at 99.4% efficiency.", messages[1].content)

        repository.deleteChatSession(sessionId)
        val afterDelete = repository.getMessagesForSessionOnce(sessionId)
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun `automatic memory detection saves Sagar and retrieves it for What is my name`() = runBlocking {
        val saveNameResult = repository.detectAndSaveAutomaticMemories("My name is Sagar. Remember this.")
        assertTrue(saveNameResult.saved)
        assertTrue(saveNameResult.confirmationMessage.contains("Memory saved successfully"))

        val saveLangResult = repository.detectAndSaveAutomaticMemories("My favorite language is Hindi. Remember it.")
        assertTrue(saveLangResult.saved)

        val retrievedForNameQuestion = repository.retrieveRelevantMemories("What is my name?")
        assertTrue(retrievedForNameQuestion.any { it.title == "Name" && it.content.contains("Sagar") })
        assertTrue(retrievedForNameQuestion.any { it.title.contains("Favorite", ignoreCase = true) && it.content.contains("Hindi") })
    }

    @Test
    fun `memory center saves and retrieves relevant user approved memories`() = runBlocking {
        repository.saveApprovedMemory(
            title = "Preferred Stack",
            content = "Uses Kotlin, Jetpack Compose, and MVVM for Android development",
            category = MemoryCategory.PROJECTS,
            tags = "android,kotlin",
            isPinned = true
        )
        repository.saveApprovedMemory(
            title = "Coffee Order",
            content = "Likes iced americano without sugar",
            category = MemoryCategory.PREFERENCES,
            tags = "food"
        )

        val relevant = repository.retrieveRelevantMemories("Help me write a Kotlin Compose screen")
        assertTrue(relevant.isNotEmpty())
        assertEquals("Preferred Stack", relevant.first().title)

        repository.clearAllMemories()
        val afterClear = repository.retrieveRelevantMemories("Kotlin")
        assertTrue(afterClear.isEmpty())
    }

    @Test
    fun `phone action manager parses Hinglish YouTube search and Male Female voices exist`() {
        val phoneControls = PhoneControlsManager(context)
        val actionManager = PhoneActionManager(context, phoneControls)
        val query = actionManager.extractYouTubeSearchQuery(
            "YouTube par search karo latest technology videos"
        )
        assertEquals("latest technology videos", query)

        val maleVoices = SupportedGeminiModels.voiceOptions.filter { it.gender == VoiceGender.MALE }
        val femaleVoices = SupportedGeminiModels.voiceOptions.filter { it.gender == VoiceGender.FEMALE }
        assertTrue(maleVoices.isNotEmpty())
        assertTrue(femaleVoices.isNotEmpty())
    }

    @Test
    fun `secure api key manager saves masks and deletes key`() {
        val keyManager = SecureApiKeyManager(context)
        keyManager.deleteApiKey()
        assertFalse(keyManager.hasValidApiKey())

        val saved = keyManager.saveApiKey("AIzaSyTestKey1234567890")
        assertTrue(saved)
        assertTrue(keyManager.hasValidApiKey())
        assertEquals("AIzaSyTestKey1234567890", keyManager.getApiKey())
        assertTrue(keyManager.getMaskedApiKey().startsWith("AIza"))
        assertTrue(keyManager.getMaskedApiKey().endsWith("7890"))

        keyManager.deleteApiKey()
        assertFalse(keyManager.hasValidApiKey())
    }

    // =========================================================================
    // LIVE AGENT ANDROID TOOL CALLING & ACCESSIBILITY TESTS (SCENARIOS 1 - 9)
    // =========================================================================

    private fun registerFakeLauncherPackage(packageName: String, activityName: String, appLabel: String) {
        val shadowPm = shadowOf(context.packageManager)
        val component = ComponentName(packageName, activityName)
        shadowPm.addOrUpdateActivity(
            android.content.pm.ActivityInfo().apply {
                this.packageName = packageName
                this.name = activityName
                this.applicationInfo = android.content.pm.ApplicationInfo().apply {
                    this.packageName = packageName
                    this.name = appLabel
                    this.nonLocalizedLabel = appLabel
                }
                this.nonLocalizedLabel = appLabel
            }
        )
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        shadowPm.addIntentFilterForActivity(component, filter)
    }

    @Test
    fun `TEST 1 - Open YouTube launches YouTube intent and confirms execution`() = runBlocking {
        registerFakeLauncherPackage(
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.youtube.HomeActivity",
            appLabel = "YouTube"
        )
        val executor = LiveAgentToolExecutor(context)
        val outcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_LAUNCH_APPLICATION,
            callId = "call_yt_1",
            args = JSONObject().put("app_name", "YouTube")
        )

        assertTrue(outcome.executed)
        assertEquals("SUCCESS", outcome.status)
        assertEquals("com.google.android.youtube", outcome.responseJson.optString("package_name"))

        val startedIntent = shadowOf(context as Application).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals("com.google.android.youtube", startedIntent.component?.packageName ?: startedIntent.`package`)
        assertTrue((startedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `TEST 2 - Open Chrome launches Chrome intent and confirms execution`() = runBlocking {
        registerFakeLauncherPackage(
            packageName = "com.android.chrome",
            activityName = "com.google.android.apps.chrome.Main",
            appLabel = "Chrome"
        )
        val executor = LiveAgentToolExecutor(context)
        val outcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_LAUNCH_APPLICATION,
            callId = "call_chrome_2",
            args = JSONObject().put("app_name", "Chrome")
        )

        assertTrue(outcome.executed)
        assertEquals("SUCCESS", outcome.status)
        assertEquals("com.android.chrome", outcome.responseJson.optString("package_name"))

        val startedIntent = shadowOf(context as Application).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals("com.android.chrome", startedIntent.component?.packageName ?: startedIntent.`package`)
    }

    @Test
    fun `TEST 3 - Click visible UI element finds Search button and performs click`() = runBlocking {
        var clickedTarget: String? = null
        val fakeBridge = object : AccessibilityControllerBridge {
            override fun isAccessibilityEnabled(context: Context): Boolean = true
            override fun getScreenContext(context: Context): ScreenContextSnapshot {
                return ScreenContextSnapshot(
                    serviceEnabled = true,
                    activePackageName = "com.google.android.youtube",
                    windowTitle = "YouTube",
                    elements = listOf(
                        ScreenElementSnapshot(
                            index = 0,
                            text = "Search",
                            contentDescription = "Search YouTube",
                            viewIdResourceName = "com.google.android.youtube:id/menu_item_search",
                            className = "ImageView",
                            packageName = "com.google.android.youtube",
                            isClickable = true,
                            isEditable = false,
                            isScrollable = false,
                            isFocusable = true,
                            boundsInScreen = "[920,80][1040,200]"
                        )
                    )
                )
            }

            override fun clickElement(
                context: Context,
                targetText: String?,
                contentDescription: String?,
                viewId: String?
            ): AccessibilityOperationResult {
                clickedTarget = targetText ?: contentDescription
                return AccessibilityOperationResult(
                    executed = true,
                    actionName = "click_accessible_element",
                    matchedElementLabel = "Search",
                    activePackageName = "com.google.android.youtube",
                    errorCode = null,
                    message = "Successfully clicked 'Search' on screen.",
                    spokenHinglishSummary = "Maine screen par 'Search' click kar diya hai."
                )
            }

            override fun scrollScreen(context: Context, direction: String): AccessibilityOperationResult =
                throw UnsupportedOperationException()
            override fun navigateBack(context: Context): AccessibilityOperationResult =
                throw UnsupportedOperationException()
            override fun navigateHome(context: Context): AccessibilityOperationResult =
                throw UnsupportedOperationException()
        }

        val executor = LiveAgentToolExecutor(
            context = context,
            accessibilityBridge = fakeBridge
        )
        val outcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_CLICK_ACCESSIBLE_ELEMENT,
            callId = "call_click_3",
            args = JSONObject().put("target_text", "Search")
        )

        assertTrue(outcome.executed)
        assertEquals("SUCCESS", outcome.status)
        assertEquals("Search", clickedTarget)
        assertEquals("Search", outcome.responseJson.optString("matched_element"))
    }

    @Test
    fun `TEST 4 - Scroll screen performs forward scroll action and confirms`() = runBlocking {
        var scrolledDirection: String? = null
        val fakeBridge = object : AccessibilityControllerBridge {
            override fun isAccessibilityEnabled(context: Context): Boolean = true
            override fun getScreenContext(context: Context): ScreenContextSnapshot =
                throw UnsupportedOperationException()
            override fun clickElement(context: Context, targetText: String?, contentDescription: String?, viewId: String?): AccessibilityOperationResult =
                throw UnsupportedOperationException()

            override fun scrollScreen(context: Context, direction: String): AccessibilityOperationResult {
                scrolledDirection = direction
                return AccessibilityOperationResult(
                    executed = true,
                    actionName = "scroll_screen",
                    matchedElementLabel = "RecyclerView",
                    activePackageName = "com.google.android.youtube",
                    errorCode = null,
                    message = "Successfully scrolled screen $direction.",
                    spokenHinglishSummary = "Maine screen ko $direction scroll kar diya hai."
                )
            }

            override fun navigateBack(context: Context): AccessibilityOperationResult =
                throw UnsupportedOperationException()
            override fun navigateHome(context: Context): AccessibilityOperationResult =
                throw UnsupportedOperationException()
        }

        val executor = LiveAgentToolExecutor(context = context, accessibilityBridge = fakeBridge)
        val outcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_SCROLL_SCREEN,
            callId = "call_scroll_4",
            args = JSONObject().put("direction", "down")
        )

        assertTrue(outcome.executed)
        assertEquals("SUCCESS", outcome.status)
        assertEquals("down", scrolledDirection)
    }

    @Test
    fun `TEST 5 - Navigate back and Home perform global accessibility actions`() = runBlocking {
        val globalActionsExecuted = mutableListOf<Int>()
        JarvisAccessibilityService.testOverrideEnabledState = true
        JarvisAccessibilityService.testGlobalActionHandler = { actionId ->
            globalActionsExecuted.add(actionId)
            true
        }

        val executor = LiveAgentToolExecutor(context = context)
        val backOutcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_NAVIGATE_BACK,
            callId = "call_back_5",
            args = JSONObject()
        )
        assertTrue(backOutcome.executed)
        assertEquals("SUCCESS", backOutcome.status)
        assertTrue(globalActionsExecuted.contains(AccessibilityService.GLOBAL_ACTION_BACK))

        val homeOutcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_NAVIGATE_HOME,
            callId = "call_home_5",
            args = JSONObject()
        )
        assertTrue(homeOutcome.executed)
        assertEquals("SUCCESS", homeOutcome.status)
        assertTrue(globalActionsExecuted.contains(AccessibilityService.GLOBAL_ACTION_HOME))
    }

    @Test
    fun `TEST 6 - Accessibility permission disabled returns structured error and never claims success`() = runBlocking {
        JarvisAccessibilityService.testOverrideEnabledState = false
        val executor = LiveAgentToolExecutor(context = context)

        val clickOutcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_CLICK_ACCESSIBLE_ELEMENT,
            callId = "call_disabled_6",
            args = JSONObject().put("target_text", "Search")
        )
        assertFalse(clickOutcome.executed)
        assertEquals("ERROR", clickOutcome.status)
        assertEquals("ACCESSIBILITY_SERVICE_DISABLED", clickOutcome.errorCode)
        assertTrue(clickOutcome.message.contains("Accessibility", ignoreCase = true))

        val scrollOutcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_SCROLL_SCREEN,
            callId = "call_scroll_disabled_6",
            args = JSONObject().put("direction", "down")
        )
        assertFalse(scrollOutcome.executed)
        assertEquals("ACCESSIBILITY_SERVICE_DISABLED", scrollOutcome.errorCode)
    }

    @Test
    fun `TEST 7 - Requested app not installed truthfully reports APP_NOT_INSTALLED`() = runBlocking {
        val executor = LiveAgentToolExecutor(context = context)
        val outcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_LAUNCH_APPLICATION,
            callId = "call_missing_app_7",
            args = JSONObject()
                .put("app_name", "NonExistentQuantumAppXYZ")
                .put("package_name", "com.nonexistent.quantumxyz")
        )

        assertFalse(outcome.executed)
        assertEquals("ERROR", outcome.status)
        assertEquals("APP_NOT_INSTALLED", outcome.errorCode)
        assertTrue(outcome.message.contains("not installed", ignoreCase = true))
    }

    @Test
    fun `TEST 8 - Tool execution failure when element is missing or malformed call reports truthful error`() = runBlocking {
        JarvisAccessibilityService.testOverrideEnabledState = true
        val emptyRoot = AccessibilityNodeInfo.obtain()
        emptyRoot.packageName = "com.example.testapp"
        emptyRoot.isVisibleToUser = true
        JarvisAccessibilityService.testOverrideRootNodeProvider = { emptyRoot }

        val executor = LiveAgentToolExecutor(context = context)
        val missingElementOutcome = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_CLICK_ACCESSIBLE_ELEMENT,
            callId = "call_fail_8a",
            args = JSONObject().put("target_text", "NonExistentButton123")
        )

        assertFalse(missingElementOutcome.executed)
        assertEquals("ERROR", missingElementOutcome.status)
        assertEquals("ELEMENT_NOT_FOUND", missingElementOutcome.errorCode)

        val malformedOutcome = executor.executeToolCall(
            functionName = "unsupported_magic_tool",
            callId = "call_fail_8b",
            args = JSONObject()
        )
        assertFalse(malformedOutcome.executed)
        assertEquals("ERROR", malformedOutcome.status)
        assertEquals("MALFORMED_TOOL_CALL", malformedOutcome.errorCode)
    }

    @Test
    fun `TEST 9 - Sensitive action requires explicit user confirmation before executing`() = runBlocking {
        val executor = LiveAgentToolExecutor(context = context)

        // 1. Clicking a payment button without user_confirmed=true must be blocked with CONFIRMATION_REQUIRED
        val unconfirmedClick = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_CLICK_ACCESSIBLE_ELEMENT,
            callId = "call_sens_9a",
            args = JSONObject()
                .put("target_text", "Confirm Payment")
                .put("user_confirmed", false)
        )
        assertFalse(unconfirmedClick.executed)
        assertEquals("CONFIRMATION_REQUIRED", unconfirmedClick.status)
        assertEquals("SENSITIVE_ACTION_CONFIRMATION_REQUIRED", unconfirmedClick.errorCode)

        // 2. Calling request_sensitive_action_confirmation with user_confirmed=false requires confirmation
        val unconfirmedSensitiveTool = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_SENSITIVE_ACTION_CONFIRMATION,
            callId = "call_sens_9b",
            args = JSONObject()
                .put("action_type", "PAYMENT")
                .put("target_description", "Send Rs 500 via UPI")
                .put("user_confirmed", false)
        )
        assertFalse(unconfirmedSensitiveTool.executed)
        assertEquals("CONFIRMATION_REQUIRED", unconfirmedSensitiveTool.status)

        // 3. Calling request_sensitive_action_confirmation with user_confirmed=true succeeds
        val confirmedSensitiveTool = executor.executeToolCall(
            functionName = LiveAgentToolExecutor.TOOL_SENSITIVE_ACTION_CONFIRMATION,
            callId = "call_sens_9c",
            args = JSONObject()
                .put("action_type", "PAYMENT")
                .put("target_description", "Send Rs 500 via UPI")
                .put("user_confirmed", true)
        )
        assertTrue(confirmedSensitiveTool.executed)
        assertEquals("SUCCESS", confirmedSensitiveTool.status)
    }

    @Test
    fun `Gemini Live setup registers tools and system instructions and processes toolCall to toolResponse`() = runBlocking {
        registerFakeLauncherPackage(
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.youtube.HomeActivity",
            appLabel = "YouTube"
        )
        val liveManager = GeminiLiveVoiceManager(context, GeminiApiService())
        val setupJson = liveManager.buildSetupMessage(
            modelPath = "models/gemini-2.5-flash-native-audio-preview-12-2025",
            voicePersona = "Puck",
            voiceGender = VoiceGender.MALE,
            assistantName = "JARVIS",
            language = SupportedLanguage.HINGLISH,
            memories = emptyList()
        )

        val setupObj = setupJson.getJSONObject("setup")
        val toolsArr = setupObj.getJSONArray("tools")
        assertTrue(toolsArr.length() > 0)
        val declarations = toolsArr.getJSONObject(0).getJSONArray("functionDeclarations")
        val registeredToolNames = (0 until declarations.length()).map {
            declarations.getJSONObject(it).getString("name")
        }
        assertTrue(registeredToolNames.contains(LiveAgentToolExecutor.TOOL_LAUNCH_APPLICATION))
        assertTrue(registeredToolNames.contains(LiveAgentToolExecutor.TOOL_GET_SCREEN_CONTEXT))
        assertTrue(registeredToolNames.contains(LiveAgentToolExecutor.TOOL_CLICK_ACCESSIBLE_ELEMENT))
        assertTrue(registeredToolNames.contains(LiveAgentToolExecutor.TOOL_SCROLL_SCREEN))
        assertTrue(registeredToolNames.contains(LiveAgentToolExecutor.TOOL_NAVIGATE_BACK))
        assertTrue(registeredToolNames.contains(LiveAgentToolExecutor.TOOL_NAVIGATE_HOME))
        assertTrue(registeredToolNames.contains(LiveAgentToolExecutor.TOOL_SENSITIVE_ACTION_CONFIRMATION))

        // Verify system instruction contains the required Live Agent guidance
        val sysInstructionText = setupObj.getJSONObject("systemInstruction")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
        assertTrue(sysInstructionText.contains("launch_application"))
        assertTrue(sysInstructionText.contains("Never claim that you cannot control the device"))

        // Verify end-to-end toolCall -> execution -> toolResponse JSON structure
        val incomingToolCall = JSONObject().apply {
            put(
                "functionCalls",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "live_call_101")
                        put("name", LiveAgentToolExecutor.TOOL_LAUNCH_APPLICATION)
                        put("args", JSONObject().put("app_name", "YouTube"))
                    }
                )
            )
        }
        val outcomes = liveManager.executeToolCallPayloadSuspend(incomingToolCall)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.first().executed)

        val wsResponse = LiveAgentToolExecutor.buildToolResponseWebSocketMessage(outcomes)
        val fnResponses = wsResponse.getJSONObject("toolResponse").getJSONArray("functionResponses")
        assertEquals(1, fnResponses.length())
        assertEquals("live_call_101", fnResponses.getJSONObject(0).getString("id"))
        assertEquals("launch_application", fnResponses.getJSONObject(0).getString("name"))
        assertEquals(
            "SUCCESS",
            fnResponses.getJSONObject(0).getJSONObject("response").getString("status")
        )
    }
}

