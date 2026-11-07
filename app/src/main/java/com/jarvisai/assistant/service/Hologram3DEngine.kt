package com.jarvisai.assistant.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class GyroscopeOrientation3D(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val isSensorActive: Boolean = false
)

data class Vec3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class Edge3D(
    val start: Vec3D,
    val end: Vec3D,
    val layerGroup: Int = 0 // 0 = Helmet Outline, 1 = Visor/Eyes, 2 = Chest Arc & Shoulders
)

/**
 * AbuZar-Ansarii/Jarvis (PrivateAgent) Dual 3D Hologram & Gyroscope Engine:
 * Tracks real-time device tilt via TYPE_GAME_ROTATION_VECTOR / TYPE_ACCELEROMETER
 * and projects 3D holographic vertices (Arc Reactor 3D Rings & Translucent Mark-85 Iron Man Bust)
 * onto a 2D Compose Canvas with realistic perspective depth.
 */
object Hologram3DEngine : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var isRegistered = false

    private val _orientation = MutableStateFlow(GyroscopeOrientation3D())
    val orientation: StateFlow<GyroscopeOrientation3D> = _orientation.asStateFlow()

    // Smoothed low-pass filter values
    private var smoothPitch = 0f
    private var smoothRoll = 0f
    private var smoothYaw = 0f

    fun startTracking(context: Context) {
        if (isRegistered) return
        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        val rotSensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (rotSensor != null) {
            isRegistered = sm.registerListener(this, rotSensor, SensorManager.SENSOR_DELAY_GAME)
            _orientation.value = _orientation.value.copy(isSensorActive = isRegistered)
        }
    }

    fun stopTracking() {
        if (!isRegistered) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {
        }
        isRegistered = false
        _orientation.value = _orientation.value.copy(isSensorActive = false)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                val rotMatrix = FloatArray(9)
                val orientationAngles = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientationAngles)
                val yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat().coerceIn(-45f, 45f)
                val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat().coerceIn(-45f, 45f)
                updateSmoothed(pitch, roll, yaw)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values.getOrElse(0) { 0f }
                val ay = event.values.getOrElse(1) { 0f }
                val pitch = ((ay - 5f) * 4.5f).coerceIn(-40f, 40f)
                val roll = (-ax * 5.0f).coerceIn(-40f, 40f)
                updateSmoothed(pitch, roll, smoothYaw)
            }
        }
    }

    private fun updateSmoothed(targetPitch: Float, targetRoll: Float, targetYaw: Float) {
        val alpha = 0.16f
        smoothPitch += alpha * (targetPitch - smoothPitch)
        smoothRoll += alpha * (targetRoll - smoothRoll)
        smoothYaw += alpha * (targetYaw - smoothYaw)
        _orientation.value = GyroscopeOrientation3D(
            pitchDeg = smoothPitch,
            rollDeg = smoothRoll,
            yawDeg = smoothYaw,
            isSensorActive = true
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Rotates a 3D point by (pitchDeg, rollDeg, yawDeg) and projects it onto 2D canvas coordinates
     * with perspective division (camera distance = 3.2f).
     */
    fun project3DPoint(
        point: Vec3D,
        center: Offset,
        scaleRadius: Float,
        pitchDeg: Float,
        rollDeg: Float,
        yawDeg: Float
    ): Pair<Offset, Float> {
        val radPitch = pitchDeg * (PI.toFloat() / 180f)
        val radRoll = rollDeg * (PI.toFloat() / 180f)
        val radYaw = yawDeg * (PI.toFloat() / 180f)

        // 1. Rotate around Y axis (roll / horizontal orbit)
        val cosY = cos(radRoll + radYaw)
        val sinY = sin(radRoll + radYaw)
        val x1 = point.x * cosY + point.z * sinY
        val z1 = -point.x * sinY + point.z * cosY
        val y1 = point.y

        // 2. Rotate around X axis (pitch / vertical tilt)
        val cosX = cos(radPitch)
        val sinX = sin(radPitch)
        val y2 = y1 * cosX - z1 * sinX
        val z2 = y1 * sinX + z1 * cosX

        // 3. Perspective projection
        val fovDist = 3.0f
        val depthFactor = (fovDist / (fovDist - z2.coerceIn(-1.8f, 1.8f))).coerceIn(0.5f, 1.8f)
        val screenX = center.x + x1 * scaleRadius * depthFactor
        val screenY = center.y + y2 * scaleRadius * depthFactor
        return Offset(screenX, screenY) to depthFactor
    }

    /**
     * Pre-computed 3D wireframe mesh for the AbuZar-Ansarii/Jarvis Mark-85 Iron Man Suit Bust Hologram:
     * Includes Helmet Crown, Faceplate Jawline, Glowing Cybernetic Eyes, Neck Collar, Shoulders, and Chest Arc Core.
     */
    val mark85BustWireframeEdges: List<Edge3D> by lazy {
        val edges = mutableListOf<Edge3D>()

        fun addSymmetricalEdge(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, group: Int) {
            edges.add(Edge3D(Vec3D(x1, y1, z1), Vec3D(x2, y2, z2), group))
            if (x1 != 0f || x2 != 0f) {
                edges.add(Edge3D(Vec3D(-x1, y1, z1), Vec3D(-x2, y2, z2), group))
            }
        }

        // Group 0: Helmet Dome, Cheekbones & Jawline (Normalized coordinates -1..+1)
        // Center crest
        edges.add(Edge3D(Vec3D(0f, -0.78f, 0.15f), Vec3D(0f, -0.52f, 0.42f), 0))
        edges.add(Edge3D(Vec3D(0f, -0.52f, 0.42f), Vec3D(0f, -0.28f, 0.50f), 0))
        // Forehead & brow ridge
        addSymmetricalEdge(0f, -0.78f, 0.15f, 0.26f, -0.70f, 0.18f, 0)
        addSymmetricalEdge(0.26f, -0.70f, 0.18f, 0.36f, -0.45f, 0.25f, 0)
        addSymmetricalEdge(0f, -0.52f, 0.42f, 0.28f, -0.46f, 0.36f, 0)
        // Faceplate sides & cheekbones
        addSymmetricalEdge(0.36f, -0.45f, 0.25f, 0.34f, -0.12f, 0.30f, 0)
        addSymmetricalEdge(0.28f, -0.26f, 0.44f, 0.34f, -0.12f, 0.30f, 0)
        addSymmetricalEdge(0.28f, -0.26f, 0.44f, 0.18f, 0.04f, 0.46f, 0)
        // Jawline & chin guard
        addSymmetricalEdge(0.34f, -0.12f, 0.30f, 0.22f, 0.16f, 0.36f, 0)
        addSymmetricalEdge(0.22f, 0.16f, 0.36f, 0.10f, 0.24f, 0.44f, 0)
        edges.add(Edge3D(Vec3D(-0.10f, 0.24f, 0.44f), Vec3D(0.10f, 0.24f, 0.44f), 0))
        edges.add(Edge3D(Vec3D(-0.14f, 0.10f, 0.48f), Vec3D(0.14f, 0.10f, 0.48f), 0))

        // Group 1: Glowing Cybernetic Eyes / Visor Slits
        addSymmetricalEdge(0.06f, -0.34f, 0.48f, 0.24f, -0.37f, 0.42f, 1)
        addSymmetricalEdge(0.24f, -0.37f, 0.42f, 0.26f, -0.31f, 0.42f, 1)
        addSymmetricalEdge(0.26f, -0.31f, 0.42f, 0.08f, -0.29f, 0.49f, 1)
        addSymmetricalEdge(0.08f, -0.29f, 0.49f, 0.06f, -0.34f, 0.48f, 1)

        // Group 2: Neck Armor, Traps, Pauldron Shoulders & Chest Arc Reactor Frame
        addSymmetricalEdge(0.18f, 0.20f, 0.25f, 0.24f, 0.38f, 0.22f, 2)
        addSymmetricalEdge(0.24f, 0.38f, 0.22f, 0.72f, 0.46f, 0.10f, 2)
        addSymmetricalEdge(0.72f, 0.46f, 0.10f, 0.82f, 0.68f, 0.16f, 2)
        addSymmetricalEdge(0.82f, 0.68f, 0.16f, 0.42f, 0.78f, 0.32f, 2)
        addSymmetricalEdge(0.24f, 0.38f, 0.22f, 0.42f, 0.56f, 0.36f, 2)
        // Chest sternum & Arc Reactor Hexagon housing
        edges.add(Edge3D(Vec3D(-0.42f, 0.78f, 0.32f), Vec3D(0.42f, 0.78f, 0.32f), 2))
        val hexRadius = 0.16f
        val chestCenterY = 0.58f
        val chestCenterZ = 0.44f
        for (i in 0 until 6) {
            val a1 = (i * 60f) * (PI.toFloat() / 180f)
            val a2 = ((i + 1) * 60f) * (PI.toFloat() / 180f)
            edges.add(
                Edge3D(
                    Vec3D(cos(a1) * hexRadius, chestCenterY + sin(a1) * hexRadius, chestCenterZ),
                    Vec3D(cos(a2) * hexRadius, chestCenterY + sin(a2) * hexRadius, chestCenterZ),
                    1
                )
            )
        }

        edges
    }
}
