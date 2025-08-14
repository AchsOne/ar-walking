package com.example.arwalking.data

/**
 * Configuration class for AR Navigation System
 * Contains all tunable parameters for vision, AR, and navigation components
 */
data class ARNavigationConfig(
    // Vision System Configuration
    val orbMaxFeatures: Int = 500,
    val matchingThreshold: Float = 0.7f,
    val temporalSmoothingWindow: Int = 5,
    val minMatchesRequired: Int = 10,
    
    // Performance Configuration
    val maxCacheSize: Int = 100,
    val processingIntervalMs: Long = 100L,
    val maxCandidates: Int = 5,
    
    // AR Configuration
    val minTrackingQuality: TrackingQuality = TrackingQuality.LIMITED,
    val arrowPlacementDistance: Float = 1.0f,
    val arrowScale: Float = 0.1f,
    
    // Navigation Configuration
    val autoAdvanceThreshold: Float = 0.8f,
    val stepTimeoutMs: Long = 30000L,
    val confidenceDecayRate: Float = 0.95f,
    
    // File Paths
    val landmarkImagesPath: String = "images",
    val routesPath: String = "routes",
    val cacheDirectory: String = "ar_navigation_cache",
    val picturesDir: String = "images",
    val cacheDir: String = "ar_navigation_cache",
    
    // Frame processing
    val frameResizeWidth: Int = 640,
    
    // Candidate selection
    val topK: Int = 5,
    
    // Thresholds
    val thresholds: ThresholdConfig = ThresholdConfig(),
    
    // Matcher configuration
    val matcher: MatcherConfig? = MatcherConfig(),
    
    // RANSAC configuration
    val ransac: RansacConfig? = RansacConfig(),
    
    // Stabilization configuration
    val stabilization: StabilizationConfig = StabilizationConfig(),
    
    // Arrow configuration
    val arrow: ArrowConfig = ArrowConfig()
)

/**
 * Tracking quality levels for AR session
 */
enum class TrackingQuality {
    NONE,
    LIMITED,
    NORMAL,
    GOOD
}

/**
 * Arrow direction enumeration
 */
enum class ArrowDirection {
    FORWARD,
    LEFT,
    RIGHT,
    BACK,
    UP,
    DOWN,
    NONE
}

/**
 * Navigation statistics for monitoring and debugging
 */
data class NavigationStats(
    val totalSteps: Int = 0,
    val completedSteps: Int = 0,
    val currentStepIndex: Int = 0,
    val progress: Float = 0f,
    val totalDistance: Float = 0f,
    val remainingDistance: Float = 0f,
    val estimatedTimeRemaining: Long = 0L
)

/**
 * Performance metrics for system monitoring
 */
data class PerformanceMetrics(
    val avgMatchingTimeMs: Float = 0f,
    val avgFrameProcessingTimeMs: Float = 0f,
    val hitRate: Float = 0f,
    val cacheHitRate: Float = 0f,
    val memoryUsageMB: Float = 0f
)

/**
 * Match result from landmark recognition
 */
data class MatchResult(
    val landmarkId: String,
    val confidence: Float,
    val matchCount: Int,
    val processingTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Navigation step with landmark information
 */
data class NavigationStep(
    val id: String,
    val instructionDe: String,
    val instructionEn: String,
    val landmarks: List<Landmark>,
    val arrowDirection: ArrowDirection,
    val estimatedDuration: Long = 0L
)

/**
 * Landmark information
 */
data class Landmark(
    val id: String,
    val nameDe: String,
    val nameEn: String,
    val type: String,
    val x: Float,
    val y: Float,
    val confidence: Float = 0f
)

/**
 * Engine state for monitoring initialization and operation
 */
data class EngineState(
    val isInitialized: Boolean = false,
    val isInitializing: Boolean = false,
    val initializationMessage: String = "",
    val isLoadingRoute: Boolean = false,
    val loadingMessage: String = "",
    val isRouteLoaded: Boolean = false,
    val error: String? = null
)

/**
 * Navigation status for real-time monitoring
 */
data class NavigationStatus(
    val currentLandmarkId: String? = null,
    val matchConfidence: Float = 0f,
    val isTracking: Boolean = false,
    val trackingQuality: TrackingQuality = TrackingQuality.NONE,
    val lastUpdateTime: Long = 0L
)

/**
 * Threshold configuration for matching and promotion
 */
data class ThresholdConfig(
    val match: Float = 0.7f,
    val promote: Float = 0.8f
)

/**
 * Matcher configuration
 */
data class MatcherConfig(
    val ratio: Float = 0.75f
)

/**
 * RANSAC configuration for homography estimation
 */
data class RansacConfig(
    val reprojThreshold: Float = 3.0f,
    val maxIters: Int = 2000,
    val confidence: Float = 0.995f
)

/**
 * Stabilization configuration
 */
data class StabilizationConfig(
    val minStableFrames: Int = 3
)

/**
 * Arrow configuration
 */
data class ArrowConfig(
    val instantPlacement: Boolean = false,
    val offset: Float = 0.5f
)