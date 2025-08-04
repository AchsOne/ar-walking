package com.example.arwalking.storage

/**
 * Konfiguration für die lokale Speicherung
 * Basierend auf STORAGE_ARCHITECTURE.md Spezifikation
 */
object StorageConfig {
    
    // === BILDGRÖSSENKONFIGURATION ===
    
    /**
     * Maximale Bildgröße für Vollbilder (2048px wie in Architektur spezifiziert)
     */
    const val MAX_IMAGE_DIMENSION = 2048
    
    /**
     * Thumbnail-Größe (256x256px wie in Architektur spezifiziert)
     */
    const val THUMBNAIL_SIZE = 256
    
    // === KOMPRIMIERUNGSKONFIGURATION ===
    
    /**
     * JPEG-Qualität für Vollbilder (85% für gute Qualität bei akzeptabler Dateigröße)
     */
    const val FULL_IMAGE_COMPRESSION_QUALITY = 85
    
    /**
     * JPEG-Qualität für Thumbnails (75% für kleinere Dateien)
     */
    const val THUMBNAIL_COMPRESSION_QUALITY = 75
    
    // === CACHE-KONFIGURATION ===
    
    /**
     * Anzahl Vollbilder im LRU-Cache (50 wie in Architektur spezifiziert)
     */
    const val BITMAP_CACHE_SIZE = 50
    
    /**
     * Anzahl Thumbnails im LRU-Cache (100 wie in Architektur spezifiziert)
     */
    const val THUMBNAIL_CACHE_SIZE = 100
    
    // === PAGING-KONFIGURATION ===
    
    /**
     * Anzahl Bilder pro Seite (20 wie in Architektur spezifiziert)
     */
    const val PAGE_SIZE = 20
    
    // === DATEIGRÖSSEN-SCHÄTZUNGEN ===
    
    /**
     * Geschätzte Dateigröße für Vollbilder (300KB durchschnittlich)
     */
    const val ESTIMATED_FULL_IMAGE_SIZE_KB = 300
    
    /**
     * Geschätzte Dateigröße für Thumbnails (25KB durchschnittlich)
     */
    const val ESTIMATED_THUMBNAIL_SIZE_KB = 25
    
    /**
     * Geschätzte Dateigröße für Metadaten (2KB durchschnittlich)
     */
    const val ESTIMATED_METADATA_SIZE_KB = 2
    
    // === SPEICHER-LIMITS ===
    
    /**
     * Maximaler RAM-Verbrauch für Vollbilder (~800MB wie in Architektur spezifiziert)
     */
    const val MAX_BITMAP_CACHE_SIZE_MB = 800
    
    /**
     * Maximaler RAM-Verbrauch für Thumbnails (~25MB wie in Architektur spezifiziert)
     */
    const val MAX_THUMBNAIL_CACHE_SIZE_MB = 25
    
    // === PERFORMANCE-ZIELE ===
    
    /**
     * Ziel-Ladezeit für lokale Bilder (15ms wie in Architektur spezifiziert)
     */
    const val TARGET_LOCAL_LOAD_TIME_MS = 15
    
    /**
     * Ziel-Ladezeit für Thumbnails aus Cache (3ms wie in Architektur spezifiziert)
     */
    const val TARGET_THUMBNAIL_CACHE_TIME_MS = 3
    
    /**
     * Ziel-Zeit für Suchoperationen (<1ms wie in Architektur spezifiziert)
     */
    const val TARGET_SEARCH_TIME_MS = 1
    
    /**
     * Ziel-Zeit für lokale Komprimierung (200ms wie in Architektur spezifiziert)
     */
    const val TARGET_COMPRESSION_TIME_MS = 200
    
    // === DATEI-EXTENSIONS ===
    
    /**
     * Dateiendung für Vollbilder
     */
    const val IMAGE_FILE_EXTENSION = ".jpg"
    
    /**
     * Dateiendung für Thumbnails
     */
    const val THUMBNAIL_FILE_EXTENSION = ".jpg"
    
    /**
     * Suffix für Thumbnail-Dateien
     */
    const val THUMBNAIL_FILE_SUFFIX = "_thumb"
    
    /**
     * Dateiendung für Metadaten
     */
    const val METADATA_FILE_EXTENSION = ".json"
    
    /**
     * Dateiendung für Feature Maps
     */
    const val FEATURE_MAP_FILE_EXTENSION = ".json"
    
    // === CLEANUP-KONFIGURATION ===
    
    /**
     * Maximales Alter für automatische Bereinigung (24 Stunden)
     */
    const val CLEANUP_MAX_AGE_HOURS = 24
    
    /**
     * Intervall für automatische Bereinigung (6 Stunden)
     */
    const val CLEANUP_INTERVAL_HOURS = 6
    
    // === BATCH-VERARBEITUNG ===
    
    /**
     * Anzahl Bilder die parallel verarbeitet werden
     */
    const val PARALLEL_PROCESSING_BATCH_SIZE = 3
    
    /**
     * Timeout für I/O-Operationen (5 Sekunden)
     */
    const val IO_TIMEOUT_MS = 5000L
    
    // === VALIDIERUNG ===
    
    /**
     * Minimale Bildgröße für Validierung (64px)
     */
    const val MIN_IMAGE_DIMENSION = 64
    
    /**
     * Maximale Dateigröße für Vollbilder (5MB)
     */
    const val MAX_FILE_SIZE_MB = 5
    
    /**
     * Unterstützte Bildformate
     */
    val SUPPORTED_IMAGE_FORMATS = listOf("jpg", "jpeg", "png", "webp")
    
    // === HILFSFUNKTIONEN ===
    
    /**
     * Konvertiert KB zu Bytes
     */
    fun kbToBytes(kb: Int): Long = kb * 1024L
    
    /**
     * Konvertiert MB zu Bytes
     */
    fun mbToBytes(mb: Int): Long = mb * 1024L * 1024L
    
    /**
     * Konvertiert Bytes zu MB
     */
    fun bytesToMB(bytes: Long): Double = bytes / (1024.0 * 1024.0)
    
    /**
     * Konvertiert Bytes zu KB
     */
    fun bytesToKB(bytes: Long): Double = bytes / 1024.0
    
    /**
     * Berechnet geschätzte Speichernutzung für gegebene Anzahl Bilder
     */
    fun estimateStorageUsage(imageCount: Int): StorageEstimate {
        val fullImagesKB = imageCount * ESTIMATED_FULL_IMAGE_SIZE_KB
        val thumbnailsKB = imageCount * ESTIMATED_THUMBNAIL_SIZE_KB
        val metadataKB = imageCount * ESTIMATED_METADATA_SIZE_KB
        val totalKB = fullImagesKB + thumbnailsKB + metadataKB
        
        return StorageEstimate(
            imageCount = imageCount,
            fullImagesSizeKB = fullImagesKB,
            thumbnailsSizeKB = thumbnailsKB,
            metadataSizeKB = metadataKB,
            totalSizeKB = totalKB,
            totalSizeMB = bytesToMB(kbToBytes(totalKB))
        )
    }
    
    /**
     * Berechnet optimale Cache-Größe basierend auf verfügbarem RAM
     */
    fun calculateOptimalCacheSize(availableMemoryMB: Int): CacheConfig {
        // Verwende maximal 10% des verfügbaren RAMs für Bild-Cache
        val maxCacheMemoryMB = (availableMemoryMB * 0.1).toInt()
        
        // Berechne optimale Anzahl Bilder im Cache
        val avgImageSizeMB = bytesToMB(kbToBytes(ESTIMATED_FULL_IMAGE_SIZE_KB))
        val optimalBitmapCacheSize = (maxCacheMemoryMB / avgImageSizeMB).toInt()
            .coerceAtMost(BITMAP_CACHE_SIZE)
            .coerceAtLeast(10) // Mindestens 10 Bilder
        
        val avgThumbnailSizeMB = bytesToMB(kbToBytes(ESTIMATED_THUMBNAIL_SIZE_KB))
        val optimalThumbnailCacheSize = (maxCacheMemoryMB / avgThumbnailSizeMB).toInt()
            .coerceAtMost(THUMBNAIL_CACHE_SIZE)
            .coerceAtLeast(20) // Mindestens 20 Thumbnails
        
        return CacheConfig(
            bitmapCacheSize = optimalBitmapCacheSize,
            thumbnailCacheSize = optimalThumbnailCacheSize,
            estimatedMemoryUsageMB = (optimalBitmapCacheSize * avgImageSizeMB + 
                                    optimalThumbnailCacheSize * avgThumbnailSizeMB).toInt()
        )
    }
}

/**
 * Schätzung der Speichernutzung
 */
data class StorageEstimate(
    val imageCount: Int,
    val fullImagesSizeKB: Int,
    val thumbnailsSizeKB: Int,
    val metadataSizeKB: Int,
    val totalSizeKB: Int,
    val totalSizeMB: Double
)

/**
 * Cache-Konfiguration
 */
data class CacheConfig(
    val bitmapCacheSize: Int,
    val thumbnailCacheSize: Int,
    val estimatedMemoryUsageMB: Int
)