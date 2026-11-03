package com.jarvisai.assistant.service

import org.json.JSONArray
import org.json.JSONObject

enum class ToolRiskLevel {
    READ_ONLY,                       // Zero risk (reading screen, getting battery info)
    LOW_RISK,                        // Low risk (opening app, scrolling, navigating home/back)
    SENSITIVE_CONFIRMATION_REQUIRED  // Consequential actions (sending messages, payments, deleting data)
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val riskLevel: ToolRiskLevel,
    val requiredPermissions: List<String> = emptyList(),
    val parameterSchema: JSONObject
)

/**
 * Central tool registry providing validated JSON schemas, risk classifications,
 * and permission requirements for all JARVIS AI tools callable by Gemini Live.
 */
object ToolRegistry {

    const val TOOL_LAUNCH_APPLICATION = "launch_application"
    const val TOOL_GET_SCREEN_CONTEXT = "get_screen_context"
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
    const val TOOL_READ_SCREEN_TEXT = "read_screen_text"
    const val TOOL_GET_SYSTEM_TELEMETRY = "get_system_telemetry"
    const val TOOL_ADJUST_SYSTEM_VOLUME = "adjust_system_volume"
    const val TOOL_CONTROL_MEDIA_PLAYBACK = "control_media_playback"
    const val TOOL_SEND_COMMUNICATION = "send_communication_message"
    const val TOOL_OPEN_PHONE_DIALER = "open_phone_dialer"
    const val TOOL_OPEN_SPOTIFY_MUSIC = "open_spotify_music"

    val ALL_TOOLS: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = TOOL_LAUNCH_APPLICATION,
            description = "Launches an installed Android app (YouTube, Chrome, Settings, Calculator, WhatsApp, Spotify, etc.) on the user's phone.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("app_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Display name of the Android app, e.g. 'YouTube', 'Chrome', 'Settings', 'Calculator'.")
                    })
                    put("package_name", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional Android package name if known.")
                    })
                    put("search_query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional search query when opening YouTube or Chrome.")
                    })
                })
                put("required", JSONArray().put("app_name"))
            }
        ),
        ToolDefinition(
            name = TOOL_GET_SCREEN_CONTEXT,
            description = "Inspects the current Android screen via JARVIS Accessibility Service and returns visible UI elements and active app package.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
        ),
        ToolDefinition(
            name = TOOL_CLICK_ACCESSIBLE_ELEMENT,
            description = "Clicks a visible button or UI element on the current Android screen using its text, contentDescription, or viewId.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("target_text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Visible text of the button or UI element to click.")
                    })
                    put("content_description", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional contentDescription of the button/icon.")
                    })
                    put("view_id", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional view resource ID.")
                    })
                    put("user_confirmed", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True only if the user explicitly confirmed a sensitive action.")
                    })
                })
            }
        ),
        ToolDefinition(
            name = TOOL_SCROLL_SCREEN,
            description = "Scrolls the current scrollable page up or down using the Accessibility Service.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Direction to scroll: 'down' or 'up'.")
                    })
                })
                put("required", JSONArray().put("direction"))
            }
        ),
        ToolDefinition(
            name = TOOL_NAVIGATE_BACK,
            description = "Performs the Android global Back action.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
        ),
        ToolDefinition(
            name = TOOL_NAVIGATE_HOME,
            description = "Performs the Android global Home action returning to Home screen.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
        ),
        ToolDefinition(
            name = TOOL_SENSITIVE_ACTION_CONFIRMATION,
            description = "Enforces user confirmation before executing any sensitive or irreversible action (messages, emails, payments, data deletion).",
            riskLevel = ToolRiskLevel.SENSITIVE_CONFIRMATION_REQUIRED,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("action_type", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Action category: 'SEND_MESSAGE', 'PAYMENT', 'DELETE_DATA', 'SECURITY_SETTING'.")
                    })
                    put("target_description", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Description of sensitive action to confirm with the user.")
                    })
                    put("user_confirmed", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True only after user explicitly confirmed.")
                    })
                })
                put("required", JSONArray().put("action_type").put("target_description"))
            }
        ),
        ToolDefinition(
            name = TOOL_DIRECTIONAL_GESTURE,
            description = "Performs directional screen gestures: 'scroll' or 'swipe' in direction 'up', 'down', 'left', 'right', or 'tap' / 'long_press' at coordinates.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Action: 'scroll', 'swipe', 'tap', or 'long_press'.")
                    })
                    put("direction", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Direction: 'up', 'down', 'left', or 'right'.")
                    })
                })
            }
        ),
        ToolDefinition(
            name = TOOL_TYPE_TEXT,
            description = "Types text into the active or targeted input field using Accessibility ACTION_SET_TEXT & clipboard fallback.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("text", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Text to input.")
                    })
                    put("target_label", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional label or hint of target field.")
                    })
                    put("append", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True to append, false to replace.")
                    })
                    put("submit", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True to submit after entering text.")
                    })
                })
                put("required", JSONArray().put("text"))
            }
        ),
        ToolDefinition(
            name = TOOL_FOCUS_INPUT,
            description = "Focuses an editable input field by hint, label, or viewId.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("target_label", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Label or hint of field to focus.")
                    })
                })
            }
        ),
        ToolDefinition(
            name = TOOL_CLEAR_TEXT,
            description = "Clears text in the active or specified input field.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
        ),
        ToolDefinition(
            name = TOOL_GLOBAL_ACTION,
            description = "Performs an Android global action: 'back', 'home', 'recents', 'notifications', or 'quick_settings'.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Global action name: 'back', 'home', 'recents', 'notifications', 'quick_settings'.")
                    })
                })
                put("required", JSONArray().put("action"))
            }
        ),
        ToolDefinition(
            name = TOOL_READ_SCREEN_TEXT,
            description = "Reads and returns all visible text hierarchy from the current Android screen.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
        ),
        ToolDefinition(
            name = TOOL_GET_SYSTEM_TELEMETRY,
            description = "Reads current real-time device battery percentage, charging status, network connectivity (Wi-Fi/cellular), RAM, storage, and current date/time.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject())
            }
        ),
        ToolDefinition(
            name = TOOL_ADJUST_SYSTEM_VOLUME,
            description = "Adjusts device media audio volume: 'up', 'down', 'mute', or 'unmute'.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("direction", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Volume adjustment: 'up', 'down', 'mute', or 'unmute'.")
                    })
                })
                put("required", JSONArray().put("direction"))
            }
        ),
        ToolDefinition(
            name = TOOL_CONTROL_MEDIA_PLAYBACK,
            description = "Controls media playback on the device: 'play', 'pause', 'toggle', 'next', or 'previous'.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("action", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Media action: 'play', 'pause', 'toggle', 'next', 'previous'.")
                    })
                })
                put("required", JSONArray().put("action"))
            }
        ),
        ToolDefinition(
            name = TOOL_SEND_COMMUNICATION,
            description = "Prepares or opens a message in WhatsApp, Telegram, SMS, or Email with recipient and text content. Always requires user confirmation for sending.",
            riskLevel = ToolRiskLevel.SENSITIVE_CONFIRMATION_REQUIRED,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("platform", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Platform: 'whatsapp', 'telegram', 'sms', or 'email'.")
                    })
                    put("recipient", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Recipient phone number, contact name, or email address.")
                    })
                    put("message", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Message text body.")
                    })
                    put("subject", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Optional email subject line (for email).")
                    })
                    put("user_confirmed", JSONObject().apply {
                        put("type", "BOOLEAN")
                        put("description", "True only if the user explicitly confirmed sending this message.")
                    })
                })
                put("required", JSONArray().put("platform").put("message"))
            }
        ),
        ToolDefinition(
            name = TOOL_OPEN_PHONE_DIALER,
            description = "Opens the Android phone dialer with the specified phone number loaded ready for the user to tap call.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("phone_number", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Phone number to dial.")
                    })
                })
                put("required", JSONArray().put("phone_number"))
            }
        ),
        ToolDefinition(
            name = TOOL_OPEN_SPOTIFY_MUSIC,
            description = "Opens Spotify and searches for a song, artist, album, or playlist.",
            riskLevel = ToolRiskLevel.LOW_RISK,
            parameterSchema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("search_query", JSONObject().apply {
                        put("type", "STRING")
                        put("description", "Song, artist, or playlist name to play on Spotify.")
                    })
                })
                put("required", JSONArray().put("search_query"))
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
        return array
    }
}
