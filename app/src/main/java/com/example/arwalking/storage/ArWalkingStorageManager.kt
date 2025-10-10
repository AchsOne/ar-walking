package com.example.arwalking.storage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Haupt-Storage-Manager für ArWalking
 * Vereint alle Storage-Komponenten und lädt Bilder direkt aus dem Projektverzeichnis
 * 
 * Features:
 * - Lädt Bilder direkt
 * - Kein Trainingsmodus erforderlich - einfach Bilder hinzufügen!
 * - LRU-Cache für 50 Vollbilder und 100 Thumbnails
 * - Automatische Thumbnail-Generierung (256x256px)
 * - Performance-Monitoring (Ziel: 5-15ms Ladezeit)
 * - Offline-First Design
 */
class ArWalkingStorageManager(private val context: Context) {
    
    private val TAG = "ArWalkingStorageManager"
    
    // Komponenten
    private val directoryManager = StorageDirectoryManager(context)
    private val projectImageManager = ProjectDirectoryImageManager(context)  // NEU: Lädt aus Projektverzeichnis
    private val optimizedImageManager = OptimizedImageManager(context)       // Fallback für interne Speicherung
    private val performanceMonitor = StoragePerformanceMonitor()
    private val localImageStorage = LocalImageStorage(context)
    
    // Performance-Statistiken
    val performanceStats: StateFlow<PerformanceStats> = performanceMonitor.performanceStats
    val loadingStates: StateFlow<Map<String, Boolean>> = optimizedImageManager.loadingStates
    
    init {
        // Initialisiere Verzeichnisstruktur
        val initialized = directoryManager.initializeDirectories()
        if (initialized) {
            Log.i(TAG, "ArWalking Storage erfolgreich initialisiert")
            Log.i(TAG, "Speicher-Pfad: ${directoryManager.appFilesDir.absolutePath}")
        } else {
            Log.e(TAG, "Fehler bei der Storage-Initialisierung")
        }
    }
    
    // === BILD-SPEICHERUNG ===
    
    /**
     * Speichert ein Bild mit optimaler Performance
     * Ziel: <200ms für komplette Speicherung
     */
    suspend fun saveImage(
        bitmap: Bitmap,
        landmarkId: String,
        landmarkName: String,
        description: String,
        category: String = "Training",
        onProgress: (String) -> Unit = {}
    ): SaveResult = withContext(Dispatchers.IO) {
        
        performanceMonitor.measureOperation(
            StoragePerformanceMonitor.OP_SAVE_IMAGE,
            landmarkId
        ) {
            optimizedImageManager.saveImageOptimized(
                bitmap = bitmap,
                landmarkId = landmarkId,
                landmarkName = landmarkName,
                description = description,
                category = category,
                onProgress = onProgress
            )
        }
    }
    
    /**
     * Speichert Bild als Fallback lokal (für Upload-Warteschlange)
     */
    suspend fun saveImageForUpload(
        bitmap: Bitmap,
        landmarkId: String,
        landmarkName: String,
        description: String
    ): Boolean = withContext(Dispatchers.IO) {
        
        localImageStorage.saveImageLocally(
            bitmap = bitmap,
            landmarkId = landmarkId,
            landmarkName = landmarkName,
            description = description
        )
    }
    
    // === BILD-LADEN ===
    
    /**
     * Lädt ein Thumbnail (Ziel: 1-3ms aus Cache, 5-15ms von Datei)
     * Lädt zuerst aus dem Projektverzeichnis, dann aus interner Speicherung
     */
    suspend fun loadThumbnail(landmarkId: String): Bitmap? = withContext(Dispatchers.IO) {
        
        performanceMonitor.measureOperation(
            StoragePerformanceMonitor.OP_LOAD_THUMBNAIL,
            landmarkId
        ) {
            // 1. Versuche aus Projektverzeichnis zu laden
            projectImageManager.loadThumbnail(landmarkId) 
                ?: // 2. Fallback: Aus interner Speicherung
                optimizedImageManager.loadThumbnail(landmarkId)
        }
    }
    
    /**
     * Lädt ein Vollbild (Ziel: 5-15ms)
     * Lädt zuerst aus dem Projektverzeichnis, dann aus interner Speicherung
     */
    suspend fun loadFullImage(landmarkId: String): Bitmap? = withContext(Dispatchers.IO) {
        
        performanceMonitor.measureOperation(
            StoragePerformanceMonitor.OP_LOAD_FULL_IMAGE,
            landmarkId
        ) {
            // 1. Versuche aus Projektverzeichnis zu laden
            projectImageManager.loadFullImage(landmarkId)
                ?: // 2. Fallback: Aus interner Speicherung
                optimizedImageManager.loadFullImage(landmarkId)
        }
    }
    
    // === DATEN-ABFRAGE ===
    
    /**
     * Gibt Landmarks paginiert zurück (Ziel: <1ms für Suche)
     */
    suspend fun getLandmarksPaged(
        page: Int = 0,
        pageSize: Int = StorageConfig.PAGE_SIZE,
        searchQuery: String = "",
        category: String = ""
    ): OptimizedImageManager.PagedResult<OptimizedImageManager.LandmarkMetadata> = withContext(Dispatchers.IO) {
        
        performanceMonitor.measureOperation(
            StoragePerformanceMonitor.OP_SEARCH_INDEX,
            "page_${page}_query_${searchQuery}"
        ) {
            optimizedImageManager.getLandmarksPaged(
                page = page,
                pageSize = pageSize,
                searchQuery = searchQuery,
                category = category
            )
        }
    }
    
    /**
     * Gibt alle Kategorien zurück
     */
    suspend fun getCategories(): List<String> = withContext(Dispatchers.IO) {
        val allLandmarks = optimizedImageManager.getLandmarksPaged(page = 0, pageSize = Int.MAX_VALUE)
        allLandmarks.items.map { it.category }.distinct().sorted()
    }
    
    /**
     * Sucht Landmarks nach Text
     */
    suspend fun searchLandmarks(query: String): List<OptimizedImageManager.LandmarkMetadata> = withContext(Dispatchers.IO) {
        val result = optimizedImageManager.getLandmarksPaged(
            page = 0,
            pageSize = Int.MAX_VALUE,
            searchQuery = query
        )
        result.items
    }
    
    // === PROJEKTVERZEICHNIS-SPEZIFISCHE METHODEN ===
    
    /**
     * Gibt alle verfügbaren Landmarks aus dem Projektverzeichnis zurück
     */
    suspend fun getAvailableProjectLandmarks(): List<LandmarkInfo> = withContext(Dispatchers.IO) {
        projectImageManager.getAvailableLandmarks()
    }
    
    /**
     * Prüft ob ein Landmark-Bild im Projektverzeichnis existiert
     */
    fun hasProjectLandmarkImage(landmarkId: String): Boolean {
        return projectImageManager.hasLandmarkImage(landmarkId)
    }
    
    /**
     * Gibt Informationen über ein Projekt-Landmark zurück
     */
    suspend fun getProjectLandmarkInfo(landmarkId: String): LandmarkInfo? = withContext(Dispatchers.IO) {
        projectImageManager.getLandmarkInfo(landmarkId)
    }
    
    /**
     * Gibt Cache-Statistiken für das Projektverzeichnis zurück
     */
    fun getProjectCacheStats(): CacheStats {
        return projectImageManager.getCacheStats()
    }
    
    // === VERWALTUNG ===
    
    /**
     * Löscht ein Landmark komplett
     */
    suspend fun deleteLandmark(landmarkId: String): Boolean = withContext(Dispatchers.IO) {
        optimizedImageManager.deleteLandmark(landmarkId)
    }
    
    /**
     * Bereinigt Cache und defekte Dateien
     */
    suspend fun cleanup(): CleanupSummary = withContext(Dispatchers.IO) {
        val imageCleanup = optimizedImageManager.cleanup()
        val directoryCleanup = directoryManager.cleanupDirectories()
        CleanupSummary(
            imageManagerCleanup = imageCleanup,
            directoryCleanup = directoryCleanup,
            totalFilesRemoved = imageCleanup.removedFiles + directoryCleanup.getTotalCleaned(),
            totalSpaceFreedMB = 0.0 // Placeholder - could be calculated if needed
        )
    }
    
    // === STATUS & MONITORING ===
    
    /**
     * Gibt aktuellen Storage-Status zurück
     */
    suspend fun getStorageStatus(): StorageStatus = withContext(Dispatchers.IO) {
        val directoryStatus = directoryManager.checkDirectoryIntegrity()
        val performanceStats = performanceMonitor.performanceStats.value
        
        StorageStatus(
            directoryStatus = directoryStatus,
            performanceStats = performanceStats,
            isHealthy = directoryStatus.isHealthy && performanceStats.isHealthy,
            totalImages = directoryStatus.imageCount,
            totalSizeMB = directoryStatus.getTotalSizeMB(),
            cacheHitRate = performanceStats.cacheHitRate,
            averageLoadTimeMs = performanceStats.loadFullImageStats?.averageDurationMs ?: 0.0
        )
    }
    
    /**
     * Schätzt Speicherverbrauch für gegebene Anzahl Bilder
     */
    fun estimateStorageUsage(imageCount: Int): StorageEstimate {
        return StorageConfig.estimateStorageUsage(imageCount)
    }
    
    /**
     * Berechnet optimale Cache-Konfiguration
     */
    fun calculateOptimalCacheConfig(availableMemoryMB: Int): CacheConfig {
        return StorageConfig.calculateOptimalCacheSize(availableMemoryMB)
    }
    
    /**
     * Loggt Performance-Zusammenfassung
     */
    fun logPerformanceSummary() {
        performanceMonitor.logPerformanceSummary()
        projectImageManager.logPerformanceSummary()
    }
    
    /**
     * Setzt Performance-Metriken zurück
     */
    fun resetPerformanceMetrics() {
        performanceMonitor.resetMetrics()
    }
    
    // === UPLOAD-WARTESCHLANGE ===
    
    /**
     * Gibt wartende Uploads zurück
     */
    suspend fun getPendingUploads(): List<PendingUpload> = withContext(Dispatchers.IO) {
        localImageStorage.getPendingUploads()
    }
    
    /**
     * Markiert Upload als erfolgreich
     */
    suspend fun markUploadAsCompleted(pendingUpload: PendingUpload): Boolean = withContext(Dispatchers.IO) {
        localImageStorage.markAsUploaded(pendingUpload)
    }
    
    /**
     * Bereinigt nach erfolgreichem Upload
     */
    suspend fun cleanupAfterUpload(pendingUpload: PendingUpload): Boolean = withContext(Dispatchers.IO) {
        localImageStorage.cleanupAfterUpload(pendingUpload)
    }
    
    /**
     * Gibt Anzahl wartender Uploads zurück
     */
    suspend fun getPendingUploadCount(): Int = withContext(Dispatchers.IO) {
        localImageStorage.getPendingUploadCount()
    }
    
    // === FEATURE MAPS ===
    
    /**
     * Speichert Feature Map für ein Gebäude/Stockwerk
     */
    suspend fun saveFeatureMap(buildingId: String, floor: Int, featureMapData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val featureMapFile = directoryManager.getFeatureMapFile(buildingId, floor)
            featureMapFile.writeText(featureMapData)
            Log.i(TAG, "Feature Map gespeichert: ${featureMapFile.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern der Feature Map: ${e.message}")
            false
        }
    }
    
    /**
     * Lädt Feature Map für ein Gebäude/Stockwerk
     */
    suspend fun loadFeatureMap(buildingId: String, floor: Int): String? = withContext(Dispatchers.IO) {
        try {
            val featureMapFile = directoryManager.getFeatureMapFile(buildingId, floor)
            if (featureMapFile.exists()) {
                featureMapFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der Feature Map: ${e.message}")
            null
        }
    }
}

/**
 * Zusammenfassung der Bereinigung
 */
data class CleanupSummary(
    val imageManagerCleanup: ImageCleanupResult,
    val directoryCleanup: DirectoryCleanupResult,
    val totalFilesRemoved: Int,
    val totalSpaceFreedMB: Double
)

/**
 * Gesamt-Status des Storage-Systems
 */
data class StorageStatus(
    val directoryStatus: DirectoryStatus,
    val performanceStats: PerformanceStats,
    val isHealthy: Boolean,
    val totalImages: Int,
    val totalSizeMB: Double,
    val cacheHitRate: Double,
    val averageLoadTimeMs: Double
) {
    fun getHealthStatus(): String = when {
        isHealthy && cacheHitRate > 80 && averageLoadTimeMs < 15 -> "Excellent"
        isHealthy && cacheHitRate > 60 && averageLoadTimeMs < 25 -> "Good"
        isHealthy -> "Fair"
        else -> "Poor"
    }
}