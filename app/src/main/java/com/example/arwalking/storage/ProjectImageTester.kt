package com.example.arwalking.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Test-Klasse für das Projektverzeichnis-Image-System
 * Testet ob Bilder korrekt aus /Users/florian/Documents/GitHub/ar-walking/landmark_images/ geladen werden
 */
class ProjectImageTester(private val context: Context) {
    
    private val TAG = "ProjectImageTester"
    private val storageManager = ArWalkingStorageManager(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Führt alle Tests aus
     */
    fun runAllTests() {
        Log.i(TAG, "🧪 Starte Projektverzeichnis-Tests...")
        
        scope.launch {
            testProjectDirectoryAccess()
            testAvailableImages()
            testImageLoading()
            testPerformance()
            testCacheEfficiency()
            
            Log.i(TAG, "✅ Alle Tests abgeschlossen!")
        }
    }
    
    /**
     * Test 1: Projektverzeichnis-Zugriff
     */
    private suspend fun testProjectDirectoryAccess() {
        Log.i(TAG, "=== TEST 1: Projektverzeichnis-Zugriff ===")
        
        try {
            val landmarks = storageManager.getAvailableProjectLandmarks()
            
            if (landmarks.isEmpty()) {
                Log.w(TAG, "⚠️ Keine Bilder im Projektverzeichnis gefunden!")
                Log.i(TAG, "📁 Pfad: ${ProjectDirectoryImageManager.PROJECT_LANDMARK_IMAGES_PATH}")
                Log.i(TAG, "💡 Tipp: Kopiere Bilder in das landmark_images Verzeichnis")
            } else {
                Log.i(TAG, "✅ ${landmarks.size} Bilder im Projektverzeichnis gefunden:")
                landmarks.forEach { landmark ->
                    Log.i(TAG, "  - ${landmark.id} (${landmark.filename}, ${String.format("%.1f", landmark.getFileSizeKB())} KB)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Zugriff auf Projektverzeichnis: ${e.message}")
        }
    }
    
    /**
     * Test 2: Verfügbare Bilder auflisten
     */
    private suspend fun testAvailableImages() {
        Log.i(TAG, "=== TEST 2: Verfügbare Bilder ===")
        
        try {
            val landmarks = storageManager.getAvailableProjectLandmarks()
            
            Log.i(TAG, "Gefundene Landmark-IDs:")
            landmarks.forEach { landmark ->
                val hasImage = storageManager.hasProjectLandmarkImage(landmark.id)
                val status = if (hasImage) "✅" else "❌"
                Log.i(TAG, "  $status ${landmark.id}")
                
                // Detaillierte Informationen
                val info = storageManager.getProjectLandmarkInfo(landmark.id)
                if (info != null) {
                    Log.d(TAG, "    Datei: ${info.filename}")
                    Log.d(TAG, "    Größe: ${String.format("%.1f", info.getFileSizeMB())} MB")
                    Log.d(TAG, "    Geändert: ${info.getLastModifiedDate()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Auflisten der Bilder: ${e.message}")
        }
    }
    
    /**
     * Test 3: Bild-Laden testen
     */
    private suspend fun testImageLoading() {
        Log.i(TAG, "=== TEST 3: Bild-Laden ===")
        
        try {
            val landmarks = storageManager.getAvailableProjectLandmarks()
            
            if (landmarks.isEmpty()) {
                Log.w(TAG, "⚠️ Keine Bilder zum Testen verfügbar")
                return
            }
            
            // Teste die ersten 3 Bilder
            landmarks.take(3).forEach { landmark ->
                Log.i(TAG, "Teste Bild: ${landmark.id}")
                
                // Teste Vollbild-Laden
                val startTime = System.currentTimeMillis()
                val fullImage = storageManager.loadFullImage(landmark.id)
                val fullLoadTime = System.currentTimeMillis() - startTime
                
                if (fullImage != null) {
                    Log.i(TAG, "  ✅ Vollbild geladen: ${fullImage.width}x${fullImage.height} (${fullLoadTime}ms)")
                } else {
                    Log.e(TAG, "  ❌ Vollbild konnte nicht geladen werden")
                }
                
                // Teste Thumbnail-Laden
                val thumbStartTime = System.currentTimeMillis()
                val thumbnail = storageManager.loadThumbnail(landmark.id)
                val thumbLoadTime = System.currentTimeMillis() - thumbStartTime
                
                if (thumbnail != null) {
                    Log.i(TAG, "  ✅ Thumbnail geladen: ${thumbnail.width}x${thumbnail.height} (${thumbLoadTime}ms)")
                } else {
                    Log.e(TAG, "  ❌ Thumbnail konnte nicht geladen werden")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Bild-Laden: ${e.message}")
        }
    }
    
    /**
     * Test 4: Performance-Test
     */
    private suspend fun testPerformance() {
        Log.i(TAG, "=== TEST 4: Performance ===")
        
        try {
            val landmarks = storageManager.getAvailableProjectLandmarks()
            
            if (landmarks.isEmpty()) {
                Log.w(TAG, "⚠️ Keine Bilder für Performance-Test verfügbar")
                return
            }
            
            val testLandmark = landmarks.first()
            val loadTimes = mutableListOf<Long>()
            
            // Führe 5 Ladevorgänge durch
            repeat(5) { iteration ->
                val startTime = System.currentTimeMillis()
                val image = storageManager.loadFullImage(testLandmark.id)
                val loadTime = System.currentTimeMillis() - startTime
                
                if (image != null) {
                    loadTimes.add(loadTime)
                    Log.d(TAG, "  Ladevorgang ${iteration + 1}: ${loadTime}ms")
                }
            }
            
            if (loadTimes.isNotEmpty()) {
                val avgTime = loadTimes.average()
                val minTime = loadTimes.minOrNull() ?: 0L
                val maxTime = loadTimes.maxOrNull() ?: 0L
                
                Log.i(TAG, "Performance-Ergebnisse für ${testLandmark.id}:")
                Log.i(TAG, "  Durchschnitt: ${String.format("%.1f", avgTime)}ms")
                Log.i(TAG, "  Minimum: ${minTime}ms")
                Log.i(TAG, "  Maximum: ${maxTime}ms")
                
                // Bewertung
                val target = StorageConfig.TARGET_LOCAL_LOAD_TIME_MS
                if (avgTime <= target) {
                    Log.i(TAG, "  ✅ Performance-Ziel erreicht (Ziel: ${target}ms)")
                } else {
                    Log.w(TAG, "  ⚠️ Performance-Ziel verfehlt (Ziel: ${target}ms)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Performance-Test: ${e.message}")
        }
    }
    
    /**
     * Test 5: Cache-Effizienz
     */
    private suspend fun testCacheEfficiency() {
        Log.i(TAG, "=== TEST 5: Cache-Effizienz ===")
        
        try {
            val landmarks = storageManager.getAvailableProjectLandmarks()
            
            if (landmarks.isEmpty()) {
                Log.w(TAG, "⚠️ Keine Bilder für Cache-Test verfügbar")
                return
            }
            
            val testLandmark = landmarks.first()
            
            // Erster Zugriff (Cache Miss)
            val firstStartTime = System.currentTimeMillis()
            val firstImage = storageManager.loadThumbnail(testLandmark.id)
            val firstLoadTime = System.currentTimeMillis() - firstStartTime
            
            // Zweiter Zugriff (Cache Hit)
            val secondStartTime = System.currentTimeMillis()
            val secondImage = storageManager.loadThumbnail(testLandmark.id)
            val secondLoadTime = System.currentTimeMillis() - secondStartTime
            
            if (firstImage != null && secondImage != null) {
                Log.i(TAG, "Cache-Test für ${testLandmark.id}:")
                Log.i(TAG, "  1. Zugriff (Cache Miss): ${firstLoadTime}ms")
                Log.i(TAG, "  2. Zugriff (Cache Hit): ${secondLoadTime}ms")
                
                val improvement = firstLoadTime - secondLoadTime
                val improvementPercent = if (firstLoadTime > 0) (improvement.toDouble() / firstLoadTime) * 100 else 0.0
                
                if (improvement > 0) {
                    Log.i(TAG, "  ✅ Cache-Verbesserung: ${improvement}ms (${String.format("%.1f", improvementPercent)}%)")
                } else {
                    Log.w(TAG, "  ⚠️ Keine Cache-Verbesserung erkennbar")
                }
                
                // Cache-Statistiken
                val cacheStats = storageManager.getProjectCacheStats()
                Log.i(TAG, "Cache-Statistiken:")
                Log.i(TAG, "  Bitmap Hit Rate: ${String.format("%.1f", cacheStats.getBitmapHitRate())}%")
                Log.i(TAG, "  Thumbnail Hit Rate: ${String.format("%.1f", cacheStats.getThumbnailHitRate())}%")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Cache-Test: ${e.message}")
        }
    }
    
    /**
     * Gibt eine Zusammenfassung des aktuellen Zustands aus
     */
    fun logCurrentStatus() {
        scope.launch {
            Log.i(TAG, "=== AKTUELLER STATUS ===")
            
            try {
                val landmarks = storageManager.getAvailableProjectLandmarks()
                Log.i(TAG, "Verfügbare Bilder: ${landmarks.size}")
                
                if (landmarks.isNotEmpty()) {
                    val totalSize = landmarks.sumOf { it.fileSize }
                    val avgSize = totalSize.toDouble() / landmarks.size
                    
                    Log.i(TAG, "Gesamtgröße: ${String.format("%.1f", totalSize / (1024.0 * 1024.0))} MB")
                    Log.i(TAG, "Durchschnittsgröße: ${String.format("%.1f", avgSize / 1024.0)} KB")
                    
                    // Zeige die ersten 5 Bilder
                    Log.i(TAG, "Erste 5 Bilder:")
                    landmarks.take(5).forEach { landmark ->
                        Log.i(TAG, "  - ${landmark.id} (${String.format("%.1f", landmark.getFileSizeKB())} KB)")
                    }
                }
                
                // Performance-Zusammenfassung
                storageManager.logPerformanceSummary()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fehler beim Status-Log: ${e.message}")
            }
        }
    }
}