package com.example.arwalking.managers

import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.example.arwalking.navigation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages ARCore position tracking, map matching, and arrow state updates.
 * Extracted from RouteViewModel for better separation of concerns.
 */
class PositionTrackingManager {
    private companion object {
        const val TAG = "PositionTrackingManager"
    }
    
    // State flows for position tracking
    private val _userProgress = MutableStateFlow(0f)
    val userProgress: StateFlow<Float> = _userProgress.asStateFlow()
    
    private val _distanceToNext = MutableStateFlow(Float.MAX_VALUE)
    val distanceToNext: StateFlow<Float> = _distanceToNext.asStateFlow()
    
    private val _arrowState = MutableStateFlow(ArrowController.ArrowState())
    val arrowState: StateFlow<ArrowController.ArrowState> = _arrowState.asStateFlow()
    
    // Navigation components
    private var poseProvider: PoseProvider? = null
    private var mapMatcher: MapMatcher? = null
    private var arrowController: ArrowController? = null
    
    /**
     * Initialize navigation components with route path
     */
    suspend fun initializeComponents(routePath: RoutePath) {
        try {
            poseProvider = SimplePoseProvider().apply { start() }
            mapMatcher = MapMatcherImpl(routePath)
            arrowController = ArrowControllerImpl(routePath)
            Log.d(TAG, "Navigation components initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize navigation components", e)
        }
    }
    
    /**
     * Update position based on ARCore frame - handles pose tracking, map matching, and arrow updates
     */
    suspend fun updatePosition(
        frame: Frame,
        currentStep: Int,
        currentRoute: com.example.arwalking.NavigationRoute?,
        hasLandmarkMatches: Boolean
    ) {
        // Get current pose from ARCore
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            Log.w(TAG, "Camera not tracking: ${camera.trackingState}")
            return
        }
        
        val pose = camera.pose
        val worldPosition = Vec3(
            pose.translation[0],
            pose.translation[1], 
            pose.translation[2]
        )
        
        // Convert ARCore quaternion to yaw angle
        val quat = pose.rotationQuaternion
        val yaw = Math.toDegrees(Math.atan2(
            2.0 * (quat[3] * quat[1] + quat[0] * quat[2]),
            1.0 - 2.0 * (quat[1] * quat[1] + quat[2] * quat[2])
        )).toFloat()
        
        // Update pose provider for testing
        (poseProvider as? SimplePoseProvider)?.updatePose(worldPosition, yaw)
        
        val routePathExists = arrowController != null && mapMatcher != null
        if (routePathExists && mapMatcher != null && arrowController != null) {
            // Update map matching
            val matchResult = mapMatcher!!.project(worldPosition)
            _userProgress.value = matchResult.s
            _distanceToNext.value = matchResult.distanceToNextManeuver
            Log.v(TAG, "Map matching: progress=${matchResult.s}, distance=${matchResult.distanceToNextManeuver}")
            
            // Update arrow state
            val instruction = if (currentRoute != null && currentStep < currentRoute.steps.size) {
                currentRoute.steps[currentStep].instruction
            } else ""
            
            val speed = 1.0f // Default walking speed
            
            val arrowState = arrowController!!.update(
                matchResult,
                yaw,
                instruction,
                hasLandmarkMatches,
                speed
            )
            _arrowState.value = arrowState
        }
    }
    
    /**
     * Get current user progress for route calculations
     */
    fun getCurrentProgress(): Float = _userProgress.value
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        poseProvider = null
        mapMatcher = null
        arrowController = null
    }
}