package com.example.arwalking.storage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * Stub-Implementation des Storage-Managers
 * Verhindert Crashes durch fehlende Klasse
 */
class ArWalkingStorageManager(private val context: Context) {
    
    private val TAG = "ArWalkingStorageManager"
    
    init {
        Log.i(TAG, "ArWalkingStorageManager initialized (stub)")
    }
    
    fun saveImage(
        bitmap: Bitmap,
        landmarkId: String,
        landmarkName: String,
        description: String,
        category: String
    ): SaveResult {
        Log.d(TAG, "saveImage called (stub): $landmarkId")
        return SaveResult.Success("Stub implementation")
    }
    
    fun loadFullImage(landmarkId: String): Bitmap? {
        Log.d(TAG, "loadFullImage called (stub): $landmarkId")
        return null
    }
    
    fun loadThumbnail(landmarkId: String): Bitmap? {
        Log.d(TAG, "loadThumbnail called (stub): $landmarkId")
        return null
    }
    
    fun getAvailableProjectLandmarks(): List<ProjectLandmark> {
        Log.d(TAG, "getAvailableProjectLandmarks called (stub)")
        return emptyList()
    }
    
    fun getStorageStatus(): StorageStatus {
        Log.d(TAG, "getStorageStatus called (stub)")
        return StorageStatus()
    }
    
    fun logPerformanceSummary() {
        Log.d(TAG, "logPerformanceSummary called (stub)")
    }
    
    fun deleteLandmark(landmarkId: String): Boolean {
        Log.d(TAG, "deleteLandmark called (stub): $landmarkId")
        return true
    }
    
    fun cleanup(): CleanupSummary {
        Log.d(TAG, "cleanup called (stub)")
        return CleanupSummary()
    }
}

sealed class SaveResult {
    data class Success(val message: String) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

data class ProjectLandmark(
    val id: String,
    val filename: String
)

class StorageStatus {
    val totalImages: Int = 0
    val cacheHitRate: Double = 0.0
    val averageLoadTimeMs: Double = 0.0
    
    fun getHealthStatus(): String = "OK (stub)"
}

class CleanupSummary {
    val removedFiles: Int = 0
}