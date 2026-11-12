package com.jarvisai.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus

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
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, if (isCompleted) Color(0xFF1E293B) else Color(0xFF00E5FF).copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Antigravity Questions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isCompleted) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ANSWERED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Questions List
            questionnaire.questions.forEachIndexed { qIdx, questionItem ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = "${qIdx + 1}. ${questionItem.question}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val selectedForQ = selectedOptions.getOrPut(questionItem.question) { mutableSetOf() }

                    questionItem.options.forEach { opt ->
                        val isChecked = selectedForQ.contains(opt.text)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = !isCompleted) {
                                    if (questionItem.isMultiSelect) {
                                        if (isChecked) selectedForQ.remove(opt.text) else selectedForQ.add(opt.text)
                                    } else {
                                        selectedForQ.clear()
                                        selectedForQ.add(opt.text)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (questionItem.isMultiSelect) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                                )
                            } else {
                                RadioButton(
                                    selected = isChecked,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00E5FF))
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = opt.text,
                                fontSize = 12.5.sp,
                                color = if (isChecked) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (!isCompleted) {
                        OutlinedTextField(
                            value = customAnswers[questionItem.question] ?: "",
                            onValueChange = { customAnswers[questionItem.question] = it },
                            placeholder = {
                                Text("Other (optional custom response)...", fontSize = 11.5.sp, color = Color(0xFF64748B))
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }

            // Action Buttons
            if (!isCompleted) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            submittedLocally = true
                            onSkip(toolCall)
                        }
                    ) {
                        Text("Skip", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val responseList = questionnaire.questions.map { q ->
                                ChoiceResponseItem(
                                    question = q.question,
                                    selectedOptionIds = selectedOptions[q.question]?.toList() ?: emptyList(),
                                    writeInResponse = customAnswers[q.question]
                                )
                            }
                            submittedLocally = true
                            onSubmit(toolCall, responseList)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Answers", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
