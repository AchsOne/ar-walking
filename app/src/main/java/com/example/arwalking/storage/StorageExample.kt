package com.example.arwalking.storage

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Zeigt den Einsatz des ArWalking Storage Systems
class StorageExample(private val context: Context) {
    
    private val TAG = "StorageExample"
    private val storageManager = ArWalkingStorageManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Speichert ein Landmark-Bild und zeigt Fortschritt
    fun saveImageExample(bitmap: Bitmap, landmarkName: String) {
        scope.launch {
            val landmarkId = "landmark_${System.currentTimeMillis()}"
            
            val result = storageManager.saveImage(
                bitmap = bitmap,
                landmarkId = landmarkId,
                landmarkName = landmarkName,
                description = "Automatisch erfasst am ${java.util.Date()}",
                category = "Training"
            ) { progress ->
                Log.d(TAG, "Speicher-Progress: $progress")
            }
            
            when (result) {
                is SaveResult.Success -> {
                    Log.i(TAG, "✅ Bild erfolgreich gespeichert: ${result.message}")
                    
                    // Sofort Thumbnail laden (sollte 1-3ms dauern)
                    val thumbnail = storageManager.loadThumbnail(landmarkId)
                    if (thumbnail != null) {
                        Log.i(TAG, "✅ Thumbnail geladen: ${thumbnail.width}x${thumbnail.height}")
                    }
                }
                is SaveResult.Error -> {
                    Log.e(TAG, "❌ Fehler beim Speichern: ${result.message}")
                }
            }
        }
    }
    
    // Lädt Landmark-Bilder paginiert
    fun loadImageListExample() {
        scope.launch {
            var page = 0
            var hasMore = true
            
            while (hasMore) {
                val result = storageManager.getLandmarksPaged(
                    page = page,
                    pageSize = 20,
                    searchQuery = "",
                    category = ""
                )
                
                Log.i(TAG, "Seite $page: ${result.items.size} Bilder geladen")
                Log.i(TAG, "Gesamt: ${result.totalCount} Bilder verfügbar")
                
                // Verarbeite Bilder
                result.items.forEach { landmark ->
                    Log.d(TAG, "- ${landmark.name} (${landmark.category})")
                    
                    // Lade Thumbnail für jedes Bild
                    val thumbnail = storageManager.loadThumbnail(landmark.id)
                    if (thumbnail != null) {
                        Log.d(TAG, "  Thumbnail: ${thumbnail.width}x${thumbnail.height}")
                    }
                }
                
                hasMore = result.hasMore
                page++
                
                // Sicherheits-Stopp bei zu vielen Seiten
                if (page > 50) break
            }
        }
    }
    
    // Sucht gespeicherte Landmark-Bilder
    fun searchImagesExample(query: String) {
        scope.launch {
            val searchResults = storageManager.searchLandmarks(query)
            
            Log.i(TAG, "Suchergebnisse für '$query': ${searchResults.size} gefunden")
            
            searchResults.take(5).forEach { landmark ->
                Log.i(TAG, "- ${landmark.name}: ${landmark.description}")
                
                // Lade Vollbild für die ersten 3 Ergebnisse
                if (searchResults.indexOf(landmark) < 3) {
                    val fullImage = storageManager.loadFullImage(landmark.id)
                    if (fullImage != null) {
                        Log.i(TAG, "  Vollbild: ${fullImage.width}x${fullImage.height}")
                    }
                }
            }
        }
    }
    
    // Misst Ladezeiten und fasst sie zusammen
    fun performanceMonitoringExample() {
        scope.launch {
            // Lade mehrere Bilder und messe Performance
            val testImages = listOf("landmark_001", "landmark_002", "landmark_003")
            
            testImages.forEach { landmarkId ->
                // Messe Thumbnail-Ladezeit
                val startTime = System.currentTimeMillis()
                val thumbnail = storageManager.loadThumbnail(landmarkId)
                val thumbnailTime = System.currentTimeMillis() - startTime
                
                if (thumbnail != null) {
                    Log.d(TAG, "Thumbnail $landmarkId: ${thumbnailTime}ms")
                }
                
                // Messe Vollbild-Ladezeit
                val fullStartTime = System.currentTimeMillis()
                val fullImage = storageManager.loadFullImage(landmarkId)
                val fullTime = System.currentTimeMillis() - fullStartTime
                
                if (fullImage != null) {
                    Log.d(TAG, "Vollbild $landmarkId: ${fullTime}ms")
                }
            }
            
            // Performance-Zusammenfassung ausgeben
            storageManager.logPerformanceSummary()
            
            // Storage-Status prüfen
            val status = storageManager.getStorageStatus()
            Log.i(TAG, "=== STORAGE STATUS ===")
            Log.i(TAG, "Gesundheit: ${status.getHealthStatus()}")
            Log.i(TAG, "Bilder: ${status.totalImages}")
            Log.i(TAG, "Größe: ${String.format("%.1f", status.totalSizeMB)} MB")
            Log.i(TAG, "Cache-Hit-Rate: ${String.format("%.1f", status.cacheHitRate)}%")
            Log.i(TAG, "Ø Ladezeit: ${String.format("%.1f", status.averageLoadTimeMs)}ms")
        }
    }
    
    // Startet eine Bereinigung alter Dateien
    fun cleanupExample() {
        scope.launch {
            Log.i(TAG, "Starte Speicher-Bereinigung...")
            
            val cleanupResult = storageManager.cleanup()
            
            Log.i(TAG, "=== BEREINIGUNG ABGESCHLOSSEN ===")
            Log.i(TAG, "Entfernte Dateien: ${cleanupResult.totalFilesRemoved}")
            Log.i(TAG, "Freigegebener Speicher: ${String.format("%.1f", cleanupResult.totalSpaceFreedMB)} MB")
            Log.i(TAG, "Verwaiste Thumbnails: ${cleanupResult.directoryCleanup.orphanedThumbnails}")
            Log.i(TAG, "Verwaiste Metadaten: ${cleanupResult.directoryCleanup.orphanedMetadata}")
            Log.i(TAG, "Defekte Dateien: ${cleanupResult.directoryCleanup.corruptedFiles}")
        }
    }
    
    /**
     * Beispiel 6: Upload-Warteschlange verwalten
     */
    fun uploadQueueExample() {
        scope.launch {
            // Prüfe wartende Uploads
            val pendingCount = storageManager.getPendingUploadCount()
            Log.i(TAG, "Wartende Uploads: $pendingCount")
            
            if (pendingCount > 0) {
                val pendingUploads = storageManager.getPendingUploads()
                
                pendingUploads.forEach { upload ->
                    Log.i(TAG, "Upload: ${upload.landmarkName} (${upload.landmarkId})")
                    
                    // Simuliere erfolgreichen Upload
                    val success = simulateUpload(upload)
                    
                    if (success) {
                        // Markiere als hochgeladen
                        storageManager.markUploadAsCompleted(upload)
                        
                        // Optional: Bereinige lokale Dateien
                        storageManager.cleanupAfterUpload(upload)
                        
                        Log.i(TAG, "✅ Upload erfolgreich: ${upload.landmarkName}")
                    } else {
                        Log.e(TAG, "❌ Upload fehlgeschlagen: ${upload.landmarkName}")
                    }
                }
            }
        }
    }
    
    /**
     * Beispiel 7: Feature Maps verwalten
     */
    fun featureMapExample() {
        scope.launch {
            val buildingId = "building_a"
            val floor = 0
            
            // Speichere Feature Map
            val featureMapData = """
                {
                    "building": "$buildingId",
                    "floor": $floor,
                    "features": [
                        {"id": "feature_001", "x": 100, "y": 200},
                        {"id": "feature_002", "x": 300, "y": 400}
                    ],
                    "created": "${System.currentTimeMillis()}"
                }
            """.trimIndent()
            
            val saved = storageManager.saveFeatureMap(buildingId, floor, featureMapData)
            if (saved) {
                Log.i(TAG, "✅ Feature Map gespeichert: $buildingId, Stockwerk $floor")
                
                // Lade Feature Map wieder
                val loadedData = storageManager.loadFeatureMap(buildingId, floor)
                if (loadedData != null) {
                    Log.i(TAG, "✅ Feature Map geladen: ${loadedData.length} Zeichen")
                }
            }
        }
    }
    
    /**
     * Beispiel 8: Speicher-Schätzung
     */
    fun storageEstimationExample() {
        // Schätze Speicherverbrauch für verschiedene Bildanzahlen
        val imageCounts = listOf(10, 50, 100, 500, 1000)
        
        Log.i(TAG, "=== SPEICHER-SCHÄTZUNGEN ===")
        imageCounts.forEach { count ->
            val estimate = storageManager.estimateStorageUsage(count)
            Log.i(TAG, "$count Bilder: ${String.format("%.1f", estimate.totalSizeMB)} MB")
        }
        
        // Berechne optimale Cache-Konfiguration
        val availableMemoryMB = 512 // Beispiel: 512 MB verfügbarer RAM
        val cacheConfig = storageManager.calculateOptimalCacheConfig(availableMemoryMB)
        
        Log.i(TAG, "=== CACHE-OPTIMIERUNG ===")
        Log.i(TAG, "Verfügbarer RAM: ${availableMemoryMB} MB")
        Log.i(TAG, "Optimale Bitmap-Cache-Größe: ${cacheConfig.bitmapCacheSize}")
        Log.i(TAG, "Optimale Thumbnail-Cache-Größe: ${cacheConfig.thumbnailCacheSize}")
        Log.i(TAG, "Geschätzter RAM-Verbrauch: ${cacheConfig.estimatedMemoryUsageMB} MB")
    }
    
    /**
     * Simuliert einen Upload (für Demo-Zwecke)
     */
    private suspend fun simulateUpload(upload: PendingUpload): Boolean {
        // Simuliere Upload-Zeit
        kotlinx.coroutines.delay(100)
        
        // 90% Erfolgsrate für Demo
        return Math.random() > 0.1
    }
    
    /**
     * Führt alle Beispiele aus
     */
    fun runAllExamples() {
        Log.i(TAG, "Starte ArWalking Storage Beispiele...")
        
        performanceMonitoringExample()
        cleanupExample()
        uploadQueueExample()
        featureMapExample()
        storageEstimationExample()
        
        Log.i(TAG, "✅ Alle Beispiele abgeschlossen!")
    }
}