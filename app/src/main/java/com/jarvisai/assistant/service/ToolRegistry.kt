package com.jarvisai.assistant.service

import org.json.JSONArray
import org.json.JSONObject

enum class ToolRiskLevel {
    READ_ONLY,                       // Zero risk (inspecting screen, querying memory vault)
    LOW_RISK,                        // Low risk (launching app, controlling hardware, autonomous operator)
    SENSITIVE_CONFIRMATION_REQUIRED  // Consequential actions (gated by AgenticSafetyGatekeeper)
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val riskLevel: ToolRiskLevel,
    val requiredPermissions: List<String> = emptyList(),
    val parameterSchema: JSONObject
)

/**
 * High-Performance 8-Primitive Atomic Tool Registry for Gemini Live Audio WebSocket (`bidiGenerateContentSetup`).
 *
 * Replaces the bloated 45+ tool declaration payload with 8 sharp, non-overlapping, atomic tool primitives
 * to minimize WebSocket setup payload size, eliminate 3-5s tool routing latency, and prevent LLM tool hallucinations:
 *   1. `execute_screen_operator_goal` -> Triggers `TaskExecutor.executeTask(goal)` Observe-Think-Act-Verify loop.
 *   2. `launch_application`           -> Launches installed Android app with optional `search_query`.
 *   3. `control_device_hardware`      -> Controls `bluetooth`, `wifi`, `hotspot`, `mobile_data`, `flashlight`, `volume`, `brightness`.
 *   4. `inspect_live_screen`          -> Returns active foreground package, window title, and visible UI elements.
 *   5. `send_communication_message`   -> Direct auto-send across `whatsapp`, `telegram`, or `sms`.
 *   6. `trigger_tactical_superpower`  -> Triggers `sentry_mode`, `airspace_radar`, `air_gestures`, `code_black`, `optical_solver`.
 *   7. `query_memory_vault`           -> Queries user profile, operator facts, and learned skills from the memory vault.
 *   8. `stop_or_undo_action`          -> Emergency halt and rollback for any running task.
 */
object ToolRegistry {

    // =========================================================================
    // THE 8 ATOMIC GEMINI LIVE PRIMITIVES
    // =========================================================================
    const val TOOL_EXECUTE_SCREEN_OPERATOR_GOAL = "execute_screen_operator_goal"
    const val TOOL_LAUNCH_APPLICATION = "launch_application"
    const val TOOL_CONTROL_DEVICE_HARDWARE = "control_device_hardware"
    const val TOOL_INSPECT_LIVE_SCREEN = "inspect_live_screen"
    const val TOOL_SEND_COMMUNICATION_MESSAGE = "send_communication_message"
    const val TOOL_TRIGGER_TACTICAL_SUPERPOWER = "trigger_tactical_superpower"
    const val TOOL_QUERY_MEMORY_VAULT = "query_memory_vault"
    const val TOOL_STOP_OR_UNDO_ACTION = "stop_or_undo_action"

    // =========================================================================
    // COMPATIBILITY CONSTANTS (Retained for internal callers; NOT in ALL_TOOLS)
    // =========================================================================
    const val TOOL_RUN_AUTONOMOUS_GOAL = "run_autonomous_react_goal"
    const val TOOL_RUN_AUTONOMOUS_REACT_GOAL = TOOL_RUN_AUTONOMOUS_GOAL
    const val TOOL_EXECUTE_PRIVATE_AGENT_OPERATOR = "execute_private_agent_operator"
    const val TOOL_CONTROL_DEVICE_SETTING = "control_device_setting"
    const val TOOL_GET_SCREEN_CONTEXT = "get_screen_context"
    const val TOOL_INSPECT_VISUAL_SCREEN = "inspect_visual_screen"
    const val TOOL_READ_SCREEN_TEXT = "read_screen_text"
    const val TOOL_SEND_COMMUNICATION = TOOL_SEND_COMMUNICATION_MESSAGE
    const val TOOL_VOICE_UNDO_OR_STOP = "voice_undo_or_stop"
    const val TOOL_UPDATE_CONSCIOUSNESS_VAULT = "update_hermes_consciousness_vault"

    const val TOOL_CLICK_ACCESSIBLE_ELEMENT = "click_accessible_element"
    const val TOOL_SCROLL_SCREEN = "scroll_screen"
    const val TOOL_NAVIGATE_BACK = "navigate_back"
    const val TOOL_NAVIGATE_HOME = "navigate_home"
    const val TOOL_SENSITIVE_ACTION_CONFIRMATION = "request_sensitive_action_confirmation"
    const val TOOL_DIRECTIONAL_GESTURE = "directional_gesture"
    const val TOOL_TYPE_TEXT = "type_text"
    const val TOOL_FOCUS_INPUT = "focus_input_field"
    const val TOOL_CLEAR_TEXT = "clear_text"
    const val TOOL_GLOBAL_ACTION = "global_action"
    const val TOOL_GET_SYSTEM_TELEMETRY = "get_system_telemetry"
    const val TOOL_ADJUST_SYSTEM_VOLUME = "adjust_system_volume"
    const val TOOL_CONTROL_MEDIA_PLAYBACK = "control_media_playback"
    const val TOOL_OPEN_PHONE_DIALER = "open_phone_dialer"
    const val TOOL_OPEN_SPOTIFY_MUSIC = "open_spotify_music"
    const val TOOL_TAP_SET_OF_MARK_BADGE = "tap_set_of_mark_badge"
    const val TOOL_CONTROL_FLOATING_HUD = "control_floating_hud"
    const val TOOL_REPLY_TO_NOTIFICATION = "reply_to_notification"
    const val TOOL_FILL_SCREEN_FORM = "fill_screen_form"
    const val TOOL_CROSS_APP_SCREEN_BRIDGE = "cross_app_screen_bridge"
    const val TOOL_MANAGE_LEARNING_MODE = "manage_learning_mode"
    const val TOOL_SET_ALARM_OR_TIMER = "set_alarm_or_timer"
    const val TOOL_DEEP_LINK_TELEPORT = "deep_link_teleport"
    const val TOOL_EXECUTE_TERMUX_COMMAND = "execute_termux_command"
    const val TOOL_ANSWER_OR_END_CALL = "answer_or_end_call"
    const val TOOL_TRIGGER_WATCHMAN_ALERT = "trigger_watchman_alert"
    const val TOOL_REPLY_TO_VOICE_NOTE = "reply_to_voice_note"
    const val TOOL_EXECUTE_BUILTIN_MACRO = "execute_builtin_macro"
    const val TOOL_OPEN_IN_APP_BROWSER = "open_in_app_browser"
    const val TOOL_AUTO_FILL_OTP = "auto_fill_otp"
    const val TOOL_COMPARE_PRICES = "compare_prices_across_apps"
    const val TOOL_SCROLL_STITCH_READER = "scroll_stitch_page_reader"
    const val TOOL_REGISTER_SENTINEL_RULE = "register_sentinel_rule"
    const val TOOL_WEB_DOM_ACTION = "execute_web_dom_action"
    const val TOOL_SCREEN_CALL_FOLLOWUP = "screen_call_with_followup"
    const val TOOL_SYNTHESIZE_PROMETHEUS_TOOL = "synthesize_prometheus_tool"
    const val TOOL_EXECUTE_OFFLINE_SLM = "execute_offline_slm_command"
    const val TOOL_CLAWBOT_REMOTE_GATEWAY = "dispatch_clawbot_remote_command"
    const val TOOL_CALL_MCP_SERVER = "call_mcp_server_tool"
    const val TOOL_SWITCH_PROCEDURAL_HUD = "switch_procedural_hud_mode"
    const val TOOL_CHRONOS_FOCUS_GUARDIAN = "evaluate_chronos_focus_guardian"
    const val TOOL_CYBER_SECURITY_SENTRY = "run_cyber_security_sentry"
    const val TOOL_GENERATE_NEURAL_IMAGE = "generate_neural_image"
    const val TOOL_RUN_SWARM_PARALLEL = "run_swarm_parallel_mission"
    const val TOOL_GHOST_PILOT_GESTURE = "execute_ghost_pilot_gesture"
    const val TOOL_SCAN_OVERHEAD_AIRCRAFT = "scan_overhead_aircraft"
    const val TOOL_CONFIGURE_AIR_GESTURES = "configure_proximity_air_gestures"
    const val TOOL_SET_SENTRY_MODE = "set_accelerometer_sentry_mode"
    const val TOOL_QUERY_OLLAMA_LOCAL_LLM = "query_ollama_local_llm"

    /**
     * Strictly the 8 atomic tool primitives sent over `BidiGenerateContentSetup`.
     */
    val ALL_TOOLS: List<ToolDefinition> = listOf(
        // 1. execute_screen_operator_goal
        ToolDefinition(
            name = TOOL_EXECUTE_SCREEN_OPERATOR_GOAL,
            description = "Executes any multi-step or on-screen UI automation goal (clicking, typing, scrolling, searching, playing videos, ordering, booking, filling forms, or changing in-app settings) via the autonomous Observe-Think-Act-Verify loop (`TaskExecutor.executeTask`).",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("goal", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Full natural-language screen automation goal to execute (e.g. 'Open YouTube, search MrBeast channel and play the latest video', 'Scroll down and click Buy Now').")
                    })
                })
                put("required", JSONArray().put("goal"))
            }
        ),

        // 2. launch_application
        ToolDefinition(
            name = TOOL_LAUNCH_APPLICATION,
            description = "Launches an installed Android application (e.g. YouTube, Chrome, WhatsApp, Settings, Spotify, Camera, Calculator, Maps) with an optional search query.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Display name of the Android application to open (e.g. 'YouTube', 'Chrome', 'WhatsApp', 'Settings', 'Spotify').")
                    })
                    put("search_query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional search query to execute when launching YouTube, Chrome, or Spotify.")
                    })
                })
                put("required", JSONArray().put("app_name"))
            }
        ),

        // 3. control_device_hardware
        ToolDefinition(
            name = TOOL_CONTROL_DEVICE_HARDWARE,
            description = "Controls instant Android device hardware toggles and levels: bluetooth, wifi, hotspot, mobile_data, flashlight, volume, or brightness.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("setting", JSONObject().apply {
                        put("type", "STRING")
                        put(
                            "enum",
                            JSONArray()
                                .put("bluetooth")
                                .put("wifi")
                                .put("hotspot")
                                .put("mobile_data")
                                .put("flashlight")
                                .put("volume")
                                .put("brightness")
                        )
                        put("description", "Hardware setting to control: 'bluetooth', 'wifi', 'hotspot', 'mobile_data', 'flashlight', 'volume', or 'brightness'.")
                    })
                    put("state", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Target state: boolean ('true'/'false' or 'on'/'off') for toggles, or integer percentage ('0' to '100', 'up', 'down', 'mute') for volume and brightness.")
                    })
                })
                put("required", JSONArray().put("setting").put("state"))
            }
        ),

        // 4. inspect_live_screen
        ToolDefinition(
            name = TOOL_INSPECT_LIVE_SCREEN,
            description = "Inspects the live Android screen and returns the active foreground package, window title, and all visible interactive/text UI elements.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("focus_area", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional focus region or visual query (e.g. 'full_screen', 'top', 'bottom', 'buttons', 'inputs', or a specific question about the screen).")
                    })
                })
            }
        ),

        // 5. send_communication_message
        ToolDefinition(
            name = TOOL_SEND_COMMUNICATION_MESSAGE,
            description = "Directly sends a message to a contact or phone number via WhatsApp, Telegram, or SMS without requiring secondary confirmation.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("platform", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray().put("whatsapp").put("telegram").put("sms"))
                        put("description", "Messaging platform: 'whatsapp', 'telegram', or 'sms'.")
                    })
                    put("recipient", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Recipient contact name or phone number (e.g. 'Rahul', 'Mummy', '+919876543210').")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Message text body to send directly.")
                    })
                })
                put("required", JSONArray().put("platform").put("recipient").put("message"))
            }
        ),

        // 6. trigger_tactical_superpower
        ToolDefinition(
            name = TOOL_TRIGGER_TACTICAL_SUPERPOWER,
            description = "Triggers a specialized JARVIS tactical hardware or security superpower: sentry_mode (accelerometer anti-theft watchdog), airspace_radar (live ADS-B overhead aircraft radar), air_gestures (proximity wave gestures), code_black (full cyber security audit & red-alert lock), or optical_solver (on-screen visual problem & OCR solver).",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("superpower", JSONObject().apply {
                        put("type", "STRING")
                        put(
                            "enum",
                            JSONArray()
                                .put("sentry_mode")
                                .put("airspace_radar")
                                .put("air_gestures")
                                .put("code_black")
                                .put("optical_solver")
                        )
                        put("description", "Tactical superpower to activate: 'sentry_mode', 'airspace_radar', 'air_gestures', 'code_black', or 'optical_solver'.")
                    })
                })
                put("required", JSONArray().put("superpower"))
            }
        ),

        // 7. query_memory_vault
        ToolDefinition(
            name = TOOL_QUERY_MEMORY_VAULT,
            description = "Queries the persistent JARVIS Memory Vault (user profile, personal facts, SOUL.md, USER.md, AGENT.md, and learned habits) for a given query.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Search query or topic to look up in the user's memory vault (e.g. 'user profile', 'family', 'preferences', 'saved macros').")
                    })
                })
                put("required", JSONArray().put("query"))
            }
        ),

        // 8. stop_or_undo_action
        ToolDefinition(
            name = TOOL_STOP_OR_UNDO_ACTION,
            description = "Emergency halt and rollback for any running autonomous screen operator task or pending safety gate.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("reason", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Reason for halting or undoing the current action (e.g. 'User requested stop', 'Wrong screen opened').")
                    })
                })
                put("required", JSONArray().put("reason"))
            }
        )
    )

    fun buildDeclarationsJsonArray(): JSONArray {
        val array = JSONArray()
        for (tool in ALL_TOOLS) {
            array.put(
                JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parameterSchema)
                }
            )
        }
        val codingTools = JarvisCodingToolsProvider.getCodingToolDeclarations()
        for (i in 0 until codingTools.length()) {
            array.put(codingTools.getJSONObject(i))
        }
        return array
    }
}
