package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.ThinkingLevel
import com.jarvisai.assistant.model.ThinkingPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThinkingSelectorBottomSheet(
    currentPreference: ThinkingPreference,
    activeModelName: String = "Current Model",
    modelSupportsThinking: Boolean = true,
    onPreferenceSelected: (ThinkingPreference) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1424),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF00E5FF).copy(alpha = 0.5f)) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Thinking Depth",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Antigravity & Gemini Thinking Depth",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (modelSupportsThinking) {
                            "Configure reasoning budget for $activeModelName"
                        } else {
                            "$activeModelName uses direct mode; applies when a Thinking model is active"
                        },
                        fontSize = 11.5.sp,
                        color = if (modelSupportsThinking) Color(0xFF94A3B8) else Color(0xFFF59E0B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ThinkingLevel.entries.forEach { level ->
                val isSelected = currentPreference.level == level
                val accent = when (level) {
                    ThinkingLevel.OFF -> Color(0xFF94A3B8)
                    ThinkingLevel.LOW -> Color(0xFF10B981)
                    ThinkingLevel.MEDIUM -> Color(0xFF00E5FF)
                    ThinkingLevel.HIGH -> Color(0xFFA78BFA)
                    ThinkingLevel.EXTRA -> Color(0xFFF59E0B)
                }
                val borderColor = if (isSelected) accent else Color(0xFF1E293B)
                val bgColor = if (isSelected) accent.copy(alpha = 0.14f) else Color(0xFF111827)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onPreferenceSelected(ThinkingPreference(level = level))
                            onDismiss()
                        }
                        .testTag("thinking_level_${level.name.lowercase()}"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                    color = bgColor
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = level.label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) accent else Color.White
                                )
                                if (level == ThinkingLevel.MEDIUM) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "DEFAULT",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E5FF)
                                        )
                                    }
                                } else if (level == ThinkingLevel.EXTRA) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "XHIGH",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = level.description,
                                fontSize = 11.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
