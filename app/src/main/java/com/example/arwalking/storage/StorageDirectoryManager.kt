package com.example.arwalking.storage

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Verwaltet die Verzeichnisstruktur für die lokale Speicherung
 * Basierend auf der STORAGE_ARCHITECTURE.md Spezifikation
 */
class StorageDirectoryManager(private val context: Context) {
    
    private val TAG = "StorageDirectoryManager"
    
    companion object {
        // Verzeichnis-Namen basierend auf STORAGE_ARCHITECTURE.md
        const val LANDMARK_IMAGES_DIR = "landmark_images"
        const val LANDMARK_THUMBNAILS_DIR = "landmark_thumbnails"
        const val LANDMARK_METADATA_DIR = "landmark_metadata"
        const val FEATURE_MAPS_DIR = "feature_maps"
        const val LANDMARK_INDEX_FILE = "landmark_index.json"
    }
    
    /**
     * Hauptverzeichnis für alle App-Daten
     * /data/data/com.example.arwalking/files/
     */
    val appFilesDir: File = context.filesDir
    
    /**
     * Verzeichnis für Vollbilder (optimiert)
     * /data/data/com.example.arwalking/files/landmark_images/
     */
    val landmarkImagesDir: File by lazy {
        File(appFilesDir, LANDMARK_IMAGES_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.i(TAG, "Erstellt: $absolutePath")
            }
        }
    }
    
    /**
     * Verzeichnis für Vorschaubilder (schnell)
     * /data/data/com.example.arwalking/files/landmark_thumbnails/
     */
    val landmarkThumbnailsDir: File by lazy {
        File(appFilesDir, LANDMARK_THUMBNAILS_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.i(TAG, "Erstellt: $absolutePath")
            }
        }
    }
    
    /**
     * Verzeichnis für JSON-Metadaten
     * /data/data/com.example.arwalking/files/landmark_metadata/
     */
    val landmarkMetadataDir: File by lazy {
        File(appFilesDir, LANDMARK_METADATA_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.i(TAG, "Erstellt: $absolutePath")
            }
        }
    }
    
    /**
     * Verzeichnis für Computer Vision Daten
     * /data/data/com.example.arwalking/files/feature_maps/
     */
    val featureMapsDir: File by lazy {
        File(appFilesDir, FEATURE_MAPS_DIR).apply {
            if (!exists()) {
                mkdirs()
                Log.i(TAG, "Erstellt: $absolutePath")
            }
        }
    }
    
    /**
     * Schnell-Index aller Bilder
     * /data/data/com.example.arwalking/files/landmark_index.json
     */
    val landmarkIndexFile: File by lazy {
        File(appFilesDir, LANDMARK_INDEX_FILE)
    }
    
    /**
     * Initialisiert alle Verzeichnisse
     */
    fun initializeDirectories(): Boolean {
        return try {
            // Erstelle alle Verzeichnisse
            val directories = listOf(
                landmarkImagesDir,
                landmarkThumbnailsDir,
                landmarkMetadataDir,
                featureMapsDir
            )
            
            var allCreated = true
            directories.forEach { dir ->
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    if (created) {
                        Log.i(TAG, "Verzeichnis erstellt: ${dir.absolutePath}")
                    } else {
                        Log.e(TAG, "Fehler beim Erstellen: ${dir.absolutePath}")
                        allCreated = false
                    }
                }
            }
            
            // Erstelle Index-Datei falls nicht vorhanden
            if (!landmarkIndexFile.exists()) {
                landmarkIndexFile.writeText("{\"landmarks\":[], \"lastUpdated\":\"${System.currentTimeMillis()}\"}")
                Log.i(TAG, "Index-Datei erstellt: ${landmarkIndexFile.absolutePath}")
            }
            
            allCreated
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Verzeichnis-Initialisierung: ${e.message}")
            false
        }
    }
    
    /**
     * Gibt Dateipfad für Vollbild zurück
     */
    fun getImageFile(landmarkId: String): File {
        return File(landmarkImagesDir, "${landmarkId}.jpg")
    }
    
    /**
     * Gibt Dateipfad für Thumbnail zurück
     */
    fun getThumbnailFile(landmarkId: String): File {
        return File(landmarkThumbnailsDir, "${landmarkId}_thumb.jpg")
    }
    
    /**
     * Gibt Dateipfad für Metadaten zurück
     */
    fun getMetadataFile(landmarkId: String): File {
        return File(landmarkMetadataDir, "${landmarkId}.json")
    }
    
    /**
     * Gibt Dateipfad für Feature Map zurück
     */
    fun getFeatureMapFile(buildingId: String, floor: Int): File {
        return File(featureMapsDir, "${buildingId}_floor_${floor}.json")
    }
    
    /**
     * Prüft Verzeichnis-Integrität
     */
    fun checkDirectoryIntegrity(): DirectoryStatus {
        val status = DirectoryStatus()
        
        try {
            // Prüfe alle Verzeichnisse
            status.landmarkImagesExists = landmarkImagesDir.exists() && landmarkImagesDir.isDirectory
            status.landmarkThumbnailsExists = landmarkThumbnailsDir.exists() && landmarkThumbnailsDir.isDirectory
            status.landmarkMetadataExists = landmarkMetadataDir.exists() && landmarkMetadataDir.isDirectory
            status.featureMapsExists = featureMapsDir.exists() && featureMapsDir.isDirectory
            status.indexFileExists = landmarkIndexFile.exists() && landmarkIndexFile.isFile
            
            // Zähle Dateien
            status.imageCount = landmarkImagesDir.listFiles()?.size ?: 0
            status.thumbnailCount = landmarkThumbnailsDir.listFiles()?.size ?: 0
            status.metadataCount = landmarkMetadataDir.listFiles()?.size ?: 0
            status.featureMapCount = featureMapsDir.listFiles()?.size ?: 0
            
            // Berechne Speicherverbrauch
            status.totalSizeBytes = calculateDirectorySize(appFilesDir)
            status.imagesSizeBytes = calculateDirectorySize(landmarkImagesDir)
            status.thumbnailsSizeBytes = calculateDirectorySize(landmarkThumbnailsDir)
            
            status.isHealthy = status.landmarkImagesExists && 
                             status.landmarkThumbnailsExists && 
                             status.landmarkMetadataExists && 
                             status.featureMapsExists && 
                             status.indexFileExists
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Integritätsprüfung: ${e.message}")
            status.isHealthy = false
        }
        
        return status
    }
    
    /**
     * Berechnet Verzeichnisgröße rekursiv
     */
    private fun calculateDirectorySize(directory: File): Long {
        return try {
            if (!directory.exists()) return 0L
            
            var size = 0L
            directory.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
            size
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Berechnen der Verzeichnisgröße: ${e.message}")
            0L
        }
    }
    
    /**
     * Bereinigt leere Verzeichnisse und defekte Dateien
     */
    fun cleanupDirectories(): DirectoryCleanupResult {
        try {
            // Entferne leere Verzeichnisse
            val directories = listOf(landmarkImagesDir, landmarkThumbnailsDir, landmarkMetadataDir, featureMapsDir)
            directories.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles()
                    if (files != null && files.isEmpty()) {
                        // Verzeichnis ist leer, aber wir behalten es für zukünftige Nutzung
                        Log.d(TAG, "Leeres Verzeichnis behalten: ${dir.name}")
                    }
                }
            }
            
            // Prüfe auf verwaiste Dateien (Thumbnails ohne Hauptbild, Metadaten ohne Bild, etc.)
            val orphanedThumbnails = findOrphanedThumbnails()
            val orphanedMetadata = findOrphanedMetadata()
            val corruptedFiles = findCorruptedFiles()
            
            Log.i(TAG, "Cleanup abgeschlossen: $orphanedThumbnails verwaiste Thumbnails, " +
                      "$orphanedMetadata verwaiste Metadaten, $corruptedFiles defekte Dateien")
            
            return DirectoryCleanupResult(
                orphanedThumbnails = orphanedThumbnails,
                orphanedMetadata = orphanedMetadata,
                corruptedFiles = corruptedFiles
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Cleanup: ${e.message}")
            return DirectoryCleanupResult(
                orphanedThumbnails = 0,
                orphanedMetadata = 0,
                corruptedFiles = 0
            )
        }
    }
    
    private fun findOrphanedThumbnails(): Int {
        var count = 0
        try {
            landmarkThumbnailsDir.listFiles()?.forEach { thumbnailFile ->
                val landmarkId = thumbnailFile.nameWithoutExtension.replace("_thumb", "")
                val imageFile = getImageFile(landmarkId)
                if (!imageFile.exists()) {
                    thumbnailFile.delete()
                    count++
                    Log.d(TAG, "Verwaistes Thumbnail gelöscht: ${thumbnailFile.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Suchen verwaister Thumbnails: ${e.message}")
        }
        return count
    }
    
    private fun findOrphanedMetadata(): Int {
        var count = 0
        try {
            landmarkMetadataDir.listFiles()?.forEach { metadataFile ->
                val landmarkId = metadataFile.nameWithoutExtension
                val imageFile = getImageFile(landmarkId)
                if (!imageFile.exists()) {
                    metadataFile.delete()
                    count++
                    Log.d(TAG, "Verwaiste Metadaten gelöscht: ${metadataFile.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Suchen verwaister Metadaten: ${e.message}")
        }
        return count
    }
    
    private fun findCorruptedFiles(): Int {
        var count = 0
        try {
            landmarkImagesDir.listFiles()?.forEach { imageFile ->
                if (imageFile.length() == 0L) {
                    imageFile.delete()
                    count++
                    Log.d(TAG, "Defekte Datei gelöscht: ${imageFile.name}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Suchen defekter Dateien: ${e.message}")
        }
        return count
    }
}

/**
 * Status der Verzeichnisstruktur
 */
data class DirectoryStatus(
    var landmarkImagesExists: Boolean = false,
    var landmarkThumbnailsExists: Boolean = false,
    var landmarkMetadataExists: Boolean = false,
    var featureMapsExists: Boolean = false,
    var indexFileExists: Boolean = false,
    var imageCount: Int = 0,
    var thumbnailCount: Int = 0,
    var metadataCount: Int = 0,
    var featureMapCount: Int = 0,
    var totalSizeBytes: Long = 0L,
    var imagesSizeBytes: Long = 0L,
    var thumbnailsSizeBytes: Long = 0L,
    var isHealthy: Boolean = false
) {
    fun getTotalSizeMB(): Double = totalSizeBytes / (1024.0 * 1024.0)
    fun getImagesSizeMB(): Double = imagesSizeBytes / (1024.0 * 1024.0)
    fun getThumbnailsSizeMB(): Double = thumbnailsSizeBytes / (1024.0 * 1024.0)
}

/**
 * Ergebnis der Verzeichnis-Bereinigung
 */
data class DirectoryCleanupResult(
    var orphanedThumbnails: Int = 0,
    var orphanedMetadata: Int = 0,
    var corruptedFiles: Int = 0
) {
    fun getTotalCleaned(): Int = orphanedThumbnails + orphanedMetadata + corruptedFiles
}