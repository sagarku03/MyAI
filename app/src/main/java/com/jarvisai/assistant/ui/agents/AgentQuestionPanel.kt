package com.jarvisai.assistant.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Phase 3: Interactive `AskUserQuestion` card for Claude Code, Codex, and Antigravity trajectories
 * when the agent needs clarification from the user before proceeding.
 */
data class AgentInteractiveQuestion(
    val id: String,
    val prompt: String,
    val options: List<String>,
    val allowMultiple: Boolean = false
)

@Composable
fun AgentQuestionPanel(
    question: AgentInteractiveQuestion,
    onSubmitAnswer: (questionId: String, selectedAnswers: List<String>, customNote: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedOptions = remember(question.id) { mutableStateListOf<String>() }
    var customAnswer by remember(question.id) { mutableStateOf("") }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("agent_question_panel_${question.id}"),
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "AGENT CLARIFICATION REQUIRED",
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = question.prompt,
                color = Color(0xFFF0F6FC),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                question.options.forEach { option ->
                    val isChecked = selectedOptions.contains(option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isChecked) Color(0xFF1F2937) else Color(0xFF0D1117))
                            .border(
                                width = 1.dp,
                                color = if (isChecked) Color(0xFF00E5FF) else Color(0xFF30363D),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (question.allowMultiple) {
                                    if (isChecked) selectedOptions.remove(option) else selectedOptions.add(option)
                                } else {
                                    selectedOptions.clear()
                                    selectedOptions.add(option)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (question.allowMultiple) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null
                            )
                        } else {
                            RadioButton(
                                selected = isChecked,
                                onClick = null
                            )
                        }
                        Text(
                            text = option,
                            color = Color(0xFFE6EDF3),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = customAnswer,
                onValueChange = { customAnswer = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_question_custom_input"),
                placeholder = { Text("Or type a custom response...", fontSize = 12.sp) },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onSubmitAnswer(
                        question.id,
                        selectedOptions.toList(),
                        customAnswer.takeIf { it.isNotBlank() }
                    )
                },
                enabled = selectedOptions.isNotEmpty() || customAnswer.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agent_question_submit_button")
            ) {
                Text(
                    text = "Send Response to Agent",
                    color = Color(0xFF0D1117),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
