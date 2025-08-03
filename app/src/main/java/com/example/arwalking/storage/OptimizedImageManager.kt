package com.example.arwalking.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Performance-optimierter Image Manager für hunderte von Bildern
 * Features:
 * - LRU Cache für Bitmaps
 * - Lazy Loading
 * - Thumbnail-Generierung
 * - Asynchrone I/O
 * - Speicher-effiziente Komprimierung
 * - Paging für große Listen
 */
class OptimizedImageManager(private val context: Context) {
    
    private val TAG = "OptimizedImageManager"
    private val gson = Gson()
    
    // Verzeichnisse
    private val imagesDir = File(context.filesDir, "landmark_images")
    private val thumbnailsDir = File(context.filesDir, "landmark_thumbnails")
    private val metadataDir = File(context.filesDir, "landmark_metadata")
    private val indexFile = File(context.filesDir, "landmark_index.json")
    
    // Performance-Konfiguration
    private val THUMBNAIL_SIZE = 256 // Thumbnail-Größe in Pixeln
    private val CACHE_SIZE = 50 // Anzahl Bilder im Cache
    private val COMPRESSION_QUALITY = 85 // JPEG-Qualität
    private val MAX_IMAGE_DIMENSION = 2048 // Maximale Bildgröße
    private val PAGE_SIZE = 20 // Anzahl Bilder pro Seite
    
    // LRU Cache für Bitmaps
    private val bitmapCache = LruCache<String, Bitmap>(CACHE_SIZE)
    private val thumbnailCache = LruCache<String, Bitmap>(CACHE_SIZE * 2)
    
    // Metadaten-Index (nur Metadaten, keine Bitmaps)
    private val metadataIndex = ConcurrentHashMap<String, LandmarkMetadata>()
    
    // Loading States
    private val _loadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val loadingStates: StateFlow<Map<String, Boolean>> = _loadingStates
    
    // Coroutine Scope für Background-Tasks
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    data class LandmarkMetadata(
        val id: String,
        val name: String,
        val description: String,
        val category: String,
        val uploadTime: String,
        val fileSize: Long,
        val imageWidth: Int,
        val imageHeight: Int,
        val deviceInfo: String,
        val hasImage: Boolean = true,
        val hasThumbnail: Boolean = true
    )
    
    data class PagedResult<T>(
        val items: List<T>,
        val totalCount: Int,
        val page: Int,
        val pageSize: Int,
        val hasMore: Boolean
    )
    
    init {
        // Erstelle Verzeichnisse
        imagesDir.mkdirs()
        thumbnailsDir.mkdirs()
        metadataDir.mkdirs()
        
        // Lade Metadaten-Index asynchron
        backgroundScope.launch {
            loadMetadataIndex()
        }
    }
    
    /**
     * Speichert ein Bild mit optimaler Performance
     */
    suspend fun saveImageOptimized(
        bitmap: Bitmap,
        landmarkId: String,
        landmarkName: String,
        description: String,
        category: String = "Training",
        onProgress: (String) -> Unit = {}
    ): SaveResult = withContext(Dispatchers.IO) {
        
        try {
            onProgress("Optimiere Bild...")
            
            // 1. Optimiere Bitmap-Größe
            val optimizedBitmap = optimizeBitmap(bitmap)
            
            // 2. Speichere Hauptbild
            onProgress("Speichere Hauptbild...")
            val imageFile = File(imagesDir, "${landmarkId}.jpg")
            val imageSuccess = saveBitmapToFile(optimizedBitmap, imageFile, COMPRESSION_QUALITY)
            
            if (!imageSuccess) {
                return@withContext SaveResult.Error("Fehler beim Speichern des Hauptbildes")
            }
            
            // 3. Generiere und speichere Thumbnail
            onProgress("Erstelle Thumbnail...")
            val thumbnail = createThumbnail(optimizedBitmap)
            val thumbnailFile = File(thumbnailsDir, "${landmarkId}.jpg")
            saveBitmapToFile(thumbnail, thumbnailFile, 75)
            
            // 4. Erstelle Metadaten
            onProgress("Speichere Metadaten...")
            val metadata = LandmarkMetadata(
                id = landmarkId,
                name = landmarkName,
                description = description,
                category = category,
                uploadTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(Date()),
                fileSize = imageFile.length(),
                imageWidth = optimizedBitmap.width,
                imageHeight = optimizedBitmap.height,
                deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                hasImage = true,
                hasThumbnail = thumbnailFile.exists()
            )
            
            // 5. Speichere Metadaten
            val metadataFile = File(metadataDir, "${landmarkId}.json")
            metadataFile.writeText(gson.toJson(metadata))
            
            // 6. Aktualisiere Index
            metadataIndex[landmarkId] = metadata
            saveMetadataIndex()
            
            // 7. Cache das Thumbnail
            thumbnailCache.put(landmarkId, thumbnail)
            
            onProgress("Bild erfolgreich optimiert und gespeichert!")
            Log.i(TAG, "Bild optimiert gespeichert: $landmarkId (${imageFile.length()} bytes)")
            
            SaveResult.Success("Bild erfolgreich gespeichert: $landmarkName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim optimierten Speichern: ${e.message}")
            SaveResult.Error("Fehler beim Speichern: ${e.message}")
        }
    }
    
    /**
     * Lädt ein Thumbnail (schnell)
     */
    suspend fun loadThumbnail(landmarkId: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // 1. Prüfe Cache
            thumbnailCache.get(landmarkId)?.let { return@withContext it }
            
            // 2. Lade von Datei
            val thumbnailFile = File(thumbnailsDir, "${landmarkId}.jpg")
            if (thumbnailFile.exists()) {
                val thumbnail = BitmapFactory.decodeFile(thumbnailFile.absolutePath)
                if (thumbnail != null) {
                    thumbnailCache.put(landmarkId, thumbnail)
                    return@withContext thumbnail
                }
            }
            
            // 3. Fallback: Erstelle Thumbnail vom Hauptbild
            val imageFile = File(imagesDir, "${landmarkId}.jpg")
            if (imageFile.exists()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imageFile.absolutePath, options)
                
                // Berechne Sample-Size für Thumbnail
                options.inSampleSize = calculateInSampleSize(options, THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                options.inJustDecodeBounds = false
                
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                if (bitmap != null) {
                    val thumbnail = createThumbnail(bitmap)
                    thumbnailCache.put(landmarkId, thumbnail)
                    
                    // Speichere Thumbnail für nächstes Mal
                    val thumbnailFile = File(thumbnailsDir, "${landmarkId}.jpg")
                    saveBitmapToFile(thumbnail, thumbnailFile, 75)
                    
                    return@withContext thumbnail
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Thumbnails $landmarkId: ${e.message}")
            null
        }
    }
    
    /**
     * Lädt ein Vollbild (langsamer, mit Cache)
     */
    suspend fun loadFullImage(landmarkId: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Update Loading State
            updateLoadingState(landmarkId, true)
            
            // 1. Prüfe Cache
            bitmapCache.get(landmarkId)?.let { 
                updateLoadingState(landmarkId, false)
                return@withContext it 
            }
            
            // 2. Lade von Datei
            val imageFile = File(imagesDir, "${landmarkId}.jpg")
            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap != null) {
                    bitmapCache.put(landmarkId, bitmap)
                    updateLoadingState(landmarkId, false)
                    Log.d(TAG, "Vollbild geladen: $landmarkId")
                    return@withContext bitmap
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
    
    /**
     * Gibt Landmarks paginiert zurück (für große Listen)
     */
    suspend fun getLandmarksPaged(
        page: Int = 0,
        pageSize: Int = PAGE_SIZE,
        searchQuery: String = "",
        category: String = ""
    ): PagedResult<LandmarkMetadata> = withContext(Dispatchers.IO) {
        
        try {
            // Filtere Metadaten
            var filteredMetadata = metadataIndex.values.toList()
            
            if (searchQuery.isNotEmpty()) {
                val query = searchQuery.lowercase()
                filteredMetadata = filteredMetadata.filter { 
                    it.name.lowercase().contains(query) || 
                    it.description.lowercase().contains(query) ||
                    it.id.lowercase().contains(query)
                }
            }
            
            if (category.isNotEmpty()) {
                filteredMetadata = filteredMetadata.filter { it.category == category }
            }
            
            // Sortiere nach Upload-Zeit (neueste zuerst)
            filteredMetadata = filteredMetadata.sortedByDescending { it.uploadTime }
            
            // Paginierung
            val totalCount = filteredMetadata.size
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, totalCount)
            
            val pageItems = if (startIndex < totalCount) {
                filteredMetadata.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            PagedResult(
                items = pageItems,
                totalCount = totalCount,
                page = page,
                pageSize = pageSize,
                hasMore = endIndex < totalCount
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei paginierter Abfrage: ${e.message}")
            PagedResult(emptyList(), 0, page, pageSize, false)
        }
    }
    
    /**
     * Löscht ein Landmark mit allen Dateien
     */
    suspend fun deleteLandmark(landmarkId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Entferne aus Caches
            bitmapCache.remove(landmarkId)
            thumbnailCache.remove(landmarkId)
            
            // Lösche Dateien
            val imageFile = File(imagesDir, "${landmarkId}.jpg")
            val thumbnailFile = File(thumbnailsDir, "${landmarkId}.jpg")
            val metadataFile = File(metadataDir, "${landmarkId}.json")
            
            var success = true
            if (imageFile.exists()) success = success && imageFile.delete()
            if (thumbnailFile.exists()) success = success && thumbnailFile.delete()
            if (metadataFile.exists()) success = success && metadataFile.delete()
            
            // Entferne aus Index
            metadataIndex.remove(landmarkId)
            saveMetadataIndex()
            
            Log.i(TAG, "Landmark gelöscht: $landmarkId")
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Löschen: ${e.message}")
            false
        }
    }
    
    /**
     * Bereinigt Cache und defekte Dateien
     */
    suspend fun cleanup(): ImageCleanupResult = withContext(Dispatchers.IO) {
        var removedFiles = 0
        var removedEntries = 0
        var errors = 0
        
        try {
            // 1. Cache leeren
            bitmapCache.evictAll()
            thumbnailCache.evictAll()
            
            // 2. Prüfe Metadaten-Konsistenz
            val toRemove = mutableListOf<String>()
            
            for ((landmarkId, metadata) in metadataIndex) {
                val imageFile = File(imagesDir, "${landmarkId}.jpg")
                val metadataFile = File(metadataDir, "${landmarkId}.json")
                
                if (!imageFile.exists() || !metadataFile.exists()) {
                    toRemove.add(landmarkId)
                    removedEntries++
                }
            }
            
            toRemove.forEach { metadataIndex.remove(it) }
            
            // 3. Entferne verwaiste Dateien
            imagesDir.listFiles()?.forEach { file ->
                val landmarkId = file.nameWithoutExtension
                if (!metadataIndex.containsKey(landmarkId)) {
                    if (file.delete()) removedFiles++ else errors++
                }
            }
            
            thumbnailsDir.listFiles()?.forEach { file ->
                val landmarkId = file.nameWithoutExtension
                if (!metadataIndex.containsKey(landmarkId)) {
                    if (file.delete()) removedFiles++ else errors++
                }
            }
            
            metadataDir.listFiles()?.forEach { file ->
                val landmarkId = file.nameWithoutExtension
                if (!metadataIndex.containsKey(landmarkId)) {
                    if (file.delete()) removedFiles++ else errors++
                }
            }
            
            // 4. Speichere bereinigten Index
            saveMetadataIndex()
            
            Log.i(TAG, "Cleanup: $removedFiles Dateien, $removedEntries Einträge entfernt")
            
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup-Fehler: ${e.message}")
            errors++
        }
        
        ImageCleanupResult(removedFiles, removedEntries, errors)
    }
    
    /**
     * Gibt Speicher-Statistiken zurück
     */
    fun getMemoryStats(): MemoryStats {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        
        return MemoryStats(
            usedMemoryMB = usedMemory / (1024 * 1024),
            maxMemoryMB = maxMemory / (1024 * 1024),
            cacheSize = bitmapCache.size(),
            thumbnailCacheSize = thumbnailCache.size(),
            totalLandmarks = metadataIndex.size
        )
    }
    
    // Private Helper-Methoden
    
    private fun optimizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Prüfe ob Resize nötig
        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }
        
        // Berechne neue Größe (behält Aspect Ratio)
        val ratio = minOf(
            MAX_IMAGE_DIMENSION.toFloat() / width,
            MAX_IMAGE_DIMENSION.toFloat() / height
        )
        
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    private fun createThumbnail(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Berechne Thumbnail-Größe (quadratisch, zentriert)
        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2
        
        // Schneide quadratischen Bereich aus
        val squareBitmap = Bitmap.createBitmap(bitmap, x, y, size, size)
        
        // Skaliere auf Thumbnail-Größe
        return Bitmap.createScaledBitmap(squareBitmap, THUMBNAIL_SIZE, THUMBNAIL_SIZE, true)
    }
    
    private fun saveBitmapToFile(bitmap: Bitmap, file: File, quality: Int): Boolean {
        return try {
            file.outputStream().use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern der Bitmap: ${e.message}")
            false
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    private suspend fun loadMetadataIndex() {
        try {
            if (indexFile.exists()) {
                val indexJson = indexFile.readText()
                val type = object : TypeToken<Map<String, LandmarkMetadata>>() {}.type
                val loadedIndex: Map<String, LandmarkMetadata> = gson.fromJson(indexJson, type)
                
                metadataIndex.clear()
                metadataIndex.putAll(loadedIndex)
                
                Log.i(TAG, "Metadaten-Index geladen: ${metadataIndex.size} Einträge")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Index: ${e.message}")
        }
    }
    
    private suspend fun saveMetadataIndex() {
        try {
            val indexJson = gson.toJson(metadataIndex)
            indexFile.writeText(indexJson)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern des Index: ${e.message}")
        }
    }
    
    private fun updateLoadingState(landmarkId: String, isLoading: Boolean) {
        val currentStates = _loadingStates.value.toMutableMap()
        if (isLoading) {
            currentStates[landmarkId] = true
        } else {
            currentStates.remove(landmarkId)
        }
        _loadingStates.value = currentStates
    }
    
    // Cleanup beim Destroy
    fun destroy() {
        backgroundScope.cancel()
        bitmapCache.evictAll()
        thumbnailCache.evictAll()
    }
}

data class MemoryStats(
    val usedMemoryMB: Long,
    val maxMemoryMB: Long,
    val cacheSize: Int,
    val thumbnailCacheSize: Int,
    val totalLandmarks: Int
)

data class ImageCleanupResult(
    val removedFiles: Int,
    val removedEntries: Int,
    val errors: Int
) {
    val totalFilesRemoved: Int get() = removedFiles + removedEntries
    val totalSpaceFreedMB: Double get() = 0.0 // Placeholder - could be calculated if needed
}