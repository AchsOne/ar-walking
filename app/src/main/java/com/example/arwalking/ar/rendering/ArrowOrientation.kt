package com.example.arwalking.ar.rendering

import com.google.ar.sceneform.Camera
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3

/**
 * Centralized helpers for arrow direction and placement (green and blue arrows).
 */
object ArrowOrientation {
    // Configuration constants
    private const val BLUE_DISTANCE_M = 1.0f
    private const val BLUE_Y_OFFSET_M = -0.5f
    private const val GREEN_DISTANCE_M = 2.0f
    private const val GREEN_Y_OFFSET_M = -0.5f

    // Pre-compiled regex for better performance
    private val HTML_TAG_REGEX = Regex("</?b>")

    // Rotation cache for frequently used angles
    private val rotationCache = mutableMapOf<Float, Quaternion>()
    private val blueRotationCache = mutableMapOf<Float, Quaternion>()

    // Pre-computed base rotation for blue arrow - adjust pitch so it stands vertically (upwards)
    private val BLUE_BASE_ROTATION = Quaternion.axisAngle(Vector3(1f, 0f, 0f), -90f)

    // Instruction patterns with associated yaw values
    private val INSTRUCTION_PATTERNS = listOf(
        "scharf links" to -120f,
        "scharf rechts" to 120f,
        "sharp left" to -120f,
        "sharp right" to 120f,
        "biegen sie links ab" to -90f,
        "biegen sie rechts ab" to 90f,
        "links ab" to -90f,
        "rechts ab" to 90f,
        "turn left" to -90f,
        "turn right" to 90f,
        "leicht links" to -45f,
        "leicht rechts" to 45f,
        "slight left" to -45f,
        "slight right" to 45f,
        "umkehren" to 180f,
        "u-turn" to 180f
    )

    private val FORWARD_KEYWORDS = setOf(
        "geradeaus", "straight", "durch", "through",
        "tür", "door", "verlassen", "leave", "treppe", "stairs"
    )

    /**
     * Calculates yaw angle from navigation instruction text.
     * Returns inverted angle for ARCore coordinate system.
     */
    fun calculateYawFromInstruction(instruction: String): Float {
        val cleanInstruction = instruction.replace(HTML_TAG_REGEX, "").lowercase()

        // Check specific patterns first (order matters for specificity)
        for ((pattern, yaw) in INSTRUCTION_PATTERNS) {
            if (cleanInstruction.contains(pattern)) {
                return -yaw // ARCore Y-rotation inverted
            }
        }

        // Check forward keywords
        if (FORWARD_KEYWORDS.any { cleanInstruction.contains(it) }) {
            return 0f
        }

        return 0f // Default: straight ahead
    }

    /**
     * Combines camera yaw with instruction yaw to get world-space rotation.
     */
    fun worldYaw(cameraYaw: Float, instructionYaw: Float): Float =
        cameraYaw + instructionYaw

    /**
     * Creates rotation quaternion for green arrow (flat on ground).
     * Uses caching for performance.
     */
    fun greenRotation(yawDeg: Float): Quaternion =
        rotationCache.getOrPut(yawDeg) {
            Quaternion.axisAngle(Vector3(0f, 1f, 0f), yawDeg)
        }

    /**
     * Creates rotation quaternion for blue arrow.
     * Aligns identically to green (yaw-only) to avoid pitching into ground.
     */
    fun blueRotation(yawDeg: Float): Quaternion =
        blueRotationCache.getOrPut(yawDeg) {
            // Yaw-only; geometry is made upright via local rotation in ArrowAbbiegen
            Quaternion.axisAngle(Vector3(0f, 1f, 0f), yawDeg)
        }

    /**
     * Calculates world position for blue arrow relative to camera.
     * Places arrow in front of camera with vertical offset.
     */
    fun bluePosition(camera: Camera): Vector3 {
        val camPos = camera.worldPosition
        val fwd = camera.forward

        return Vector3(
            camPos.x + fwd.x * BLUE_DISTANCE_M,
            camPos.y + BLUE_Y_OFFSET_M,
            camPos.z + fwd.z * BLUE_DISTANCE_M
        )
    }

    /**
     * Calculates fixed world position for blue arrow based on anchor position.
     * Used to place the arrow at a fixed location that doesn't move with device movement.
     */
    fun bluePositionFixed(anchorWorldPos: Vector3): Vector3 {
        return Vector3(
            anchorWorldPos.x,
            anchorWorldPos.y + 0.20f, // Slightly higher for better visibility
            anchorWorldPos.z
        )
    }

    fun greenPosition(camera: Camera): Vector3 {
        val camPos = camera.worldPosition
        val fwd = camera.forward

        return Vector3(
            camPos.x + fwd.x * GREEN_DISTANCE_M,
            camPos.y + GREEN_Y_OFFSET_M,
            camPos.z + fwd.z * GREEN_DISTANCE_M
        )
    }

    /**
     * Clears rotation caches. Call when memory optimization is needed.
     */
    fun clearCache() {
        rotationCache.clear()
        blueRotationCache.clear()
    }
}
