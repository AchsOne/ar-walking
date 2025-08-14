package com.example.arwalking.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.io.IOException
import com.example.arwalking.data.ARNavigationConfig

/**
 * LandmarkStore - Manages landmark reference images and their loading
 * Handles image loading, preprocessing, and caching
 */
class LandmarkStore(
    private val context: Context,
    private val config: ARNavigationConfig
) {
    
    companion object {
        private const val TAG = "LandmarkStore"
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp")
    }
    
    private val landmarkImages = mutableMapOf<String, List<LandmarkImage>>()
    
    /**
     * Load all landmark images for the given landmark IDs
     */
    suspend fun loadLandmarks(landmarkIds: Set<String>): LoadResult = withContext(Dispatchers.IO) {
        val loadedCount = mutableMapOf<String, Int>()
        val errors = mutableListOf<String>()
        
        landmarkIds.forEach { landmarkId ->
            try {
                val images = loadLandmarkImages(landmarkId)
                if (images.isNotEmpty()) {
                    landmarkImages[landmarkId] = images
                    loadedCount[landmarkId] = images.size
                    Log.d(TAG, "Loaded ${images.size} images for landmark: $landmarkId")
                } else {
                    errors.add("No images found for landmark: $landmarkId")
                    Log.w(TAG, "No images found for landmark: $landmarkId")
                }
            } catch (e: Exception) {
                val error = "Failed to load landmark $landmarkId: ${e.message}"
                errors.add(error)
                Log.e(TAG, error, e)
            }
        }
        
        LoadResult(
            totalLandmarks = landmarkIds.size,
            loadedLandmarks = loadedCount.size,
            totalImages = loadedCount.values.sum(),
            imagesByLandmark = loadedCount,
            errors = errors
        )
    }
    
    /**
     * Load images for a specific landmark
     */
    private suspend fun loadLandmarkImages(landmarkId: String): List<LandmarkImage> = withContext(Dispatchers.IO) {
        val images = mutableListOf<LandmarkImage>()
        
        try {
            // Try to list files in the landmark directory
            val landmarkDir = "${config.picturesDir}/$landmarkId"
            val files = context.assets.list(landmarkDir) ?: emptyArray()
            
            files.filter { file ->
                val extension = file.substringAfterLast('.', "").lowercase()
                SUPPORTED_EXTENSIONS.contains(extension)
            }.forEach { filename ->
                try {
                    val imagePath = "$landmarkDir/$filename"
                    val bitmap = loadBitmapFromAssets(imagePath)
                    
                    if (bitmap != null) {
                        val mat = bitmapToMat(bitmap)
                        val resizedMat = resizeImage(mat)
                        
                        images.add(
                            LandmarkImage(
                                landmarkId = landmarkId,
                                filename = filename,
                                path = imagePath,
                                mat = resizedMat,
                                width = resizedMat.cols(),
                                height = resizedMat.rows()
                            )
                        )
                        
                        // Clean up original mat if different from resized
                        if (mat != resizedMat) {
                            mat.release()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load image $filename for landmark $landmarkId", e)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Landmark directory not found: $landmarkId", e)
        }
        
        images
    }
    
    /**
     * Load bitmap from assets
     */
    private fun loadBitmapFromAssets(path: String): Bitmap? {
        return try {
            context.assets.open(path).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load bitmap from assets: $path", e)
            null
        }
    }
    
    /**
     * Convert bitmap to OpenCV Mat
     */
    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }
    
    /**
     * Resize image to configured width while maintaining aspect ratio
     */
    private fun resizeImage(mat: Mat): Mat {
        val originalWidth = mat.cols()
        val originalHeight = mat.rows()
        
        if (originalWidth <= config.frameResizeWidth) {
            return mat
        }
        
        val aspectRatio = originalHeight.toFloat() / originalWidth.toFloat()
        val newHeight = (config.frameResizeWidth * aspectRatio).toInt()
        
        val resized = Mat()
        org.opencv.imgproc.Imgproc.resize(mat, resized, org.opencv.core.Size(config.frameResizeWidth.toDouble(), newHeight.toDouble()))
        
        return resized
    }
    
    /**
     * Get images for a specific landmark
     */
    fun getLandmarkImages(landmarkId: String): List<LandmarkImage> {
        return landmarkImages[landmarkId] ?: emptyList()
    }
    
    /**
     * Get all loaded landmark IDs
     */
    fun getLoadedLandmarkIds(): Set<String> {
        return landmarkImages.keys.toSet()
    }
    
    /**
     * Clear all loaded images and free memory
     */
    fun clear() {
        landmarkImages.values.flatten().forEach { image ->
            image.mat.release()
        }
        landmarkImages.clear()
        Log.d(TAG, "Cleared all landmark images")
    }
    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        var totalPixels = 0L
        var totalImages = 0
        
        landmarkImages.values.flatten().forEach { image ->
            totalPixels += image.width * image.height
            totalImages++
        }
        
        // Estimate memory usage (assuming 3 bytes per pixel for RGB)
        val estimatedMemoryMB = (totalPixels * 3) / (1024 * 1024)
        
        return MemoryStats(
            totalImages = totalImages,
            totalPixels = totalPixels,
            estimatedMemoryMB = estimatedMemoryMB,
            landmarkCount = landmarkImages.size
        )
    }
}

/**
 * Represents a landmark reference image
 */
data class LandmarkImage(
    val landmarkId: String,
    val filename: String,
    val path: String,
    val mat: Mat,
    val width: Int,
    val height: Int
)

/**
 * Result of loading landmark images
 */
data class LoadResult(
    val totalLandmarks: Int,
    val loadedLandmarks: Int,
    val totalImages: Int,
    val imagesByLandmark: Map<String, Int>,
    val errors: List<String>
) {
    val isSuccess: Boolean get() = errors.isEmpty() && loadedLandmarks > 0
    val hasWarnings: Boolean get() = errors.isNotEmpty() && loadedLandmarks > 0
}

/**
 * Memory usage statistics
 */
data class MemoryStats(
    val totalImages: Int,
    val totalPixels: Long,
    val estimatedMemoryMB: Long,
    val landmarkCount: Int
)