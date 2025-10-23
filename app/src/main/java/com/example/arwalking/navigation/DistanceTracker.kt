package com.example.arwalking.navigation

import android.util.Log
import kotlin.math.sqrt

/**
 * Tracks walking distance using ARCore VIO (Visual-Inertial Odometry)
 * Provides accurate step-by-step distance measurement for route progression
 */
class DistanceTracker {
    companion object {
        private const val TAG = "DistanceTracker"
        private const val MIN_MOVEMENT_THRESHOLD = 0.01f // 1cm minimum movement
        private const val MAX_REALISTIC_STEP = 2.0f      // 2m maximum realistic step (filter noise)
    }
    
    // Position tracking
    private var lastPosition: Vec3? = null
    private var totalDistanceWalked = 0f
    private var currentStepDistanceWalked = 0f
    
    // Statistics
    private var positionUpdates = 0
    private var filteredMovements = 0
    
    /**
     * Update position from ARCore frame and calculate walked distance
     */
    fun updatePosition(currentPos: Vec3) {
        positionUpdates++
        
        lastPosition?.let { lastPos ->
            val stepDistance = calculateDistance(lastPos, currentPos)
            
            // Filter realistic movements only
            if (stepDistance >= MIN_MOVEMENT_THRESHOLD && stepDistance <= MAX_REALISTIC_STEP) {
                totalDistanceWalked += stepDistance
                currentStepDistanceWalked += stepDistance
                filteredMovements++
                
                Log.v(TAG, "📍 Position update: +${String.format("%.3f", stepDistance)}m " +
                          "(step: ${String.format("%.2f", currentStepDistanceWalked)}m, " +
                          "total: ${String.format("%.2f", totalDistanceWalked)}m)")
            } else if (stepDistance > MAX_REALISTIC_STEP) {
                Log.v(TAG, "⚠️ Movement filtered: ${String.format("%.2f", stepDistance)}m > ${MAX_REALISTIC_STEP}m (too large)")
            }
        }
        
        lastPosition = currentPos
    }

    /**
     * Reset distance for new step
     */
    fun resetStepDistance() {
        Log.d(TAG, "🔄 Step distance reset: was ${String.format("%.2f", currentStepDistanceWalked)}m")
        currentStepDistanceWalked = 0f
    }
    
    /**
     * Get current step progress
     */
    fun getStepProgress(): Float = currentStepDistanceWalked
    
    /**
     * Get total walking distance
     */
    fun getTotalDistance(): Float = totalDistanceWalked
    
    /**
     * Get step progress percentage based on expected distance
     */
    fun getStepProgressPercent(expectedDistance: Double): Float {
        return if (expectedDistance > 0) {
            (currentStepDistanceWalked / expectedDistance.toFloat() * 100f).coerceAtMost(999f)
        } else 0f
    }
    
    /**
     * Check if step distance threshold is reached
     */
    fun shouldAdvanceByDistance(expectedDistance: Double, tolerancePercent: Float = 80f): Boolean {
        if (expectedDistance <= 0) return false
        
        val threshold = expectedDistance * (tolerancePercent / 100f)
        val shouldAdvance = currentStepDistanceWalked >= threshold
        
        Log.d(TAG, "📏 Distance check: ${String.format("%.2f", currentStepDistanceWalked)}m / " +
                  "${String.format("%.2f", expectedDistance)}m (${String.format("%.0f", tolerancePercent)}% = " +
                  "${String.format("%.2f", threshold)}m) -> advance: $shouldAdvance")
        
        return shouldAdvance
    }
    
    /**
     * Get statistics for debugging
     */
    fun getStats(): DistanceStats {
        return DistanceStats(
            positionUpdates = positionUpdates,
            filteredMovements = filteredMovements,
            totalDistance = totalDistanceWalked,
            currentStepDistance = currentStepDistanceWalked
        )
    }
    
    /**
     * Calculate 3D distance between two positions
     */
    private fun calculateDistance(pos1: Vec3, pos2: Vec3): Float {
        val dx = pos2.x - pos1.x
        val dy = pos2.y - pos1.y
        val dz = pos2.z - pos1.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Reset all tracking data
     */
    fun reset() {
        Log.d(TAG, "🔄 DistanceTracker reset - was: total=${String.format("%.2f", totalDistanceWalked)}m, " +
                  "step=${String.format("%.2f", currentStepDistanceWalked)}m")
        lastPosition = null
        totalDistanceWalked = 0f
        currentStepDistanceWalked = 0f
        positionUpdates = 0
        filteredMovements = 0
    }
}

/**
 * Statistics data class for debugging
 */
data class DistanceStats(
    val positionUpdates: Int,
    val filteredMovements: Int,
    val totalDistance: Float,
    val currentStepDistance: Float
)
