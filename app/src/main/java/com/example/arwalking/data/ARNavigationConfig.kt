package com.example.arwalking.data

/**
 * Configuration for the AR navigation system.
 * Provides tunable parameters for vision, matching and AR rendering.
 */
data class ARNavigationConfig(
    val picturesDir: String = "images",
    val cacheDir: String = "feature_cache",
    val feature: FeatureConfig = FeatureConfig(),
    val matcher: MatcherConfig = MatcherConfig(),
    val ransac: RansacConfig = RansacConfig(),
    val topK: Int = 10,
    val thresholds: ThresholdConfig = ThresholdConfig(),
    val frameResizeWidth: Int = 960,
    val stabilization: StabilizationConfig = StabilizationConfig(),
    val arrow: ArrowConfig = ArrowConfig()
)

/** Configuration for feature extraction */
data class FeatureConfig(
    val type: String = "ORB",
    val nFeatures: Int = 2000,
    val scaleFactor: Float = 1.2f,
    val nLevels: Int = 8
)

/** Matcher configuration (Lowe ratio etc.) */
data class MatcherConfig(
    val ratio: Float = 0.75f
)

/** RANSAC parameters for homography estimation */
data class RansacConfig(
    val reprojThreshold: Float = 3.0f,
    val maxIters: Int = 2000,
    val confidence: Float = 0.995f
)

/** Matching thresholds for recognition and promotion */
data class ThresholdConfig(
    val match: Float = 0.35f,
    val promote: Float = 0.50f,
    val demote: Float = 0.25f
)

/** Temporal stabilization configuration */
data class StabilizationConfig(
    val emaAlpha: Float = 0.2f,
    val minStableFrames: Int = 3
)

/** Rendering configuration for the navigation arrow */
data class ArrowConfig(
    val scale: Float = 0.1f,
    val offset: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val occlusion: Boolean = true,
    val instantPlacement: Boolean = true
)

/** Result of landmark recognition */
data class MatchResult(
    val landmarkId: String,
    val confidence: Float,
    val inliers: Int,
    val keypoints: Int,
    val homography: FloatArray? = null
)

/** AR tracking quality information */
enum class TrackingQuality {
    NONE,
    INSUFFICIENT,
    SUFFICIENT,
    NORMAL
}

/** High level navigation status */
data class NavigationStatus(
    val currentLandmarkId: String? = null,
    val currentStepId: String? = null,
    val matchConfidence: Float = 0f,
    val isTracking: Boolean = false,
    val trackingQuality: TrackingQuality = TrackingQuality.NONE
)

/** Performance metrics for debugging and monitoring */
data class PerformanceMetrics(
    val avgMatchingTimeMs: Float = 0f,
    val hitRate: Float = 0f,
    val falsePositiveRate: Float = 0f,
    val memoryUsageMB: Float = 0f
)
