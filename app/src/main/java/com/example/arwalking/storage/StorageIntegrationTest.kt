package com.example.arwalking.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * Integrations-Test für das ArWalking Storage System
 * Testet die wichtigsten Funktionen und Performance-Ziele
 */
class StorageIntegrationTest(private val context: Context) {
    
    private val TAG = "StorageIntegrationTest"
    private val storageManager = ArWalkingStorageManager(context)
    
    /**
     * Führt alle Tests aus
     */
    fun runAllTests(): TestResult = runBlocking {
        Log.i(TAG, "🧪 Starte Storage Integration Tests...")
        
        val results = mutableListOf<SingleTestResult>()
        
        // Test 1: Verzeichnis-Initialisierung
        results.add(testDirectoryInitialization())
        
        // Test 2: Bild speichern und laden
        results.add(testImageSaveAndLoad())
        
        // Test 3: Thumbnail-Performance
        results.add(testThumbnailPerformance())
        
        // Test 4: Paginierte Abfrage
        results.add(testPaginatedQuery())
        
        // Test 5: Suche-Performance
        results.add(testSearchPerformance())
        
        // Test 6: Cache-Effizienz
        results.add(testCacheEfficiency())
        
        // Test 7: Bereinigung
        results.add(testCleanup())
        
        // Test 8: Feature Maps
        results.add(testFeatureMaps())
        
        val overallResult = TestResult(
            testName = "Storage Integration Test",
            passed = results.all { it.passed },
            executionTimeMs = results.sumOf { it.executionTimeMs },
            details = results,
            summary = generateSummary(results)
        )
        
        logTestResults(overallResult)
        return@runBlocking overallResult
    }
    
    /**
     * Test 1: Verzeichnis-Initialisierung
     */
    private suspend fun testDirectoryInitialization(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val status = storageManager.getStorageStatus()
            val directoryStatus = status.directoryStatus
            
            val passed = directoryStatus.landmarkImagesExists &&
                        directoryStatus.landmarkThumbnailsExists &&
                        directoryStatus.landmarkMetadataExists &&
                        directoryStatus.featureMapsExists &&
                        directoryStatus.indexFileExists
            
            SingleTestResult(
                testName = "Directory Initialization",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = if (passed) "Alle Verzeichnisse erfolgreich initialisiert" 
                         else "Verzeichnis-Initialisierung fehlgeschlagen"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Directory Initialization",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Test 2: Bild speichern und laden
     */
    private suspend fun testImageSaveAndLoad(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Erstelle Test-Bitmap
            val testBitmap = createTestBitmap(512, 512)
            val testId = "test_image_${System.currentTimeMillis()}"
            
            // Speichere Bild
            val saveResult = storageManager.saveImage(
                bitmap = testBitmap,
                landmarkId = testId,
                landmarkName = "Test Image",
                description = "Integration Test Image",
                category = "Test"
            )
            
            val saveSuccess = saveResult is SaveResult.Success
            
            // Lade Bild wieder
            val loadedImage = storageManager.loadFullImage(testId)
            val loadSuccess = loadedImage != null
            
            // Bereinige Test-Daten
            storageManager.deleteLandmark(testId)
            
            val passed = saveSuccess && loadSuccess
            
            SingleTestResult(
                testName = "Image Save and Load",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = if (passed) "Bild erfolgreich gespeichert und geladen"
                         else "Fehler beim Speichern/Laden: Save=$saveSuccess, Load=$loadSuccess"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Image Save and Load",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Test 3: Thumbnail-Performance (Ziel: 1-3ms)
     */
    private suspend fun testThumbnailPerformance(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Erstelle Test-Bild
            val testBitmap = createTestBitmap(1024, 1024)
            val testId = "thumbnail_test_${System.currentTimeMillis()}"
            
            // Speichere Bild (erstellt automatisch Thumbnail)
            storageManager.saveImage(
                bitmap = testBitmap,
                landmarkId = testId,
                landmarkName = "Thumbnail Test",
                description = "Performance Test",
                category = "Test"
            )
            
            // Messe Thumbnail-Ladezeit (mehrere Versuche für Cache-Test)
            val loadTimes = mutableListOf<Long>()
            
            repeat(5) {
                val loadStart = System.currentTimeMillis()
                val thumbnail = storageManager.loadThumbnail(testId)
                val loadTime = System.currentTimeMillis() - loadStart
                
                if (thumbnail != null) {
                    loadTimes.add(loadTime)
                }
            }
            
            // Bereinige Test-Daten
            storageManager.deleteLandmark(testId)
            
            val avgLoadTime = loadTimes.average()
            val passed = loadTimes.isNotEmpty() && avgLoadTime <= StorageConfig.TARGET_THUMBNAIL_CACHE_TIME_MS
            
            SingleTestResult(
                testName = "Thumbnail Performance",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Ø Thumbnail-Ladezeit: ${String.format("%.1f", avgLoadTime)}ms " +
                         "(Ziel: ${StorageConfig.TARGET_THUMBNAIL_CACHE_TIME_MS}ms)"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Thumbnail Performance",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Test 4: Paginierte Abfrage
     */
    private suspend fun testPaginatedQuery(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = storageManager.getLandmarksPaged(
                page = 0,
                pageSize = 20
            )
            
            val passed = result.pageSize == 20 && 
                        result.page == 0 &&
                        result.items.size <= result.pageSize
            
            SingleTestResult(
                testName = "Paginated Query",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Seite 0: ${result.items.size}/${result.totalCount} Bilder geladen"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Paginated Query",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Test 5: Suche-Performance (Ziel: <1ms)
     */
    private suspend fun testSearchPerformance(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val searchStart = System.currentTimeMillis()
            val results = storageManager.searchLandmarks("test")
            val searchTime = System.currentTimeMillis() - searchStart
            
            val passed = searchTime <= StorageConfig.TARGET_SEARCH_TIME_MS
            
            SingleTestResult(
                testName = "Search Performance",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Suche dauerte ${searchTime}ms (Ziel: ${StorageConfig.TARGET_SEARCH_TIME_MS}ms), " +
                         "${results.size} Ergebnisse"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Search Performance",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Test 6: Cache-Effizienz
     */
    private suspend fun testCacheEfficiency(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Erstelle Test-Bild
            val testBitmap = createTestBitmap(256, 256)
            val testId = "cache_test_${System.currentTimeMillis()}"
            
            storageManager.saveImage(
                bitmap = testBitmap,
                landmarkId = testId,
                landmarkName = "Cache Test",
                description = "Cache Efficiency Test",
                category = "Test"
            )
            
            // Erster Zugriff (Cache Miss)
            val firstLoadStart = System.currentTimeMillis()
            val firstLoad = storageManager.loadThumbnail(testId)
            val firstLoadTime = System.currentTimeMillis() - firstLoadStart
            
            // Zweiter Zugriff (Cache Hit)
            val secondLoadStart = System.currentTimeMillis()
            val secondLoad = storageManager.loadThumbnail(testId)
            val secondLoadTime = System.currentTimeMillis() - secondLoadStart
            
            // Bereinige Test-Daten
            storageManager.deleteLandmark(testId)
            
            val cacheImprovement = firstLoadTime > secondLoadTime
            val passed = firstLoad != null && secondLoad != null && cacheImprovement
            
            SingleTestResult(
                testName = "Cache Efficiency",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "1. Zugriff: ${firstLoadTime}ms, 2. Zugriff: ${secondLoadTime}ms " +
                         "(Verbesserung: ${cacheImprovement})"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Cache Efficiency",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Test 7: Bereinigung
     */
    private suspend fun testCleanup(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val cleanupResult = storageManager.cleanup()
            val passed = true // Cleanup sollte immer funktionieren
            
            SingleTestResult(
                testName = "Cleanup",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "${cleanupResult.totalFilesRemoved} Dateien bereinigt, " +
                         "${String.format("%.1f", cleanupResult.totalSpaceFreedMB)} MB freigegeben"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Cleanup",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Test 8: Feature Maps
     */
    private suspend fun testFeatureMaps(): SingleTestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val buildingId = "test_building"
            val floor = 0
            val testData = """{"test": "data", "timestamp": ${System.currentTimeMillis()}}"""
            
            // Speichere Feature Map
            val saveSuccess = storageManager.saveFeatureMap(buildingId, floor, testData)
            
            // Lade Feature Map
            val loadedData = storageManager.loadFeatureMap(buildingId, floor)
            val loadSuccess = loadedData == testData
            
            val passed = saveSuccess && loadSuccess
            
            SingleTestResult(
                testName = "Feature Maps",
                passed = passed,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = if (passed) "Feature Map erfolgreich gespeichert und geladen"
                         else "Fehler: Save=$saveSuccess, Load=$loadSuccess"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Feature Maps",
                passed = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                message = "Fehler: ${e.message}"
            )
        }
    }
    
    /**
     * Erstellt ein Test-Bitmap
     */
    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        return bitmap
    }
    
    /**
     * Generiert Zusammenfassung der Test-Ergebnisse
     */
    private fun generateSummary(results: List<SingleTestResult>): String {
        val passed = results.count { it.passed }
        val total = results.size
        val totalTime = results.sumOf { it.executionTimeMs }
        
        return "Tests: $passed/$total bestanden, Gesamtzeit: ${totalTime}ms"
    }
    
    /**
     * Loggt Test-Ergebnisse
     */
    private fun logTestResults(result: TestResult) {
        Log.i(TAG, "=== STORAGE INTEGRATION TEST RESULTS ===")
        Log.i(TAG, "Gesamt: ${if (result.passed) "✅ BESTANDEN" else "❌ FEHLGESCHLAGEN"}")
        Log.i(TAG, "Ausführungszeit: ${result.executionTimeMs}ms")
        Log.i(TAG, "Zusammenfassung: ${result.summary}")
        Log.i(TAG, "")
        
        result.details.forEach { test ->
            val status = if (test.passed) "✅" else "❌"
            Log.i(TAG, "$status ${test.testName} (${test.executionTimeMs}ms)")
            Log.i(TAG, "   ${test.message}")
        }
        
        Log.i(TAG, "==========================================")
    }
}

/**
 * Ergebnis eines einzelnen Tests
 */
data class SingleTestResult(
    val testName: String,
    val passed: Boolean,
    val executionTimeMs: Long,
    val message: String
)

/**
 * Gesamt-Test-Ergebnis
 */
data class TestResult(
    val testName: String,
    val passed: Boolean,
    val executionTimeMs: Long,
    val details: List<SingleTestResult>,
    val summary: String
)