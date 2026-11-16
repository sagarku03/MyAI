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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvisai.assistant.model.ChoiceResponseItem
import com.jarvisai.assistant.model.QuestionnaireQuestion
import com.jarvisai.assistant.model.ToolCall
import com.jarvisai.assistant.model.ToolCallStatus

/**
 * Resolves a human-readable answer summary for a single question from structured fields first,
 * falling back cleanly to toolCall.output lines when viewing older persisted messages.
 */
private fun resolveQuestionAnswerText(
    questionItem: QuestionnaireQuestion,
    fallbackOutput: String,
    isSkipped: Boolean
): String {
    val labelParts = questionItem.selectedOptionIds.map { sel ->
        val matched = questionItem.options.find {
            it.resolvedOptionId() == sel || it.resolvedDisplayText() == sel
        }
        matched?.resolvedDisplayText() ?: sel
    }.filter { it.isNotBlank() }.toMutableList()

    if (questionItem.customAnswer.isNotBlank()) {
        labelParts.add(questionItem.customAnswer.trim())
    }
    if (labelParts.isNotEmpty()) {
        return labelParts.joinToString(", ")
    }

    if (isSkipped) return "Skipped"

    // Fallback parse from output format: "- <question>: <answer>"
    val outputLines = fallbackOutput.lines()
    val matchingLine = outputLines.firstOrNull { line ->
        val cleaned = line.trim().removePrefix("- ").trim()
        cleaned.startsWith(questionItem.question.take(24), ignoreCase = true) && cleaned.contains(":")
    }
    if (matchingLine != null) {
        val afterColon = matchingLine.substringAfter(":").trim()
        if (afterColon.isNotBlank()) return afterColon
    }
    return fallbackOutput.trim().ifBlank { "Confirmed" }
}

/**
 * In-stream status & summary card for the `ask_question` tool call.
 * - While waiting: compact dark card ("Waiting for your answer") with bulleted questions and pointer
 *   to the interactive panel docked directly above the message box.
 * - Once submitted/skipped: green-bordered "Preferences Submitted" summary card showing every Q & A pair.
 */
@Composable
fun ChoiceQuestionnaireCard(
    toolCall: ToolCall,
    onSubmit: (toolCall: ToolCall, responses: List<ChoiceResponseItem>) -> Unit = { _, _ -> },
    onSkip: (toolCall: ToolCall) -> Unit = {},
    onTapPrompt: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val questionnaire = toolCall.questionnaire ?: return
    val isCompleted = toolCall.status == ToolCallStatus.SUCCESS
    val isSkipped = isCompleted && toolCall.output.startsWith("Skipped", ignoreCase = true)

    if (!isCompleted) {
        // Pending card: "Waiting for your answer"
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = onTapPrompt != null) { onTapPrompt?.invoke() }
                .testTag("choice_questionnaire_card_${toolCall.id}"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1119),
            border = BorderStroke(1.dp, Color(0xFF263042))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = "Waiting for your answer",
                        tint = Color(0xFF4C8DF6),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Waiting for your answer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFF8FAFC)
                    )
                }

                Column(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    questionnaire.questions.forEach { qItem ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "•",
                                fontSize = 13.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = qItem.question,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFFA1A1AA)
                            )
                        }
                    }
                }

                Text(
                    text = "Answer in the panel above the message box.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B8AFF)
                )
            }
        }
    } else {
        // Completed card: green-bordered "Preferences Submitted"
        val borderColor = if (isSkipped) Color(0xFF475569) else Color(0xFF1E8E5A)
        val iconColor = if (isSkipped) Color(0xFF94A3B8) else Color(0xFF22C55E)

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .testTag("choice_questionnaire_card_${toolCall.id}"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1119),
            border = BorderStroke(1.2.dp, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isSkipped) Icons.Outlined.Close else Icons.Default.CheckCircle,
                        contentDescription = if (isSkipped) "Preferences Skipped" else "Preferences Submitted",
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isSkipped) "Preferences Skipped" else "Preferences Submitted",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFF8FAFC)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF131823),
                    border = BorderStroke(1.dp, Color(0xFF1E2634)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        questionnaire.questions.forEachIndexed { idx, qItem ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    thickness = 1.dp,
                                    color = Color(0xFF1E2634)
                                )
                            }
                            val answerDisplay = resolveQuestionAnswerText(
                                questionItem = qItem,
                                fallbackOutput = toolCall.output,
                                isSkipped = isSkipped
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Q:",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF60A5FA)
                                    )
                                    Text(
                                        text = qItem.question,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "↳ A:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = iconColor
                                    )
                                    Text(
                                        text = answerDisplay,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp,
                                        color = Color(0xFFF1F5F9)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive stepper panel docked directly above the message composer bar when an active
 * `ask_question` tool call is awaiting user input.
 */
@Composable
fun AskQuestionBottomPanel(
    toolCall: ToolCall,
    onSubmit: (toolCall: ToolCall, responses: List<ChoiceResponseItem>) -> Unit,
    onSkip: (toolCall: ToolCall) -> Unit,
    modifier: Modifier = Modifier
) {
    val questionnaire = toolCall.questionnaire ?: return
    val questions = questionnaire.questions
    if (questions.isEmpty()) return

    var currentIndex by remember(toolCall.id) { mutableIntStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, questions.lastIndex)
    val currentQuestion = questions[safeIndex]

    // Use immutable Sets as map values so every selection triggers instant Compose recomposition
    val selectedOptions = remember(toolCall.id) { mutableStateMapOf<String, Set<String>>() }
    val customAnswers = remember(toolCall.id) { mutableStateMapOf<String, String>() }

    fun qKey(index: Int, q: QuestionnaireQuestion): String = "${index}_${q.question}"

    val currentKey = qKey(safeIndex, currentQuestion)
    val selectedForCurrent = selectedOptions[currentKey] ?: emptySet()
    val customForCurrent = customAnswers[currentKey] ?: ""
    val hasAnswerForCurrent = selectedForCurrent.isNotEmpty() || customForCurrent.isNotBlank()
    val isLastQuestion = safeIndex >= questions.lastIndex

    val anyAnswered = questions.indices.any { idx ->
        val k = qKey(idx, questions[idx])
        (selectedOptions[k]?.isNotEmpty() == true) || (!customAnswers[k].isNullOrBlank())
    }

    fun dispatchAllResponses() {
        val responseList = questions.mapIndexed { idx, q ->
            val k = qKey(idx, q)
            ChoiceResponseItem(
                question = q.question,
                selectedOptionIds = selectedOptions[k]?.toList() ?: emptyList(),
                writeInResponse = customAnswers[k]?.trim()?.takeIf { it.isNotBlank() }
            )
        }
        onSubmit(toolCall, responseList)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("ask_question_bottom_panel_${toolCall.id}"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0E131D),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.2.dp, Color(0xFF263145))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header row: Badge + Title + Step Indicators + Dismiss/Skip X button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF17253B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HelpOutline,
                        contentDescription = "Clarifying question",
                        tint = Color(0xFF4C8DF6),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (questions.size == 1) "Antigravity has 1 question" else "Antigravity has ${questions.size} questions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        questions.indices.forEach { idx ->
                            val k = qKey(idx, questions[idx])
                            val isAnsweredIdx = (selectedOptions[k]?.isNotEmpty() == true) || (!customAnswers[k].isNullOrBlank())
                            val isActive = idx == safeIndex
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (isActive) 18.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isActive -> Color(0xFF3B82F6)
                                            isAnsweredIdx -> Color(0xFF22C55E)
                                            else -> Color(0xFF334155)
                                        }
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${safeIndex + 1} of ${questions.size}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                IconButton(
                    onClick = { onSkip(toolCall) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("questionnaire_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Skip all questions",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current Question Prompt
            Text(
                text = currentQuestion.question,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = Color(0xFFF8FAFC)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (currentQuestion.isMultiSelect) "Choose one or more" else "Choose one",
                fontSize = 12.5.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Scrollable options + optional write-in field
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                currentQuestion.options.forEach { opt ->
                    val optionId = opt.resolvedOptionId()
                    val optionDisplayText = opt.resolvedDisplayText()
                    val isChecked = selectedForCurrent.contains(optionId)

                    val borderColor by animateColorAsState(
                        targetValue = if (isChecked) Color(0xFF3B82F6) else Color(0xFF232C3D),
                        label = "panel_option_border"
                    )
                    val containerColor by animateColorAsState(
                        targetValue = if (isChecked) Color(0xFF16243B) else Color(0xFF121824),
                        label = "panel_option_bg"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(containerColor)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                if (currentQuestion.isMultiSelect) {
                                    selectedOptions[currentKey] = if (isChecked) {
                                        selectedForCurrent - optionId
                                    } else {
                                        selectedForCurrent + optionId
                                    }
                                } else {
                                    selectedOptions[currentKey] = setOf(optionId)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentQuestion.isMultiSelect) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF3B82F6),
                                    uncheckedColor = Color(0xFF64748B),
                                    checkmarkColor = Color.White
                                )
                            )
                        } else {
                            RadioButton(
                                selected = isChecked,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF3B82F6),
                                    unselectedColor = Color(0xFF64748B)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = optionDisplayText,
                                fontSize = 14.sp,
                                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isChecked) Color.White else Color(0xFFE2E8F0)
                            )
                            if (opt.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = opt.description,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customForCurrent,
                    onValueChange = { customAnswers[currentKey] = it },
                    placeholder = {
                        Text(
                            text = "Or type your own answer...",
                            fontSize = 12.5.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF232C3D),
                        focusedContainerColor = Color(0xFF121824),
                        unfocusedContainerColor = Color(0xFF121824)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer action row: Skip all (left) + Back / Skip question / Next / Submit (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onSkip(toolCall) },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("questionnaire_skip_all_button")
                ) {
                    Text(
                        text = "Skip all",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (safeIndex > 0) {
                        OutlinedButton(
                            onClick = { currentIndex = (safeIndex - 1).coerceAtLeast(0) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E3A4F)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = "Back",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val primaryLabel = when {
                        !isLastQuestion && !hasAnswerForCurrent -> "Skip question"
                        !isLastQuestion && hasAnswerForCurrent -> "Next"
                        isLastQuestion && (hasAnswerForCurrent || anyAnswered) -> "Submit"
                        else -> "Skip question"
                    }
                    val primaryTag = when {
                        primaryLabel == "Submit" -> "questionnaire_submit_button"
                        primaryLabel == "Skip question" -> "questionnaire_skip_button"
                        else -> "questionnaire_next_button"
                    }

                    Button(
                        onClick = {
                            if (!isLastQuestion) {
                                currentIndex = (safeIndex + 1).coerceAtMost(questions.lastIndex)
                            } else if (hasAnswerForCurrent || anyAnswered) {
                                dispatchAllResponses()
                            } else {
                                onSkip(toolCall)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag(primaryTag)
                    ) {
                        Text(
                            text = primaryLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
