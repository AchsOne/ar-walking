package com.example.arwalking

/**
 * Konfiguration für Feature-Mapping
 */
object FeatureMappingConfig {
    

    
    // OpenCV Feature-Detection Konfiguration
    object FeatureDetection {
        // ORB Konfiguration
        const val ORB_MAX_FEATURES = 1000
        const val ORB_SCALE_FACTOR = 1.2f
        const val ORB_N_LEVELS = 8
        const val ORB_EDGE_THRESHOLD = 31
        const val ORB_FIRST_LEVEL = 0
        const val ORB_WTA_K = 2
        const val ORB_PATCH_SIZE = 31
        const val ORB_FAST_THRESHOLD = 20
        
        // Matching Konfiguration
        const val MATCH_DISTANCE_THRESHOLD = 50.0f
        const val MIN_MATCH_CONFIDENCE = 0.6f
        const val MAX_MATCH_RESULTS = 3
        
        // SIFT Konfiguration (alternative, bessere Qualität aber langsamer)
        const val SIFT_N_FEATURES = 500
        const val SIFT_N_OCTAVE_LAYERS = 3
        const val SIFT_CONTRAST_THRESHOLD = 0.04
        const val SIFT_EDGE_THRESHOLD = 10.0
        const val SIFT_SIGMA = 1.6
    }
    
    // Cache-Konfiguration
    object Cache {
        const val FEATURE_MAP_CACHE_SIZE = 10 // Max Anzahl gecachte Feature-Maps
        const val IMAGE_CACHE_SIZE_MB = 50 // Max Größe für Bild-Cache in MB
        const val CACHE_EXPIRY_HOURS = 24 // Cache-Gültigkeit in Stunden
        
        // Lokale Speicher-Pfade
        const val FEATURE_MAPS_DIR = "feature_maps"
        const val LANDMARK_IMAGES_DIR = "landmark_images"
        const val PROCESSED_FEATURES_DIR = "processed_features"
    }
    
    // Netzwerk-Konfiguration
    object Network {
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 60L
        const val WRITE_TIMEOUT_SECONDS = 60L
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1000L
        
        // Upload-Limits
        const val MAX_IMAGE_SIZE_MB = 10
        const val MAX_BATCH_SIZE = 5
        const val COMPRESSION_QUALITY = 85 // JPEG Qualität (0-100)
    }
    
    // UI-Konfiguration
    object UI {
        const val MATCH_INFO_UPDATE_INTERVAL_MS = 100L
        const val CONFIDENCE_DISPLAY_THRESHOLD = 0.3f
        const val MAX_DISPLAYED_ALTERNATIVES = 2
        
        // Farben für verschiedene Confidence-Level
        const val HIGH_CONFIDENCE_COLOR = 0xFF4CAF50.toInt() // Grün
        const val MEDIUM_CONFIDENCE_COLOR = 0xFFFF9800.toInt() // Orange
        const val LOW_CONFIDENCE_COLOR = 0xFFF44336.toInt() // Rot
        
        fun getConfidenceColor(confidence: Float): Int {
            return when {
                confidence >= 0.8f -> HIGH_CONFIDENCE_COLOR
                confidence >= 0.5f -> MEDIUM_CONFIDENCE_COLOR
                else -> LOW_CONFIDENCE_COLOR
            }
        }
    }
    
    // Debug-Konfiguration
    object Debug {
        val ENABLE_FEATURE_VISUALIZATION = BuildConfig.DEBUG_FEATURE_MAPPING
        val ENABLE_PERFORMANCE_LOGGING = BuildConfig.DEBUG_FEATURE_MAPPING
        const val ENABLE_DETAILED_MATCHING_LOGS = false // Nur bei Bedarf aktivieren
        const val SAVE_DEBUG_IMAGES = false // Speichert Debug-Bilder lokal
        

    }
    
    // Experimentelle Features
    object Experimental {
        const val ENABLE_DEEP_LEARNING_FEATURES = false // Für zukünftige ML-Integration
        const val ENABLE_SLAM_INTEGRATION = false // Simultaneous Localization and Mapping
        const val ENABLE_CLOUD_PROCESSING = false // Deaktiviert - kein Server
        const val ENABLE_COLLABORATIVE_MAPPING = false // Nutzer können Landmarks hinzufügen
    }
    
    // Validierung der Konfiguration
    fun validateConfig(): List<String> {
        val issues = mutableListOf<String>()
        
        if (FeatureDetection.ORB_MAX_FEATURES <= 0) {
            issues.add("ORB_MAX_FEATURES muss größer als 0 sein")
        }
        
        if (Network.MAX_IMAGE_SIZE_MB <= 0) {
            issues.add("MAX_IMAGE_SIZE_MB muss größer als 0 sein")
        }
        
        if (Cache.FEATURE_MAP_CACHE_SIZE <= 0) {
            issues.add("FEATURE_MAP_CACHE_SIZE muss größer als 0 sein")
        }
        
        return issues
    }
    
    // Hilfsfunktionen
    fun isDebugMode(): Boolean = BuildConfig.DEBUG_FEATURE_MAPPING
    
    fun getImageCompressionQuality(): Int = Network.COMPRESSION_QUALITY
    
    fun getMaxImageSizeBytes(): Long = Network.MAX_IMAGE_SIZE_MB * 1024 * 1024L
}