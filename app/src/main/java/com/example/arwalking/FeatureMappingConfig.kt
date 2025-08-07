package com.example.arwalking

/**
 * Konfiguration für Feature-Mapping
 */
object FeatureMappingConfig {
    

    
    // OpenCV Feature-Detection Konfiguration
    object FeatureDetection {
        // Primärer Algorithmus: SIFT für robustere Features
        const val USE_SIFT_PRIMARY = true // SIFT als Hauptalgorithmus verwenden
        
        // SIFT Konfiguration (robuster für verschiedene Lichtverhältnisse)
        const val SIFT_N_FEATURES = 2000 // Erhöht für mehr Features
        const val SIFT_N_OCTAVE_LAYERS = 4 // Mehr Octave-Layer für Multi-Scale
        const val SIFT_CONTRAST_THRESHOLD = 0.03 // Niedriger für mehr Features
        const val SIFT_EDGE_THRESHOLD = 15.0 // Angepasst für bessere Edge-Detection
        const val SIFT_SIGMA = 1.6
        
        // ORB Konfiguration (Fallback/Performance-Option)
        const val ORB_MAX_FEATURES = 2000 // Erhöht für mehr Features
        const val ORB_SCALE_FACTOR = 1.15f // Kleinerer Faktor für feinere Skalierung
        const val ORB_N_LEVELS = 12 // Mehr Level für Multi-Scale Features
        const val ORB_EDGE_THRESHOLD = 25 // Reduziert für mehr Edge-Features
        const val ORB_FIRST_LEVEL = 0
        const val ORB_WTA_K = 2
        const val ORB_PATCH_SIZE = 31
        const val ORB_FAST_THRESHOLD = 15 // Reduziert für mehr Features
        
        // Matching Konfiguration - Gelockerte Kriterien
        const val MATCH_DISTANCE_THRESHOLD = 75.0f // Erhöht für mehr Matches
        const val MIN_MATCH_CONFIDENCE = 0.4f // Reduziert für mehr Matches
        const val MIN_REQUIRED_MATCHES = 15 // Minimum Matches für valide Erkennung
        const val MAX_MATCH_RESULTS = 5 // Mehr Ergebnisse berücksichtigen
        
        // Lowe's Ratio Test für robusteres Matching
        const val LOWE_RATIO_THRESHOLD = 0.8f // Standard: 0.7, gelockert für mehr Matches
        
        // Multi-Scale Feature Detection
        const val ENABLE_MULTI_SCALE = true
        val SCALE_LEVELS = arrayOf(1.0f, 0.8f, 1.2f, 0.6f, 1.5f) // Verschiedene Skalierungen
        
        // Beleuchtungsrobustheit
        const val ENABLE_HISTOGRAM_EQUALIZATION = true // CLAHE für bessere Beleuchtung
        const val CLAHE_CLIP_LIMIT = 3.0
        const val CLAHE_TILE_GRID_SIZE = 8
    }
    
    // Tracking-Stabilität Konfiguration
    object Tracking {
        // Temporales Tracking mit Kalman-Filter
        const val ENABLE_KALMAN_FILTER = true
        const val KALMAN_PROCESS_NOISE = 0.01f // Prozessrauschen
        const val KALMAN_MEASUREMENT_NOISE = 0.1f // Messrauschen
        const val KALMAN_ERROR_COVARIANCE = 1.0f // Fehlerkovarianz
        
        // Tracking-Stabilität
        const val MIN_TRACKING_FRAMES = 3 // Minimum Frames für stabiles Tracking
        const val MAX_TRACKING_LOSS_FRAMES = 5 // Max Frames ohne Match bevor Reset
        const val TRACKING_CONFIDENCE_DECAY = 0.95f // Confidence-Abfall pro Frame
        
        // Smoothing für stabilere Ergebnisse
        const val ENABLE_POSITION_SMOOTHING = true
        const val SMOOTHING_FACTOR = 0.7f // 0.0 = kein Smoothing, 1.0 = maximales Smoothing
        const val MAX_POSITION_JUMP = 50.0f // Maximaler Positionssprung in Pixeln
    }
    
    // Cache-Konfiguration
    object Cache {
        const val FEATURE_MAP_CACHE_SIZE = 15 // Erhöht für mehr gecachte Feature-Maps
        const val IMAGE_CACHE_SIZE_MB = 75 // Erhöht für bessere Performance
        const val CACHE_EXPIRY_HOURS = 24 // Cache-Gültigkeit in Stunden
        
        // Lokale Speicher-Pfade
        const val FEATURE_MAPS_DIR = "feature_maps"
        const val LANDMARK_IMAGES_DIR = "landmark_images"
        const val PROCESSED_FEATURES_DIR = "processed_features"
        const val DEBUG_IMAGES_DIR = "debug_images" // Für Debug-Bilder
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
        const val ENABLE_DETAILED_MATCHING_LOGS = true // Aktiviert für Matching-Analyse
        const val SAVE_DEBUG_IMAGES = true // Speichert Debug-Bilder für Analyse
        
        // Erweiterte Debug-Optionen
        const val LOG_FEATURE_COUNT = true // Loggt Anzahl erkannter Features
        const val LOG_MATCH_STATISTICS = true // Loggt Matching-Statistiken
        const val LOG_TRACKING_STATE = true // Loggt Tracking-Zustand
        const val SAVE_FEATURE_OVERLAY_IMAGES = true // Speichert Bilder mit Feature-Overlay
        const val SAVE_MATCH_VISUALIZATION = true // Speichert Match-Visualisierung
        
        // Performance-Monitoring
        const val MEASURE_DETECTION_TIME = true
        const val MEASURE_MATCHING_TIME = true
        const val MEASURE_TOTAL_PIPELINE_TIME = true
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
        
        // Feature Detection Validierung
        if (FeatureDetection.ORB_MAX_FEATURES <= 0) {
            issues.add("ORB_MAX_FEATURES muss größer als 0 sein")
        }
        if (FeatureDetection.SIFT_N_FEATURES <= 0) {
            issues.add("SIFT_N_FEATURES muss größer als 0 sein")
        }
        if (FeatureDetection.MIN_REQUIRED_MATCHES <= 0) {
            issues.add("MIN_REQUIRED_MATCHES muss größer als 0 sein")
        }
        if (FeatureDetection.LOWE_RATIO_THRESHOLD <= 0 || FeatureDetection.LOWE_RATIO_THRESHOLD >= 1) {
            issues.add("LOWE_RATIO_THRESHOLD muss zwischen 0 und 1 liegen")
        }
        
        // Tracking Validierung
        if (Tracking.SMOOTHING_FACTOR < 0 || Tracking.SMOOTHING_FACTOR > 1) {
            issues.add("SMOOTHING_FACTOR muss zwischen 0 und 1 liegen")
        }
        if (Tracking.MIN_TRACKING_FRAMES <= 0) {
            issues.add("MIN_TRACKING_FRAMES muss größer als 0 sein")
        }
        
        // Network Validierung
        if (Network.MAX_IMAGE_SIZE_MB <= 0) {
            issues.add("MAX_IMAGE_SIZE_MB muss größer als 0 sein")
        }
        
        // Cache Validierung
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