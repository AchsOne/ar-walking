package com.example.arwalking.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.arwalking.data.ARNavigationConfig
import com.example.arwalking.data.ArrowDirection
import com.example.arwalking.data.TrackingQuality

/**
 * AR State for monitoring AR session status
 */
data class ARState(
    val isSessionActive: Boolean = false,
    val isInitialized: Boolean = false,
    val trackingQuality: TrackingQuality = TrackingQuality.NONE,
    val trackingState: TrackingState = TrackingState.STOPPED,
    val trackingLostDuration: Long = 0,
    val isTrackingLost: Boolean = false,
    val lastError: String? = null,
    val error: String? = null
)

/**
 * Arrow pose information for 3D rendering
 */
data class ArrowPose(
    val position: Vector3,
    val rotation: Quaternion,
    val direction: ArrowDirection = ArrowDirection.FORWARD,
    val scale: Float = 1.0f,
    val isVisible: Boolean = true,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Arrow anchor for AR scene management
 */
data class ArrowAnchor(
    val anchor: Anchor,
    val pose: ArrowPose,
    val direction: ArrowDirection,
    val isInstantPlacement: Boolean = false,
    val creationTime: Long = System.currentTimeMillis()
)

/**
 * ArrowPlacer - Manages ARCore session and 3D arrow placement
 * Handles depth occlusion, pose smoothing, and sticky placement
 */
class ArrowPlacer(
    private val context: Context,
    private val config: ARNavigationConfig
) {

    companion object {
        private const val TAG = "ArrowPlacer"
        private const val SMOOTHING_FACTOR = 0.8f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
        private const val REANCHOR_DISTANCE_THRESHOLD = 2.0f // meters
    }

    private var arSession: Session? = null
    private var isSessionInitialized = false

    private val _arState = MutableStateFlow(ARState())
    val arState: StateFlow<ARState> = _arState.asStateFlow()

    private val _arrowPose = MutableStateFlow<ArrowPose?>(null)
    val arrowPose: StateFlow<ArrowPose?> = _arrowPose.asStateFlow()

    // Arrow management
    private var currentArrow: ArrowAnchor? = null
    private var targetDirection: ArrowDirection = ArrowDirection.FORWARD
    private var lastValidPose: Pose? = null
    private var poseHistory = mutableListOf<Pose>()
    private val maxPoseHistory = 5

    // Tracking quality management
    private var trackingLostTime: Long = 0
    private var lastTrackingState = TrackingState.STOPPED

    /**
     * Initialize ARCore session
     */
    suspend fun initializeAR(): Boolean {
        try {
            Log.d(TAG, "Initializing ARCore session...")

            // Check ARCore availability
            when (ArCoreApk.getInstance().checkAvailability(context)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    Log.d(TAG, "ARCore is supported and installed")
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                    throw UnavailableApkTooOldException()
                }
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    throw UnavailableArcoreNotInstalledException()
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    throw UnavailableDeviceNotCompatibleException()
                }
                else -> {
                    Log.w(TAG, "ARCore availability unknown")
                }
            }

            // Create session
            arSession = Session(context)

            // Configure session
            val sessionConfig = Config(arSession).apply {
                // Enable depth if available
                if (arSession?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true) {
                    depthMode = Config.DepthMode.AUTOMATIC
                    Log.d(TAG, "Depth mode enabled")
                } else {
                    Log.d(TAG, "Depth mode not supported")
                }

                // Configure plane detection
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

                // Enable instant placement if configured
                if (config.arrow.instantPlacement) {
                    instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    Log.d(TAG, "Instant placement enabled")
                }

                // Configure light estimation
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            }

            arSession?.configure(sessionConfig)
            isSessionInitialized = true

            _arState.value = _arState.value.copy(
                isInitialized = true,
                isSessionActive = false,
                trackingQuality = TrackingQuality.NONE
            )

            Log.d(TAG, "ARCore session initialized successfully")
            return true

        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "ARCore not installed", e)
            _arState.value = _arState.value.copy(error = "ARCore not installed")
            return false
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "ARCore APK too old", e)
            _arState.value = _arState.value.copy(error = "ARCore APK too old")
            return false
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "Device not compatible with ARCore", e)
            _arState.value = _arState.value.copy(error = "Device not compatible with ARCore")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ARCore", e)
            _arState.value = _arState.value.copy(error = "Failed to initialize ARCore: ${e.message}")
            return false
        }
    }

    /**
     * Start AR session
     */
    fun startSession(): Boolean {
        val session = arSession ?: return false

        try {
            session.resume()
            _arState.value = _arState.value.copy(isSessionActive = true)
            Log.d(TAG, "AR session started")
            return true
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available", e)
            _arState.value = _arState.value.copy(error = "Camera not available")
            return false
        }
    }

    /**
     * Pause AR session
     */
    fun pauseSession() {
        arSession?.pause()
        _arState.value = _arState.value.copy(isSessionActive = false)
        Log.d(TAG, "AR session paused")
    }

    /**
     * Update AR session with new frame
     */
    fun updateSession(): Frame? {
        val session = arSession ?: return null

        return try {
            val frame = session.update()
            updateTrackingState(frame)
            frame
        } catch (e: Exception) {
            Log.e(TAG, "Error updating AR session", e)
            null
        }
    }

    /**
     * Place or update arrow at the specified location and direction
     */
    fun placeArrow(
        hitResult: HitResult?,
        direction: ArrowDirection,
        forceReplace: Boolean = false
    ): Boolean {
        val session = arSession ?: return false

        targetDirection = direction

        // If we have a current arrow and don't need to force replace, just update direction
        if (currentArrow != null && !forceReplace) {
            updateArrowDirection(direction)
            return true
        }

        // Remove existing arrow
        removeArrow()

        // Place new arrow
        val anchor = when {
            hitResult != null -> {
                // Place at hit result location
                placeArrowAtHitResult(hitResult, direction)
            }
            config.arrow.instantPlacement -> {
                // Use instant placement
                placeArrowWithInstantPlacement(direction)
            }
            else -> {
                Log.w(TAG, "No hit result and instant placement disabled")
                null
            }
        }

        if (anchor != null) {
            currentArrow = anchor
            updateArrowPose(anchor.pose, direction)
            Log.d(TAG, "Arrow placed successfully")
            return true
        }

        return false
    }

    /**
     * Place arrow at hit result location
     */
    private fun placeArrowAtHitResult(hitResult: HitResult, direction: ArrowDirection): ArrowAnchor? {
        return try {
            val anchor = hitResult.createAnchor()
            val pose = calculateArrowPose(anchor.pose, direction)

            ArrowAnchor(
                anchor = anchor,
                pose = pose,
                direction = direction,
                isInstantPlacement = false,
                creationTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place arrow at hit result", e)
            null
        }
    }

    /**
     * Place arrow using instant placement
     */
    private fun placeArrowWithInstantPlacement(direction: ArrowDirection): ArrowAnchor? {
        val session = arSession ?: return null

        return try {
            val frame = session.update()
            val camera = frame.camera
            val cameraPose = camera.pose

            // Place arrow 1 meter in front of camera
            val forwardVector = Vector3(0f, 0f, -1f)
            val worldForward = cameraPose.rotateVector(floatArrayOf(forwardVector.x, forwardVector.y, forwardVector.z))

            val arrowPosition = floatArrayOf(
                cameraPose.tx() + worldForward[0],
                cameraPose.ty() - 0.5f, // Slightly below camera
                cameraPose.tz() + worldForward[2]
            )

            val arrowPose = Pose(arrowPosition, cameraPose.rotation)
            val anchor = session.createAnchor(arrowPose)
            val finalPose = calculateArrowPose(arrowPose, direction)

            ArrowAnchor(
                anchor = anchor,
                pose = finalPose,
                direction = direction,
                isInstantPlacement = true,
                creationTime = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to place arrow with instant placement", e)
            null
        }
    }

    /**
     * Calculate arrow pose based on direction
     */
    private fun calculateArrowPose(basePose: Pose, direction: ArrowDirection): ArrowPose {
        val rotation = when (direction) {
            ArrowDirection.FORWARD -> Quaternion.axisAngle(Vector3.up(), 0f)
            ArrowDirection.LEFT -> Quaternion.axisAngle(Vector3.up(), 90f)
            ArrowDirection.RIGHT -> Quaternion.axisAngle(Vector3.up(), -90f)
            ArrowDirection.BACK -> Quaternion.axisAngle(Vector3.up(), 180f)
            ArrowDirection.UP -> Quaternion.axisAngle(Vector3.right(), -90f)
            ArrowDirection.DOWN -> Quaternion.axisAngle(Vector3.right(), 90f)
            ArrowDirection.NONE -> Quaternion.axisAngle(Vector3.up(), 0f)
        }

        // Apply offset from config
        val offset = config.arrow.offset
        val offsetVector = floatArrayOf(offset.first, offset.second, offset.third)
        val worldOffset = basePose.rotateVector(offsetVector)

        val finalPosition = floatArrayOf(
            basePose.tx() + worldOffset[0],
            basePose.ty() + worldOffset[1],
            basePose.tz() + worldOffset[2]
        )

        // Combine base rotation with direction rotation
        val baseQuaternion = basePose.rotation
        val finalRotation = Quaternion(
            baseQuaternion[3] * rotation.w - baseQuaternion[0] * rotation.x - baseQuaternion[1] * rotation.y - baseQuaternion[2] * rotation.z,
            baseQuaternion[3] * rotation.x + baseQuaternion[0] * rotation.w + baseQuaternion[1] * rotation.z - baseQuaternion[2] * rotation.y,
            baseQuaternion[3] * rotation.y - baseQuaternion[0] * rotation.z + baseQuaternion[1] * rotation.w + baseQuaternion[2] * rotation.x,
            baseQuaternion[3] * rotation.z + baseQuaternion[0] * rotation.y - baseQuaternion[1] * rotation.x + baseQuaternion[2] * rotation.w
        )

        return ArrowPose(
            position = Vector3(finalPosition[0], finalPosition[1], finalPosition[2]),
            rotation = finalRotation,
            direction = direction,
            scale = config.arrow.scale,
            isVisible = true,
            confidence = 1.0f,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Update arrow direction without changing position
     */
    private fun updateArrowDirection(direction: ArrowDirection) {
        val arrow = currentArrow ?: return

        if (arrow.direction != direction) {
            val anchorPose = arrow.anchor.pose
            val basePose = Pose(
                floatArrayOf(anchorPose.tx(), anchorPose.ty(), anchorPose.tz()),
                anchorPose.rotation
            )
            val newPose = calculateArrowPose(basePose, direction)
            val updatedArrow = arrow.copy(
                pose = newPose,
                direction = direction
            )
            currentArrow = updatedArrow
            updateArrowPose(newPose, direction)
        }
    }

    /**
     * Update arrow pose with smoothing
     */
    private fun updateArrowPose(pose: ArrowPose, direction: ArrowDirection) {
        val poseObject = Pose(
            floatArrayOf(pose.position.x, pose.position.y, pose.position.z),
            floatArrayOf(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w)
        )

        val smoothedPose = if (lastValidPose != null && poseHistory.isNotEmpty()) {
            smoothPose(poseObject, lastValidPose!!)
        } else {
            poseObject
        }

        // Add to pose history
        poseHistory.add(smoothedPose)
        if (poseHistory.size > maxPoseHistory) {
            poseHistory.removeAt(0)
        }

        lastValidPose = smoothedPose

        _arrowPose.value = ArrowPose(
            position = Vector3(smoothedPose.tx(), smoothedPose.ty(), smoothedPose.tz()),
            rotation = Quaternion(
                smoothedPose.rotation[3], // w
                smoothedPose.rotation[0], // x
                smoothedPose.rotation[1], // y
                smoothedPose.rotation[2]  // z
            ),
            direction = direction,
            scale = config.arrow.scale,
            isVisible = true,
            confidence = calculatePoseConfidence()
        )
    }

    /**
     * Smooth pose to reduce jitter
     */
    private fun smoothPose(newPose: Pose, lastPose: Pose): Pose {
        // Linear interpolation for position
        val smoothedPosition = floatArrayOf(
            lastPose.tx() * SMOOTHING_FACTOR + newPose.tx() * (1 - SMOOTHING_FACTOR),
            lastPose.ty() * SMOOTHING_FACTOR + newPose.ty() * (1 - SMOOTHING_FACTOR),
            lastPose.tz() * SMOOTHING_FACTOR + newPose.tz() * (1 - SMOOTHING_FACTOR)
        )

        // Spherical linear interpolation for rotation
        val lastQuat = Quaternion(
            lastPose.rotation[3], lastPose.rotation[0], lastPose.rotation[1], lastPose.rotation[2]
        )
        val newQuat = Quaternion(
            newPose.rotation[3], newPose.rotation[0], newPose.rotation[1], newPose.rotation[2]
        )

        val smoothedQuat = Quaternion.slerp(lastQuat, newQuat, 1 - SMOOTHING_FACTOR)
        val smoothedRotation = floatArrayOf(smoothedQuat.x, smoothedQuat.y, smoothedQuat.z, smoothedQuat.w)

        return Pose(smoothedPosition, smoothedRotation)
    }

    /**
     * Calculate pose confidence based on tracking quality and history
     */
    private fun calculatePoseConfidence(): Float {
        val trackingQuality = _arState.value.trackingQuality
        val baseConfidence = when (trackingQuality) {
            TrackingQuality.NORMAL -> 1.0f
            TrackingQuality.SUFFICIENT -> 0.7f
            TrackingQuality.INSUFFICIENT -> 0.3f
            TrackingQuality.NONE -> 0.0f
        }

        // Reduce confidence if pose history is inconsistent
        val historyConsistency = if (poseHistory.size >= 2) {
            val distances = poseHistory.zipWithNext { a, b ->
                val dx = a.tx() - b.tx()
                val dy = a.ty() - b.ty()
                val dz = a.tz() - b.tz()
                kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
            }
            val avgDistance = distances.average().toFloat()
            (1.0f - (avgDistance * 10).coerceAtMost(1.0f)).coerceAtLeast(0.0f)
        } else {
            1.0f
        }

        return baseConfidence * historyConsistency
    }

    /**
     * Remove current arrow
     */
    fun removeArrow() {
        currentArrow?.anchor?.detach()
        currentArrow = null
        lastValidPose = null
        poseHistory.clear()

        _arrowPose.value = _arrowPose.value?.copy(isVisible = false)

        Log.d(TAG, "Arrow removed")
    }

    /**
     * Update tracking state
     */
    private fun updateTrackingState(frame: Frame) {
        val camera = frame.camera
        val trackingState = camera.trackingState

        val trackingQuality = when (trackingState) {
            TrackingState.TRACKING -> {
                when (camera.trackingFailureReason) {
                    TrackingFailureReason.NONE -> TrackingQuality.NORMAL
                    TrackingFailureReason.BAD_STATE -> TrackingQuality.INSUFFICIENT
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> TrackingQuality.INSUFFICIENT
                    TrackingFailureReason.EXCESSIVE_MOTION -> TrackingQuality.INSUFFICIENT
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> TrackingQuality.INSUFFICIENT
                    else -> TrackingQuality.SUFFICIENT
                }
            }
            TrackingState.PAUSED -> TrackingQuality.INSUFFICIENT
            TrackingState.STOPPED -> TrackingQuality.NONE
        }

        // Track when tracking was lost
        if (trackingState != TrackingState.TRACKING && lastTrackingState == TrackingState.TRACKING) {
            trackingLostTime = System.currentTimeMillis()
        }

        lastTrackingState = trackingState

        _arState.value = _arState.value.copy(
            trackingQuality = trackingQuality,
            trackingState = trackingState,
            trackingLostDuration = if (trackingState != TrackingState.TRACKING && trackingLostTime > 0) {
                System.currentTimeMillis() - trackingLostTime
            } else 0
        )

        // Handle re-anchoring if tracking was lost for too long
        if (trackingQuality == TrackingQuality.NORMAL &&
            _arState.value.trackingLostDuration > 2000 &&
            currentArrow != null) {

            Log.d(TAG, "Re-anchoring arrow after tracking recovery")
            // Could implement re-anchoring logic here
        }
    }

    /**
     * Perform hit test for arrow placement
     */
    fun hitTest(frame: Frame, x: Float, y: Float): List<HitResult> {
        return try {
            frame.hitTest(x, y).filter { hitResult ->
                val trackable = hitResult.trackable
                trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hit test failed", e)
            emptyList()
        }
    }

    /**
     * Get current AR state
     */
    fun getARState(): ARState {
        return _arState.value
    }

    /**
     * Release resources
     */
    fun release() {
        removeArrow()
        arSession?.close()
        arSession = null
        isSessionInitialized = false

        _arState.value = ARState()
        _arrowPose.value = null

        Log.d(TAG, "ArrowPlacer resources released")
    }
}