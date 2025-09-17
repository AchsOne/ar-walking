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

/**
 * Image Manager für das Laden von Bildern direkt aus den App-Assets
 * Pfad: app/src/main/assets/landmark_images/
 *
 * Features:
 * - Lädt Bilder aus Assets
 * - Kein Trainingsmodus erforderlich
 * - LRU-Cache für Performance
 * - Automatische Thumbnail-Generierung
 */
class ProjectDirectoryImageManager(private val context: Context) {

    private val TAG = "ProjectDirectoryImageManager"

    companion object {
        const val PROJECT_ASSETS_PATH = "landmark_images"

        // Cache-Konfiguration
        const val BITMAP_CACHE_SIZE = 50
        const val THUMBNAIL_CACHE_SIZE = 100
        const val THUMBNAIL_SIZE = 256
    }

    // LRU-Caches
    private val bitmapCache = LruCache<String, Bitmap>(BITMAP_CACHE_SIZE)
    private val thumbnailCache = LruCache<String, Bitmap>(THUMBNAIL_CACHE_SIZE)

    // Ladezustände
    private val _loadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingStates: StateFlow<Map<String, Boolean>> = _loadingStates

    // Performance Monitor
    private val performanceMonitor = StoragePerformanceMonitor()

    init {
        // Nur loggen, welche Dateien existieren
        scanAvailableImages()
    }

    /**
     * Scannt verfügbare Bilder in den Assets
     */
    private fun scanAvailableImages() {
        try {
            val files = context.assets.list(PROJECT_ASSETS_PATH) ?: emptyArray()
            val images = files.filter { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) || it.endsWith(".webp", true) }
            Log.i(TAG, "Gefundene Asset-Bilder: ${images.size}")
            images.forEach { Log.d(TAG, "- $it") }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Scannen der Assets: ${e.message}")
        }
    }

    /**
     * Lädt ein Vollbild aus den Assets
     */
    suspend fun loadFullImage(landmarkId: String): Bitmap? = withContext(Dispatchers.IO) {
        performanceMonitor.measureOperation(
            StoragePerformanceMonitor.OP_LOAD_FULL_IMAGE,
            landmarkId
        ) {
            try {
                updateLoadingState(landmarkId, true)

                // Cache prüfen
                bitmapCache.get(landmarkId)?.let { return@measureOperation it }

                // Datei finden
                val fileName = findAssetFileName(landmarkId)
                if (fileName != null) {
                    val bitmap = loadBitmapFromAssets(fileName)
                    if (bitmap != null) {
                        bitmapCache.put(landmarkId, bitmap)
                        Log.d(TAG, "Vollbild geladen: $landmarkId (${bitmap.width}x${bitmap.height})")
                        return@measureOperation bitmap
                    }
                }

                Log.w(TAG, "Vollbild nicht gefunden in Assets: $landmarkId")
                null
            } finally {
                updateLoadingState(landmarkId, false)
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
                // Cache prüfen
                thumbnailCache.get(landmarkId)?.let { return@measureOperation it }

                // Vollbild laden
                val fileName = findAssetFileName(landmarkId)
                if (fileName != null) {
                    val fullImage = loadBitmapFromAssets(fileName)
                    if (fullImage != null) {
                        val thumbnail = createThumbnail(fullImage)
                        if (thumbnail != null) {
                            thumbnailCache.put(landmarkId, thumbnail)
                            Log.d(TAG, "Thumbnail generiert: $landmarkId (${thumbnail.width}x${thumbnail.height})")
                            return@measureOperation thumbnail
                        }
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
     * Hilfsfunktion: Lädt ein Bitmap aus Assets
     */
    private fun loadBitmapFromAssets(fileName: String): Bitmap? {
        return try {
            context.assets.open("$PROJECT_ASSETS_PATH/$fileName").use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden aus Assets: ${e.message}")
            null
        }
    }

    /**
     * Sucht die passende Asset-Datei zu einer Landmark-ID
     */
    private fun findAssetFileName(landmarkId: String): String? {
        return try {
            val files = context.assets.list(PROJECT_ASSETS_PATH) ?: return null
            files.firstOrNull { it.substringBeforeLast(".").equals(landmarkId, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Suchen in Assets: ${e.message}")
            null
        }
    }

    /**
     * Thumbnail erstellen
     */
    private fun createThumbnail(bitmap: Bitmap): Bitmap? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val size = minOf(width, height)
            val x = (width - size) / 2
            val y = (height - size) / 2
            val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
            Bitmap.createScaledBitmap(cropped, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Erstellen des Thumbnails: ${e.message}")
            null
        }
    }

    /**
     * Liste aller verfügbaren Landmarks
     */
    suspend fun getAvailableLandmarks(): List<LandmarkInfo> = withContext(Dispatchers.IO) {
        try {
            val files = context.assets.list(PROJECT_ASSETS_PATH) ?: return@withContext emptyList()
            files.filter { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) || it.endsWith(".webp", true) }
                .map { file ->
                    LandmarkInfo(
                        id = file.substringBeforeLast("."),
                        filename = file,
                        fileSize = 0, // Assets geben keine echte Größe zurück
                        lastModified = 0,
                        extension = file.substringAfterLast(".", "")
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Auflisten der Assets: ${e.message}")
            emptyList()
        }
    }

    fun hasLandmarkImage(landmarkId: String): Boolean {
        return findAssetFileName(landmarkId) != null
    }

    suspend fun getLandmarkInfo(landmarkId: String): LandmarkInfo? = withContext(Dispatchers.IO) {
        val fileName = findAssetFileName(landmarkId)
        if (fileName != null) {
            LandmarkInfo(
                id = landmarkId,
                filename = fileName,
                fileSize = 0,
                lastModified = 0,
                extension = fileName.substringAfterLast(".", "")
            )
        } else null
    }

    private fun updateLoadingState(landmarkId: String, isLoading: Boolean) {
        val current = _loadingStates.value.toMutableMap()
        if (isLoading) current[landmarkId] = true else current.remove(landmarkId)
        _loadingStates.value = current
    }

    fun clearCaches() {
        bitmapCache.evictAll()
        thumbnailCache.evictAll()
        Log.i(TAG, "Caches geleert")
    }

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

    fun logPerformanceSummary() {
        performanceMonitor.logPerformanceSummary()
        val stats = getCacheStats()
        Log.i(TAG, "=== CACHE ===")
        Log.i(TAG, "Bitmap Cache: ${stats.bitmapCacheSize}/${stats.bitmapCacheMaxSize} (HitRate: ${stats.getBitmapHitRate()}%)")
        Log.i(TAG, "Thumbnail Cache: ${stats.thumbnailCacheSize}/${stats.thumbnailCacheMaxSize} (HitRate: ${stats.getThumbnailHitRate()}%)")
    }
}
