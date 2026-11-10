package com.jarvisai.assistant.service

import com.jarvisai.assistant.data.local.WorkingMemoryTaskEntity
import com.jarvisai.assistant.model.WorkingTaskPlanSnapshot
import com.jarvisai.assistant.model.WorkingTaskStep
import com.jarvisai.assistant.model.WorkingTaskStepStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tier 2 — Working Memory Blackboard (Executive Task State, Step Tracker & Scratchpad).
 *
 * Holds the active multi-step task goal, ordered steps (PENDING / IN_PROGRESS / DONE / FAILED),
 * intermediate key-value variables (e.g., extracted OTP, copied text, target contact, calculated price),
 * and a 3-frame UI hash ring-buffer for deterministic loop detection.
 */
object WorkingMemoryBlackboard {

    private val scratchpadMap = ConcurrentHashMap<String, String>()
    private val uiHashRingBuffer = CopyOnWriteArrayList<Int>()
    private val stepsList = CopyOnWriteArrayList<WorkingTaskStep>()

    private val _activeTaskPlan = MutableStateFlow(WorkingTaskPlanSnapshot())
    val activeTaskPlan: StateFlow<WorkingTaskPlanSnapshot> = _activeTaskPlan.asStateFlow()

    @Synchronized
    fun startTask(
        rootGoal: String,
        plannedTriples: List<Triple<String, String, String>> = emptyList(),
        clearScratchpad: Boolean = false
    ): WorkingTaskPlanSnapshot {
        val cleanGoal = rootGoal.trim()
        val taskId = "wm_${System.currentTimeMillis()}"
        if (clearScratchpad) {
            scratchpadMap.clear()
        }
        uiHashRingBuffer.clear()
        stepsList.clear()

        if (plannedTriples.isNotEmpty()) {
            plannedTriples.forEachIndexed { idx, triple ->
                val stepTitle = buildString {
                    append(triple.first)
                    if (triple.second.isNotBlank()) append(": ${triple.second}")
                    if (triple.third.isNotBlank()) append(" (${triple.third})")
                }
                stepsList.add(
                    WorkingTaskStep(
                        stepIndex = idx + 1,
                        title = stepTitle,
                        actionType = triple.first,
                        targetParam = triple.second,
                        status = if (idx == 0) WorkingTaskStepStatus.IN_PROGRESS else WorkingTaskStepStatus.PENDING
                    )
                )
            }
        } else if (cleanGoal.isNotBlank()) {
            stepsList.add(
                WorkingTaskStep(
                    stepIndex = 1,
                    title = cleanGoal,
                    actionType = "EXECUTE_GOAL",
                    targetParam = cleanGoal,
                    status = WorkingTaskStepStatus.IN_PROGRESS
                )
            )
        }

        val snapshot = WorkingTaskPlanSnapshot(
            taskId = taskId,
            rootGoal = cleanGoal,
            status = "ACTIVE",
            steps = stepsList.toList(),
            currentStepIndex = 1,
            scratchpadVariables = scratchpadMap.toMap(),
            uiTreeHashHistory = uiHashRingBuffer.toList(),
            isLoopDetected = false,
            obstacleRetryCount = 0,
            updatedAtMs = System.currentTimeMillis()
        )
        _activeTaskPlan.value = snapshot
        return snapshot
    }

    @Synchronized
    fun addCustomStep(title: String, actionType: String = "CUSTOM", targetParam: String = ""): WorkingTaskPlanSnapshot {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return _activeTaskPlan.value
        val nextIdx = stepsList.size + 1
        stepsList.add(
            WorkingTaskStep(
                stepIndex = nextIdx,
                title = cleanTitle,
                actionType = actionType,
                targetParam = targetParam,
                status = WorkingTaskStepStatus.PENDING
            )
        )
        val current = _activeTaskPlan.value
        val updated = current.copy(
            taskId = current.taskId.ifBlank { "wm_${System.currentTimeMillis()}" },
            rootGoal = current.rootGoal.ifBlank { cleanTitle },
            status = if (current.status == "IDLE") "ACTIVE" else current.status,
            steps = stepsList.toList(),
            updatedAtMs = System.currentTimeMillis()
        )
        _activeTaskPlan.value = updated
        return updated
    }

    @Synchronized
    fun markStepInProgress(stepIndex: Int, note: String = "") {
        updateStepStatus(stepIndex, WorkingTaskStepStatus.IN_PROGRESS, note)
    }

    @Synchronized
    fun markStepCompleted(stepIndex: Int, note: String = "") {
        updateStepStatus(stepIndex, WorkingTaskStepStatus.DONE, note)
    }

    @Synchronized
    fun markStepFailed(stepIndex: Int, reason: String = "") {
        updateStepStatus(stepIndex, WorkingTaskStepStatus.FAILED, reason)
    }

    @Synchronized
    private fun updateStepStatus(stepIndex: Int, newStatus: WorkingTaskStepStatus, note: String) {
        val idx = stepsList.indexOfFirst { it.stepIndex == stepIndex }
        if (idx >= 0) {
            val existing = stepsList[idx]
            stepsList[idx] = existing.copy(
                status = newStatus,
                note = note.ifBlank { existing.note }
            )
        }
        val nextActive = stepsList.firstOrNull {
            it.status == WorkingTaskStepStatus.IN_PROGRESS || it.status == WorkingTaskStepStatus.PENDING
        }?.stepIndex ?: stepsList.size

        val allDone = stepsList.isNotEmpty() && stepsList.all { it.status == WorkingTaskStepStatus.DONE }
        val current = _activeTaskPlan.value
        _activeTaskPlan.value = current.copy(
            status = if (allDone) "COMPLETED" else current.status.ifBlank { "ACTIVE" },
            steps = stepsList.toList(),
            currentStepIndex = nextActive,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    @Synchronized
    fun putScratchpadVariable(key: String, value: String) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) return
        scratchpadMap[cleanKey] = value.trim()
        refreshSnapshot()
    }

    fun getScratchpadVariable(key: String): String? {
        return scratchpadMap[key.trim()]
    }

    @Synchronized
    fun put(key: String, value: String) {
        putScratchpadVariable(key, value)
    }

    fun get(key: String): String? {
        return getScratchpadVariable(key)
    }

    fun getAllJson(): JSONObject {
        val snap = _activeTaskPlan.value
        return JSONObject().apply {
            put("taskId", snap.taskId)
            put("rootGoal", snap.rootGoal)
            put("status", snap.status)
            put("currentStepIndex", snap.currentStepIndex)
            put("isLoopDetected", snap.isLoopDetected)
            val scratch = JSONObject()
            scratchpadMap.forEach { (k, v) -> scratch.put(k, v) }
            put("scratchpad", scratch)
        }
    }

    @Synchronized
    fun removeScratchpadVariable(key: String) {
        scratchpadMap.remove(key.trim())
        refreshSnapshot()
    }

    /**
     * Records a UI screen node hash and detects if the agent is stuck on the exact same screen
     * for 3 consecutive steps (UI Loop Detection).
     */
    @Synchronized
    fun recordUiScreenHash(uiHash: Int): Boolean {
        uiHashRingBuffer.add(uiHash)
        while (uiHashRingBuffer.size > 5) {
            uiHashRingBuffer.removeAt(0)
        }
        val size = uiHashRingBuffer.size
        val loopDetected = size >= 3 &&
            uiHashRingBuffer[size - 1] == uiHashRingBuffer[size - 2] &&
            uiHashRingBuffer[size - 2] == uiHashRingBuffer[size - 3]

        val current = _activeTaskPlan.value
        _activeTaskPlan.value = current.copy(
            uiTreeHashHistory = uiHashRingBuffer.toList(),
            isLoopDetected = loopDetected,
            obstacleRetryCount = if (loopDetected) current.obstacleRetryCount + 1 else current.obstacleRetryCount,
            updatedAtMs = System.currentTimeMillis()
        )
        return loopDetected
    }

    @Synchronized
    fun completeActiveTask(summaryNote: String = "") {
        for (i in stepsList.indices) {
            if (stepsList[i].status == WorkingTaskStepStatus.IN_PROGRESS) {
                stepsList[i] = stepsList[i].copy(
                    status = WorkingTaskStepStatus.DONE,
                    note = summaryNote.ifBlank { stepsList[i].note }
                )
            }
        }
        val current = _activeTaskPlan.value
        _activeTaskPlan.value = current.copy(
            status = "COMPLETED",
            steps = stepsList.toList(),
            isLoopDetected = false,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    @Synchronized
    fun failActiveTask(reason: String = "") {
        val current = _activeTaskPlan.value
        _activeTaskPlan.value = current.copy(
            status = "FAILED",
            steps = stepsList.toList(),
            updatedAtMs = System.currentTimeMillis()
        )
        if (reason.isNotBlank()) {
            scratchpadMap["last_failure_reason"] = reason
            refreshSnapshot()
        }
    }

    @Synchronized
    fun clearWorkingMemory() {
        scratchpadMap.clear()
        uiHashRingBuffer.clear()
        stepsList.clear()
        _activeTaskPlan.value = WorkingTaskPlanSnapshot()
    }

    @Synchronized
    fun resetActiveTask() {
        clearWorkingMemory()
    }

    @Synchronized
    private fun refreshSnapshot() {
        val current = _activeTaskPlan.value
        _activeTaskPlan.value = current.copy(
            steps = stepsList.toList(),
            scratchpadVariables = scratchpadMap.toMap(),
            uiTreeHashHistory = uiHashRingBuffer.toList(),
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun toRoomEntity(): WorkingMemoryTaskEntity? {
        val snap = _activeTaskPlan.value
        if (snap.taskId.isBlank() && snap.rootGoal.isBlank() && snap.scratchpadVariables.isEmpty()) {
            return null
        }
        val stepsArr = JSONArray()
        snap.steps.forEach { step ->
            stepsArr.put(
                JSONObject().apply {
                    put("stepIndex", step.stepIndex)
                    put("title", step.title)
                    put("actionType", step.actionType)
                    put("targetParam", step.targetParam)
                    put("status", step.status.name)
                    put("note", step.note)
                }
            )
        }
        val scratchObj = JSONObject()
        snap.scratchpadVariables.forEach { (k, v) -> scratchObj.put(k, v) }

        return WorkingMemoryTaskEntity(
            taskId = snap.taskId.ifBlank { "wm_default" },
            rootGoal = snap.rootGoal.ifBlank { "Scratchpad Session" },
            status = snap.status,
            stepsJson = stepsArr.toString(),
            scratchpadJson = scratchObj.toString(),
            currentStepIndex = snap.currentStepIndex,
            obstacleRetryCount = snap.obstacleRetryCount,
            createdAt = snap.updatedAtMs,
            updatedAt = System.currentTimeMillis()
        )
    }

    @Synchronized
    fun restoreFromRoomEntity(entity: WorkingMemoryTaskEntity): WorkingTaskPlanSnapshot {
        scratchpadMap.clear()
        stepsList.clear()

        runCatching {
            val stepsArr = JSONArray(entity.stepsJson)
            for (i in 0 until stepsArr.length()) {
                val obj = stepsArr.optJSONObject(i) ?: continue
                val statusName = obj.optString("status", WorkingTaskStepStatus.PENDING.name)
                val statusEnum = runCatching { WorkingTaskStepStatus.valueOf(statusName) }
                    .getOrDefault(WorkingTaskStepStatus.PENDING)
                stepsList.add(
                    WorkingTaskStep(
                        stepIndex = obj.optInt("stepIndex", i + 1),
                        title = obj.optString("title", "Step ${i + 1}"),
                        actionType = obj.optString("actionType", "CUSTOM"),
                        targetParam = obj.optString("targetParam", ""),
                        status = statusEnum,
                        note = obj.optString("note", "")
                    )
                )
            }
        }

        runCatching {
            val scratchObj = JSONObject(entity.scratchpadJson)
            val keys = scratchObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                scratchpadMap[k] = scratchObj.optString(k, "")
            }
        }

        val restored = WorkingTaskPlanSnapshot(
            taskId = entity.taskId,
            rootGoal = entity.rootGoal,
            status = entity.status,
            steps = stepsList.toList(),
            currentStepIndex = entity.currentStepIndex,
            scratchpadVariables = scratchpadMap.toMap(),
            uiTreeHashHistory = uiHashRingBuffer.toList(),
            isLoopDetected = false,
            obstacleRetryCount = entity.obstacleRetryCount,
            updatedAtMs = entity.updatedAt
        )
        _activeTaskPlan.value = restored
        return restored
    }

    fun buildWorkingMemoryPromptBlock(): String {
        val snap = _activeTaskPlan.value
        if (snap.rootGoal.isBlank() && snap.scratchpadVariables.isEmpty()) return ""

        return buildString {
            appendLine("[TIER 2 — WORKING MEMORY BLACKBOARD]")
            if (snap.rootGoal.isNotBlank()) {
                appendLine("Active Task Goal: ${snap.rootGoal} [Status: ${snap.status}]")
            }
            if (snap.steps.isNotEmpty()) {
                val formattedSteps = snap.steps.joinToString(" | ") { s ->
                    val icon = when (s.status) {
                        WorkingTaskStepStatus.DONE -> "DONE"
                        WorkingTaskStepStatus.IN_PROGRESS -> "IN_PROGRESS"
                        WorkingTaskStepStatus.FAILED -> "FAILED"
                        WorkingTaskStepStatus.PENDING -> "PENDING"
                    }
                    "Step ${s.stepIndex} ($icon): ${s.title}"
                }
                appendLine("Task Steps: $formattedSteps")
            }
            if (snap.scratchpadVariables.isNotEmpty()) {
                val vars = snap.scratchpadVariables.entries.joinToString(", ") { "${it.key}='${it.value}'" }
                appendLine("Scratchpad Variables: $vars")
            }
            if (snap.isLoopDetected) {
                appendLine("WARNING: UI Loop Detected (same screen hash repeated 3x). Try an alternative navigation path.")
            }
        }.trim()
    }
}
