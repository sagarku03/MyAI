package com.jarvisai.assistant.service

import android.content.Context
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SwarmDroneReport(
    val droneId: String,
    val droneRole: String,
    val latencyMs: Long,
    val findings: String
)

data class SwarmExecutionBundle(
    val missionGoal: String,
    val totalWallClockMs: Long,
    val droneReports: List<SwarmDroneReport>,
    val synthesizedHinglishBriefing: String
)

/**
 * Feature 9: Swarm Multi-Agent Parallel Execution Coordinator.
 * Inspired by AbuZar-Ansarii/Ultron Parallel Sub-Agent Drone Swarms:
 * Spawns 3 concurrent Kotlin Coroutine Sub-Agents (Drone Alpha, Drone Beta, Drone Gamma)
 * in parallel to inspect hardware/network, active UI/screen context, and cognitive memory
 * or shopping comparison data simultaneously, merging results onto WorkingMemoryBlackboard.
 */
object SwarmParallelCoordinator {

    private val _lastSwarmBundle = MutableStateFlow<SwarmExecutionBundle?>(null)
    val lastSwarmBundle: StateFlow<SwarmExecutionBundle?> = _lastSwarmBundle.asStateFlow()

    suspend fun executeParallelSwarmMission(
        context: Context,
        missionQuery: String
    ): SwarmExecutionBundle = coroutineScope {
        val startMs = System.currentTimeMillis()
        ProceduralHudModeState.setMode(ProceduralHudMode.QUANTUM_SEARCH)

        // Drone Alpha: Hardware, Battery, RAM & Cyber Security Telemetry
        val droneAlphaDeferred = async(Dispatchers.Default) {
            val t0 = System.currentTimeMillis()
            val sys = SystemStatusManager(context)
            val bat = sys.getBatteryInfo()
            val net = sys.getNetworkInfo()
            val ram = sys.getStorageAndRamInfo()
            SwarmDroneReport(
                droneId = "DRONE-ALPHA",
                droneRole = "Hardware & Network Telemetry",
                latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                findings = "Battery ${bat.levelPercent}% (${bat.chargePlug}), Net ${net.transportType}, Free RAM ${ram.availableRamMb}MB"
            )
        }

        // Drone Beta: Active Screen DOM & Set-of-Marks Visual Context
        val droneBetaDeferred = async(Dispatchers.Default) {
            val t0 = System.currentTimeMillis()
            val snap = JarvisAccessibilityService.getScreenContext(context)
            val topLabels = snap.elements.take(8).mapNotNull {
                it.text.ifBlank { it.contentDescription }.takeIf { s -> s.isNotBlank() }
            }
            SwarmDroneReport(
                droneId = "DRONE-BETA",
                droneRole = "Screen DOM & UI Inspector",
                latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                findings = "Active Package: ${snap.activePackageName.ifBlank { "Launcher" }} | Visible Elements (${snap.elements.size}): ${topLabels.joinToString(", ").ifBlank { "None" }}"
            )
        }

        // Drone Gamma: Cognitive Memory Blackboard & OTP/Notification Sentinel
        val droneGammaDeferred = async(Dispatchers.Default) {
            val t0 = System.currentTimeMillis()
            val otp = JarvisNotificationListenerService.latestHarvestedOtpFlow.value?.otpCode ?: "None"
            val replyables = JarvisNotificationListenerService.activeReplyablesFlow.value.size
            val hotTools = PrometheusToolSynthesizer.hotLoadedTools.value.size
            SwarmDroneReport(
                droneId = "DRONE-GAMMA",
                droneRole = "Memory, Notifications & Prometheus Vault",
                latencyMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                findings = "Pending Notifications: $replyables | Latest OTP: $otp | Hot-Loaded Prometheus Tools: $hotTools"
            )
        }

        val reports = listOf(
            droneAlphaDeferred.await(),
            droneBetaDeferred.await(),
            droneGammaDeferred.await()
        )
        val totalMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        // Write merged swarm intelligence to Tier-2 WorkingMemoryBlackboard
        for (rep in reports) {
            WorkingMemoryBlackboard.putScratchpadVariable(rep.droneId, rep.findings)
        }

        val spoken = "Boss, 3 Parallel Swarm Sub-Agents (${totalMs}ms) ne mission '$missionQuery' complete kar liya hai: " +
            reports.joinToString(" | ") { "${it.droneId}: ${it.findings}" }

        val bundle = SwarmExecutionBundle(
            missionGoal = missionQuery,
            totalWallClockMs = totalMs,
            droneReports = reports,
            synthesizedHinglishBriefing = spoken
        )
        _lastSwarmBundle.value = bundle
        bundle
    }
}
