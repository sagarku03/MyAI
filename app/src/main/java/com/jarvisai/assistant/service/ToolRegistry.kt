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
    val parameterSchema: JSONObject,
    val isNonBlocking: Boolean = false
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
    // INTERACTIVE LIVE SCREEN PRIMITIVES + DYNAMIC DISCOVERY (Maya-Grade)
    // =========================================================================
    const val TOOL_READ_SCREEN_ELEMENTS = "read_screen_elements"
    const val TOOL_TAP_ELEMENT = "tap_element"
    const val TOOL_TAP_TEXT = "tap_text"
    const val TOOL_TAP_COORDINATES = "tap_coordinates"
    const val TOOL_WAIT_FOR_SCREEN_TEXT = "wait_for_screen_text"
    const val TOOL_GO_BACK = "go_back"
    const val TOOL_FIND_TOOL = "find_tool"
    const val TOOL_RUN_TOOL = "run_tool"

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
            },
            isNonBlocking = true
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

    val INTERACTIVE_LIVE_TOOLS: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = TOOL_READ_SCREEN_ELEMENTS,
            description = "Dumps all visible interactive/labeled UI elements on the current screen with indexed `#id` handles, labels, clickable/editable flags, and pixel bounds so you can call `tap_element(id)` or detect opaque screens needing vision.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("filter", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional filter query or category ('clickable', 'editable', 'all').")
                    })
                })
            }
        ),
        ToolDefinition(
            name = TOOL_TAP_ELEMENT,
            description = "Taps a specific UI element by its `#id` from `read_screen_elements`. Verifies the live node has not drifted before tapping and waits for screen change.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Numeric element id returned by `read_screen_elements`.")
                    })
                })
                put("required", JSONArray().put("id"))
            }
        ),
        ToolDefinition(
            name = TOOL_TAP_TEXT,
            description = "Finds and taps an on-screen button or label by visible text or content description. Refuses ambiguous multi-matches cleanly and polls briefly for animations.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Visible text or button description to tap.")
                    })
                    put("exact", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "Optional exact match flag (default false).")
                    })
                })
                put("required", JSONArray().put("text"))
            }
        ),
        ToolDefinition(
            name = TOOL_TAP_COORDINATES,
            description = "Taps exact (x, y) coordinates seen in the live screen screenshot or Set-of-Mark frame. Automatically unscales image coordinates (`space='image'`), 0-1000 grid (`space='1000'`), or screen pixels (`space='screen'`), and overlays a red proof ring on the post-tap visual frame.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("x", JSONObject().apply {
                        put("type", "NUMBER")
                        put("description", "Horizontal X coordinate.")
                    })
                    put("y", JSONObject().apply {
                        put("type", "NUMBER")
                        put("description", "Vertical Y coordinate.")
                    })
                    put("space", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray().put("image").put("screen").put("1000").put("fraction").put("auto"))
                        put("description", "Coordinate space: 'image' (from screenshot preview), '1000' (0..1000 grid), 'screen' (physical pixels), 'fraction' (0..1), or 'auto'.")
                    })
                    put("image_width", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Optional source image width for letterbox unscaling.")
                    })
                    put("image_height", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Optional source image height for letterbox unscaling.")
                    })
                    put("label_hint", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional description of what you are tapping.")
                    })
                })
                put("required", JSONArray().put("x").put("y"))
            }
        ),
        ToolDefinition(
            name = TOOL_TYPE_TEXT,
            description = "Types text into the active or target input field. Supports realistic human-paced typing (`realistic=true`), optional field focus hint (`target`), and optional Enter/Send submission (`submit=true`).",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Text to enter into the field.")
                    })
                    put("target", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional label/hint of the input field to focus first.")
                    })
                    put("submit", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "Whether to press Enter / submit after typing (default false).")
                    })
                    put("realistic", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "Type character-by-character with human pacing and readback verification (default true).")
                    })
                })
                put("required", JSONArray().put("text"))
            }
        ),
        ToolDefinition(
            name = TOOL_SCROLL_SCREEN,
            description = "Scrolls the primary scrollable container on screen ('down', 'up', 'left', 'right') using smart semantic/gesture fallback and verifies content moved.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray().put("down").put("up").put("left").put("right"))
                        put("description", "Scroll direction.")
                    })
                    put("distance", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray().put("SHORT").put("MEDIUM").put("LONG"))
                        put("description", "Scroll distance ('SHORT', 'MEDIUM', 'LONG').")
                    })
                })
                put("required", JSONArray().put("direction"))
            }
        ),
        ToolDefinition(
            name = TOOL_WAIT_FOR_SCREEN_TEXT,
            description = "Waits up to `timeout_ms` (max 8000ms) for a specific text label to appear or disappear on screen.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Target text to wait for.")
                    })
                    put("mode", JSONObject().apply {
                        put("type", "STRING")
                        put("enum", JSONArray().put("appear").put("disappear"))
                        put("description", "Whether to wait for text to 'appear' or 'disappear'.")
                    })
                    put("timeout_ms", JSONObject().apply {
                        put("type", "INTEGER")
                        put("description", "Timeout in milliseconds (default 5000).")
                    })
                })
                put("required", JSONArray().put("text"))
            }
        ),
        ToolDefinition(
            name = TOOL_GO_BACK,
            description = "Presses Android Back button once and waits for screen transition.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
        ),
        ToolDefinition(
            name = TOOL_FIND_TOOL,
            description = "Discovers specialized JARVIS/Agentra phone & web tools on demand (e.g., timer/alarm, OTP autofill, phone call, browser, notifications, media playback, home/recents, scroll-stitch reader, Termux shell, macros) so you can call them via `run_tool`.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Keyword or capability to search for (e.g. 'alarm', 'call', 'browser', 'volume', 'media', 'notification', 'otp', 'macro').")
                    })
                })
                put("required", JSONArray().put("query"))
            }
        ),
        ToolDefinition(
            name = TOOL_RUN_TOOL,
            description = "Executes any specialized capability discovered via `find_tool` by passing its `name` and JSON object `args`.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Exact tool name returned by `find_tool`.")
                    })
                    put("args", JSONObject().apply {
                        put("type", "OBJECT")
                        put("description", "JSON arguments object for the selected tool.")
                    })
                })
                put("required", JSONArray().put("name"))
            }
        )
    )

    fun buildDeclarationsJsonArray(includeNonBlockingBehavior: Boolean = true): JSONArray {
        val array = JSONArray()
        for (tool in ALL_TOOLS) {
            array.put(
                JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    if (includeNonBlockingBehavior && tool.isNonBlocking) {
                        put("behavior", "NON_BLOCKING")
                    }
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

    /**
     * Full declaration array used by `BidiGenerateContentSetup` in Live Voice sessions:
     * includes the 8 core tools + interactive screen manipulation primitives (`read_screen_elements`,
     * `tap_element`, `tap_text`, `tap_coordinates`, `type_text`, `scroll_screen`, `wait_for_screen_text`,
     * `go_back`) + dynamic `find_tool` / `run_tool` + coding tools.
     */
    fun buildLiveVoiceDeclarationsJsonArray(
        includeNonBlockingBehavior: Boolean = true,
        includeInteractiveScreenTools: Boolean = true
    ): JSONArray {
        val array = buildDeclarationsJsonArray(includeNonBlockingBehavior)
        if (includeInteractiveScreenTools) {
            for (tool in INTERACTIVE_LIVE_TOOLS) {
                array.put(
                    JSONObject().apply {
                        put("name", tool.name)
                        put("description", tool.description)
                        if (includeNonBlockingBehavior && tool.isNonBlocking) {
                            put("behavior", "NON_BLOCKING")
                        }
                        put("parameters", tool.parameterSchema)
                    }
                )
            }
        }
        return array
    }
}
