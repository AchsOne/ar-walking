package com.example.arwalking.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Image Manager für das Laden von Bildern direkt aus dem Projektverzeichnis
 * Pfad: /Users/florian/Documents/GitHub/ar-walking/landmark_images/
 * 
 * Features:
 * - Lädt Bilder direkt aus dem Projektordner
 * - Kein Trainingsmodus erforderlich
 * - Manuelle Bildverwaltung möglich
 * - LRU-Cache für Performance
 * - Automatische Thumbnail-Generierung
 */
class ProjectDirectoryImageManager(private val context: Context) {
    
    private val TAG = "ProjectDirectoryImageManager"
    
    companion object {
        // Projektverzeichnis-Pfad (fest codiert wie gewünscht)
        const val PROJECT_LANDMARK_IMAGES_PATH = "/Users/florian/Documents/GitHub/ar-walking/landmark_images"
        
        // Cache-Konfiguration
        const val BITMAP_CACHE_SIZE = 50
        const val THUMBNAIL_CACHE_SIZE = 100
        const val THUMBNAIL_SIZE = 256
    }
    
    // Projektverzeichnis für Landmark-Bilder
    private val projectImagesDir = File(PROJECT_LANDMARK_IMAGES_PATH)
    
    // LRU-Caches für Performance
    private val bitmapCache = LruCache<String, Bitmap>(BITMAP_CACHE_SIZE)
    private val thumbnailCache = LruCache<String, Bitmap>(THUMBNAIL_CACHE_SIZE)
    
    // Loading States
    private val _loadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingStates: StateFlow<Map<String, Boolean>> = _loadingStates
    
    // Performance Monitor
    private val performanceMonitor = StoragePerformanceMonitor()
    
    init {
        // Stelle sicher, dass das Projektverzeichnis existiert
        if (!projectImagesDir.exists()) {
            Log.w(TAG, "Projektverzeichnis existiert nicht: $PROJECT_LANDMARK_IMAGES_PATH")
            Log.i(TAG, "Erstelle Verzeichnis...")
            projectImagesDir.mkdirs()
        } else {
            Log.i(TAG, "Projektverzeichnis gefunden: $PROJECT_LANDMARK_IMAGES_PATH")
            scanAvailableImages()
        }
    }
    
    /**
     * Scannt verfügbare Bilder im Projektverzeichnis
     */
    private fun scanAvailableImages() {
        try {
            val imageFiles = projectImagesDir.listFiles { file ->
                file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
            }
            
            Log.i(TAG, "Gefundene Bilder: ${imageFiles?.size ?: 0}")
            imageFiles?.forEach { file ->
                Log.d(TAG, "- ${file.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Scannen der Bilder: ${e.message}")
        }
    }
    
    /**
     * Lädt ein Vollbild aus dem Projektverzeichnis
     * Unterstützte Formate: .jpg, .jpeg, .png, .webp
     */
    suspend fun loadFullImage(landmarkId: String): Bitmap? = withContext(Dispatchers.IO) {
        performanceMonitor.measureOperation(
            StoragePerformanceMonitor.OP_LOAD_FULL_IMAGE,
            landmarkId
        ) {
            try {
                updateLoadingState(landmarkId, true)
                
                // Prüfe Cache zuerst
                bitmapCache.get(landmarkId)?.let { cachedBitmap ->
                    updateLoadingState(landmarkId, false)
                    Log.d(TAG, "Vollbild aus Cache geladen: $landmarkId")
                    return@measureOperation cachedBitmap
                }
                
                // Suche Bilddatei im Projektverzeichnis
                val imageFile = findImageFile(landmarkId)
                if (imageFile != null && imageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        // Cache das Bitmap
                        bitmapCache.put(landmarkId, bitmap)
                        updateLoadingState(landmarkId, false)
                        Log.d(TAG, "Vollbild geladen: $landmarkId (${bitmap.width}x${bitmap.height})")
                        return@measureOperation bitmap
                    }
                }
                
                updateLoadingState(landmarkId, false)
                Log.w(TAG, "Vollbild nicht gefunden: $landmarkId")
                null
                
            } catch (e: Exception) {
                updateLoadingState(landmarkId, false)
                Log.e(TAG, "Fehler beim Laden des Vollbildes $landmarkId: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Lädt ein Thumbnail (generiert es bei Bedarf)
     */
    suspend fun loadThumbnail(landmarkId: String): Bitmap? = withContext(Dispatchers.IO) {
        performanceMonitor.measureOperation(
            StoragePerformanceMonitor.OP_LOAD_THUMBNAIL,
            landmarkId
        ) {
            try {
                // Prüfe Thumbnail-Cache zuerst
                thumbnailCache.get(landmarkId)?.let { cachedThumbnail ->
                    Log.d(TAG, "Thumbnail aus Cache geladen: $landmarkId")
                    return@measureOperation cachedThumbnail
                }
                
                // Lade Vollbild und erstelle Thumbnail
                val fullImage = loadFullImageForThumbnail(landmarkId)
                if (fullImage != null) {
                    val thumbnail = createThumbnail(fullImage)
                    if (thumbnail != null) {
                        thumbnailCache.put(landmarkId, thumbnail)
                        Log.d(TAG, "Thumbnail generiert: $landmarkId (${thumbnail.width}x${thumbnail.height})")
                        return@measureOperation thumbnail
                    }
                }
                
                Log.w(TAG, "Thumbnail konnte nicht erstellt werden: $landmarkId")
                null
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden des Thumbnails $landmarkId: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Lädt Vollbild für Thumbnail-Generierung (ohne Cache-Update)
     */
    private fun loadFullImageForThumbnail(landmarkId: String): Bitmap? {
        return try {
            val imageFile = findImageFile(landmarkId)
            if (imageFile != null && imageFile.exists()) {
                // Lade mit reduzierter Auflösung für Thumbnail-Generierung
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imageFile.absolutePath, options)
                
                // Berechne Sample-Size für effiziente Thumbnail-Erstellung
                options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE * 2, THUMBNAIL_SIZE * 2)
                options.inJustDecodeBounds = false
                
                BitmapFactory.decodeFile(imageFile.absolutePath, options)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden für Thumbnail: ${e.message}")
            null
        }
    }
    
    /**
     * Sucht die Bilddatei für eine Landmark-ID
     * Unterstützt verschiedene Dateierweiterungen
     */
    private fun findImageFile(landmarkId: String): File? {
        val extensions = listOf("jpg", "jpeg", "png", "webp")
        
        for (extension in extensions) {
            val file = File(projectImagesDir, "$landmarkId.$extension")
            if (file.exists()) {
                return file
            }
        }
        
        // Fallback: Suche nach Dateien die mit der landmarkId beginnen
        val matchingFiles = projectImagesDir.listFiles { file ->
            file.isFile && file.nameWithoutExtension.equals(landmarkId, ignoreCase = true)
        }
        
        return matchingFiles?.firstOrNull()
    }
    
    /**
     * Erstellt ein Thumbnail aus einem Vollbild
     */
    private fun createThumbnail(bitmap: Bitmap): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            
            // Berechne neue Dimensionen (quadratisch)
            val size = minOf(width, height)
            val x = (width - size) / 2
            val y = (height - size) / 2
            
            // Schneide quadratischen Bereich aus
            val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
            
            // Skaliere auf Thumbnail-Größe
            Bitmap.createScaledBitmap(croppedBitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Erstellen des Thumbnails: ${e.message}")
            null
        }
    }
    
    /**
     * Berechnet optimale Sample-Size für Bitmap-Dekodierung
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Gibt alle verfügbaren Landmark-IDs zurück
     */
    suspend fun getAvailableLandmarks(): List<LandmarkInfo> = withContext(Dispatchers.IO) {
        try {
            val imageFiles = projectImagesDir.listFiles { file ->
                file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp")
            } ?: emptyArray()
            
            imageFiles.map { file ->
                LandmarkInfo(
                    id = file.nameWithoutExtension,
                    filename = file.name,
                    fileSize = file.length(),
                    lastModified = file.lastModified(),
                    extension = file.extension.lowercase()
                )
            }.sortedBy { it.id }
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der verfügbaren Landmarks: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Prüft ob ein Landmark-Bild existiert
     */
    fun hasLandmarkImage(landmarkId: String): Boolean {
        return findImageFile(landmarkId)?.exists() == true
    }
    
    /**
     * Gibt Informationen über ein Landmark zurück
     */
    suspend fun getLandmarkInfo(landmarkId: String): LandmarkInfo? = withContext(Dispatchers.IO) {
        val imageFile = findImageFile(landmarkId)
        if (imageFile != null && imageFile.exists()) {
            LandmarkInfo(
                id = landmarkId,
                filename = imageFile.name,
                fileSize = imageFile.length(),
                lastModified = imageFile.lastModified(),
                extension = imageFile.extension.lowercase()
            )
        } else {
            null
        }
    }
    
    /**
     * Aktualisiert den Loading-State
     */
    private fun updateLoadingState(landmarkId: String, isLoading: Boolean) {
        val currentStates = _loadingStates.value.toMutableMap()
        if (isLoading) {
            currentStates[landmarkId] = true
        } else {
            currentStates.remove(landmarkId)
        }
        _loadingStates.value = currentStates
    }
    
    /**
     * Bereinigt die Caches
     */
    fun clearCaches() {
        bitmapCache.evictAll()
        thumbnailCache.evictAll()
        Log.i(TAG, "Caches bereinigt")
    }
    
    /**
     * Gibt Cache-Statistiken zurück
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            bitmapCacheSize = bitmapCache.size(),
            bitmapCacheMaxSize = bitmapCache.maxSize(),
            thumbnailCacheSize = thumbnailCache.size(),
            thumbnailCacheMaxSize = thumbnailCache.maxSize(),
            bitmapCacheHitCount = bitmapCache.hitCount().toLong(),
            bitmapCacheMissCount = bitmapCache.missCount().toLong(),
            thumbnailCacheHitCount = thumbnailCache.hitCount().toLong(),
            thumbnailCacheMissCount = thumbnailCache.missCount().toLong()
        )
    }
    
    /**
     * Loggt Performance-Zusammenfassung
     */
    fun logPerformanceSummary() {
        performanceMonitor.logPerformanceSummary()
        
        val cacheStats = getCacheStats()
        Log.i(TAG, "=== CACHE STATISTIKEN ===")
        Log.i(TAG, "Bitmap Cache: ${cacheStats.bitmapCacheSize}/${cacheStats.bitmapCacheMaxSize}")
        Log.i(TAG, "Thumbnail Cache: ${cacheStats.thumbnailCacheSize}/${cacheStats.thumbnailCacheMaxSize}")
        Log.i(TAG, "Bitmap Hit Rate: ${String.format("%.1f", cacheStats.getBitmapHitRate())}%")
        Log.i(TAG, "Thumbnail Hit Rate: ${String.format("%.1f", cacheStats.getThumbnailHitRate())}%")
    }
}

/**
 * Informationen über ein Landmark-Bild
 */
data class LandmarkInfo(
    val id: String,
    val filename: String,
    val fileSize: Long,
    val lastModified: Long,
    val extension: String
) {
    fun getFileSizeKB(): Double = fileSize / 1024.0
    fun getFileSizeMB(): Double = fileSize / (1024.0 * 1024.0)
    fun getLastModifiedDate(): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(lastModified))
}

/**
 * Cache-Statistiken
 */
data class CacheStats(
    val bitmapCacheSize: Int,
    val bitmapCacheMaxSize: Int,
    val thumbnailCacheSize: Int,
    val thumbnailCacheMaxSize: Int,
    val bitmapCacheHitCount: Long,
    val bitmapCacheMissCount: Long,
    val thumbnailCacheHitCount: Long,
    val thumbnailCacheMissCount: Long
) {
    fun getBitmapHitRate(): Double {
        val total = bitmapCacheHitCount + bitmapCacheMissCount
        return if (total > 0) (bitmapCacheHitCount.toDouble() / total) * 100 else 0.0
    }
    
    fun getThumbnailHitRate(): Double {
        val total = thumbnailCacheHitCount + thumbnailCacheMissCount
        return if (total > 0) (thumbnailCacheHitCount.toDouble() / total) * 100 else 0.0
    }
}