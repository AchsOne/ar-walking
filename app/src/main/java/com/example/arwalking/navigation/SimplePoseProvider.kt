package com.example.arwalking.navigation

import android.util.Log

/**
 * Simple PoseProvider implementation for testing
 * In the future, this can be replaced with ARCore or sensor-based implementations
 */
class SimplePoseProvider : PoseProvider {
    
    companion object {
        private const val TAG = "SimplePoseProvider"
    }
    
    private var isStarted = false
    private var currentPose = PoseProvider.Pose()
    
    override fun start() {
        isStarted = true
        Log.d(TAG, "SimplePoseProvider started")
    }
    
    override fun stop() {
        isStarted = false
        Log.d(TAG, "SimplePoseProvider stopped")
    }
    
    override fun current(): PoseProvider.Pose {
        return if (isStarted) {
            currentPose
        } else {
            PoseProvider.Pose()
        }
    }
    
    // For testing - allows manual pose updates
    fun updatePose(position: Vec3, yaw: Float, velocity: Float = 0f, quality: Float = 0.8f) {
        currentPose = PoseProvider.Pose(
            positionWorld = position,
            yaw = yaw,
            velocity = velocity,
            quality = quality
        )
        Log.d(TAG, "Pose updated: pos=(${position.x}, ${position.y}, ${position.z}), yaw=$yaw°")
    }
}