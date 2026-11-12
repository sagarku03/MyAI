package com.jarvisai.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus

/**
 * Interactive Multiple-Choice & Single-Choice Questionnaire Card (`ChoiceQuestionnaireCard`)
 * for the `ask_question` agent tool. Enforces 48.dp minimum touch targets, 8.dp spacing,
 * glowing selection borders, and custom write-in responses.
 */
@Composable
fun ChoiceQuestionnaireCard(
    toolCall: ToolCall,
    onSubmit: (toolCall: ToolCall, responses: List<ChoiceResponseItem>) -> Unit,
    onSkip: (toolCall: ToolCall) -> Unit,
    modifier: Modifier = Modifier
) {
    val questionnaire = toolCall.questionnaire ?: return
    var submittedLocally by remember(toolCall.id) { mutableStateOf(false) }
    val isCompleted = toolCall.status == ToolCallStatus.SUCCESS || submittedLocally

    val selectedOptions = remember(toolCall.id) { mutableStateMapOf<String, MutableSet<String>>() }
    val customAnswers = remember(toolCall.id) { mutableStateMapOf<String, String>() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("choice_questionnaire_card_${toolCall.id}"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0B0F19),
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = 1.2.dp,
            brush = if (isCompleted) {
                Brush.horizontalGradient(listOf(Color(0xFF10B981).copy(alpha = 0.6f), Color(0xFF1F2937)))
            } else {
                Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF38BDF8)))
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Outlined.HelpOutline,
                            contentDescription = "Agent Questionnaire",
                            tint = if (isCompleted) Color(0xFF10B981) else Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = questionnaire.title.ifBlank { "Agent Clarification Request" },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFFF9FAFB)
                        )
                        Text(
                            text = "TOOL // ASK_QUESTION",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                if (isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Text(
                            text = "SUBMITTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Questions List
            questionnaire.questions.forEachIndexed { qIdx, questionItem ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${qIdx + 1}. ${questionItem.question}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFF3F4F6),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF111827)
                        ) {
                            Text(
                                text = if (questionItem.isMultiSelect) "MULTI-SELECT" else "SINGLE-SELECT",
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val selectedForQ = selectedOptions.getOrPut(questionItem.question) { mutableSetOf() }

                    questionItem.options.forEach { opt ->
                        val optionValue = opt.text.ifBlank { opt.label }
                        val isChecked = selectedForQ.contains(optionValue)
                        val borderColor by animateColorAsState(
                            targetValue = if (isChecked) Color(0xFF38BDF8) else Color(0xFF1F2937),
                            label = "option_border_glow"
                        )
                        val containerColor by animateColorAsState(
                            targetValue = if (isChecked) Color(0xFF6366F1).copy(alpha = 0.18f) else Color(0xFF111827),
                            label = "option_bg_glow"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(containerColor)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .clickable(enabled = !isCompleted) {
                                    if (questionItem.isMultiSelect) {
                                        if (isChecked) selectedForQ.remove(optionValue) else selectedForQ.add(optionValue)
                                    } else {
                                        selectedForQ.clear()
                                        selectedForQ.add(optionValue)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (questionItem.isMultiSelect) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF38BDF8),
                                        uncheckedColor = Color(0xFF64748B),
                                        checkmarkColor = Color(0xFF0B0F19)
                                    )
                                )
                            } else {
                                RadioButton(
                                    selected = isChecked,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF38BDF8),
                                        unselectedColor = Color(0xFF64748B)
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = optionValue,
                                    fontSize = 13.5.sp,
                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isChecked) Color.White else Color(0xFFD1D5DB)
                                )
                                if (opt.description.isNotBlank()) {
                                    Text(
                                        text = opt.description,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }

                    if (!isCompleted) {
                        OutlinedTextField(
                            value = customAnswers[questionItem.question] ?: "",
                            onValueChange = { customAnswers[questionItem.question] = it },
                            placeholder = {
                                Text(
                                    "Write-in custom response (optional)...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF1F2937),
                                focusedContainerColor = Color(0xFF111827),
                                unfocusedContainerColor = Color(0xFF111827)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Action Buttons (Minimum 48.dp touch target, 8.dp separation)
            if (!isCompleted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            submittedLocally = true
                            onSkip(toolCall)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF374151)),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("questionnaire_skip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Skip or Cancel",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Skip / Cancel", color = Color(0xFFD1D5DB), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            val responseList = questionnaire.questions.map { q ->
                                ChoiceResponseItem(
                                    question = q.question,
                                    selectedOptionIds = selectedOptions[q.question]?.toList() ?: emptyList(),
                                    writeInResponse = customAnswers[q.question]?.takeIf { it.isNotBlank() }
                                )
                            }
                            submittedLocally = true
                            onSubmit(toolCall, responseList)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color(0xFF0B0F19)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("questionnaire_submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = "Submit Answers",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Answers", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
