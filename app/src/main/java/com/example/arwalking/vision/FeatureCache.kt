package com.example.arwalking.vision

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import java.io.*
import java.security.MessageDigest
import com.example.arwalking.data.ARNavigationConfig

/**
 * FeatureCache - Persistent cache for ORB features and descriptors
 * Handles feature extraction caching to improve startup performance
 */
class FeatureCache(
    private val context: Context,
    private val config: ARNavigationConfig
) {
    
    companion object {
        private const val TAG = "FeatureCache"
        private const val CACHE_VERSION = "v1"
        private const val FEATURES_SUFFIX = "_features.cache"
        private const val DESCRIPTORS_SUFFIX = "_descriptors.cache"
        private const val METADATA_SUFFIX = "_metadata.cache"
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, config.cacheDir).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Cache features for a landmark image
     */
    suspend fun cacheFeatures(
        landmarkId: String,
        imagePath: String,
        keypoints: MatOfKeyPoint,
        descriptors: Mat
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val imageHash = calculateImageHash(imagePath)
            val cacheKey = "${landmarkId}_${imageHash}_$CACHE_VERSION"
            
            // Save keypoints
            val featuresFile = File(cacheDir, "$cacheKey$FEATURES_SUFFIX")
            saveKeypoints(keypoints, featuresFile)
            
            // Save descriptors
            val descriptorsFile = File(cacheDir, "$cacheKey$DESCRIPTORS_SUFFIX")
            saveDescriptors(descriptors, descriptorsFile)
            
            // Save metadata
            val metadataFile = File(cacheDir, "$cacheKey$METADATA_SUFFIX")
            saveMetadata(landmarkId, imagePath, imageHash, metadataFile)
            
            Log.d(TAG, "Cached features for $landmarkId: ${keypoints.total()} keypoints")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache features for $landmarkId", e)
            false
        }
    }
    
    /**
     * Load cached features for a landmark image
     */
    suspend fun loadCachedFeatures(
        landmarkId: String,
        imagePath: String
    ): CachedFeatures? = withContext(Dispatchers.IO) {
        try {
            val imageHash = calculateImageHash(imagePath)
            val cacheKey = "${landmarkId}_${imageHash}_$CACHE_VERSION"
            
            val featuresFile = File(cacheDir, "$cacheKey$FEATURES_SUFFIX")
            val descriptorsFile = File(cacheDir, "$cacheKey$DESCRIPTORS_SUFFIX")
            val metadataFile = File(cacheDir, "$cacheKey$METADATA_SUFFIX")
            
            if (!featuresFile.exists() || !descriptorsFile.exists() || !metadataFile.exists()) {
                return@withContext null
            }
            
            // Validate metadata
            val metadata = loadMetadata(metadataFile)
            if (metadata?.imageHash != imageHash) {
                Log.w(TAG, "Cache invalidated for $landmarkId: hash mismatch")
                return@withContext null
            }
            
            val keypoints = loadKeypoints(featuresFile)
            val descriptors = loadDescriptors(descriptorsFile)
            
            if (keypoints != null && descriptors != null) {
                Log.d(TAG, "Loaded cached features for $landmarkId: ${keypoints.total()} keypoints")
                CachedFeatures(keypoints, descriptors, metadata)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached features for $landmarkId", e)
            null
        }
    }
    
    /**
     * Check if features are cached for a landmark image
     */
    suspend fun isCached(landmarkId: String, imagePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val imageHash = calculateImageHash(imagePath)
            val cacheKey = "${landmarkId}_${imageHash}_$CACHE_VERSION"
            
            val featuresFile = File(cacheDir, "$cacheKey$FEATURES_SUFFIX")
            val descriptorsFile = File(cacheDir, "$cacheKey$DESCRIPTORS_SUFFIX")
            val metadataFile = File(cacheDir, "$cacheKey$METADATA_SUFFIX")
            
            featuresFile.exists() && descriptorsFile.exists() && metadataFile.exists()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Clear all cached features
     */
    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = cacheDir.listFiles()?.count { it.delete() } ?: 0
            Log.d(TAG, "Cleared cache: deleted $deleted files")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
            false
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            val featureFiles = files.filter { it.name.endsWith(FEATURES_SUFFIX) }
            val descriptorFiles = files.filter { it.name.endsWith(DESCRIPTORS_SUFFIX) }
            val metadataFiles = files.filter { it.name.endsWith(METADATA_SUFFIX) }
            
            val totalSize = files.sumOf { it.length() }
            val cachedLandmarks = featureFiles.size // Each landmark image has one feature file
            
            CacheStats(
                totalFiles = files.size,
                cachedLandmarks = cachedLandmarks,
                totalSizeBytes = totalSize,
                featureFiles = featureFiles.size,
                descriptorFiles = descriptorFiles.size,
                metadataFiles = metadataFiles.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cache stats", e)
            CacheStats(0, 0, 0, 0, 0, 0)
        }
    }
    
    /**
     * Calculate hash for image file to detect changes
     */
    private suspend fun calculateImageHash(imagePath: String): String = withContext(Dispatchers.IO) {
        try {
            context.assets.open(imagePath).use { inputStream ->
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate image hash for $imagePath", e)
            imagePath.hashCode().toString()
        }
    }
    
    /**
     * Save keypoints to file
     */
    private fun saveKeypoints(keypoints: MatOfKeyPoint, file: File) {
        ObjectOutputStream(FileOutputStream(file)).use { oos ->
            val keypointArray = keypoints.toArray()
            oos.writeInt(keypointArray.size)
            keypointArray.forEach { kp ->
                oos.writeFloat(kp.pt.x.toFloat())
                oos.writeFloat(kp.pt.y.toFloat())
                oos.writeFloat(kp.size)
                oos.writeFloat(kp.angle)
                oos.writeFloat(kp.response)
                oos.writeInt(kp.octave)
                oos.writeInt(kp.class_id)
            }
        }
    }
    
    /**
     * Load keypoints from file
     */
    private fun loadKeypoints(file: File): MatOfKeyPoint? {
        return try {
            ObjectInputStream(FileInputStream(file)).use { ois ->
                val size = ois.readInt()
                val keypoints = Array(size) {
                    org.opencv.core.KeyPoint(
                        ois.readFloat().toDouble(),
                        ois.readFloat().toDouble(),
                        ois.readFloat(),
                        ois.readFloat(),
                        ois.readFloat(),
                        ois.readInt(),
                        ois.readInt()
                    )
                }
                MatOfKeyPoint(*keypoints)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load keypoints from $file", e)
            null
        }
    }
    
    /**
     * Save descriptors to file
     */
    private fun saveDescriptors(descriptors: Mat, file: File) {
        DataOutputStream(FileOutputStream(file)).use { dos ->
            dos.writeInt(descriptors.rows())
            dos.writeInt(descriptors.cols())
            dos.writeInt(descriptors.type())
            
            val data = ByteArray(descriptors.rows() * descriptors.cols() * descriptors.elemSize().toInt())
            descriptors.get(0, 0, data)
            dos.write(data)
        }
    }
    
    /**
     * Load descriptors from file
     */
    private fun loadDescriptors(file: File): Mat? {
        return try {
            DataInputStream(FileInputStream(file)).use { dis ->
                val rows = dis.readInt()
                val cols = dis.readInt()
                val type = dis.readInt()
                
                val mat = Mat(rows, cols, type)
                val data = ByteArray(rows * cols * mat.elemSize().toInt())
                dis.readFully(data)
                mat.put(0, 0, data)
                
                mat
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load descriptors from $file", e)
            null
        }
    }
    
    /**
     * Save metadata to file
     */
    private fun saveMetadata(landmarkId: String, imagePath: String, imageHash: String, file: File) {
        ObjectOutputStream(FileOutputStream(file)).use { oos ->
            oos.writeUTF(landmarkId)
            oos.writeUTF(imagePath)
            oos.writeUTF(imageHash)
            oos.writeLong(System.currentTimeMillis())
            oos.writeUTF(CACHE_VERSION)
        }
    }
    
    /**
     * Load metadata from file
     */
    private fun loadMetadata(file: File): CacheMetadata? {
        return try {
            ObjectInputStream(FileInputStream(file)).use { ois ->
                CacheMetadata(
                    landmarkId = ois.readUTF(),
                    imagePath = ois.readUTF(),
                    imageHash = ois.readUTF(),
                    timestamp = ois.readLong(),
                    version = ois.readUTF()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load metadata from $file", e)
            null
        }
    }
}

/**
 * Cached features data
 */
data class CachedFeatures(
    val keypoints: MatOfKeyPoint,
    val descriptors: Mat,
    val metadata: CacheMetadata
)

/**
 * Cache metadata
 */
data class CacheMetadata(
    val landmarkId: String,
    val imagePath: String,
    val imageHash: String,
    val timestamp: Long,
    val version: String
)

/**
 * Cache statistics
 */
data class CacheStats(
    val totalFiles: Int,
    val cachedLandmarks: Int,
    val totalSizeBytes: Long,
    val featureFiles: Int,
    val descriptorFiles: Int,
    val metadataFiles: Int
) {
    val totalSizeMB: Float get() = totalSizeBytes / (1024f * 1024f)
    val hitRate: Float get() = if (cachedLandmarks > 0) cachedLandmarks.toFloat() / cachedLandmarks else 0f
}