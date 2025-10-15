package com.example.arwalking


/**
 * Datenmodelle für Feature-based Mapping
 */

// Landmark mit Feature-Daten
data class FeatureLandmark(
    val id: String,
    val name: String,
    val description: String,
    val position: Position,
    val imageUrl: String,
    val localImagePath: String? = null,
    val featureDescriptors: String? = null, // Base64 encoded OpenCV descriptors
    val keypoints: List<KeypointData>? = null,
    val confidence: Float = 0.0f,
    val lastUpdated: Long = System.currentTimeMillis()
)

// Position im 3D-Raum
data class Position(
    val x: Double,
    val y: Double,
    val z: Double,
    val building: String? = null,
    val floor: Int? = null
)

// Keypoint-Daten für Feature-Matching
data class KeypointData(
    val x: Float,
    val y: Float,
    val angle: Float,
    val response: Float,
    val octave: Int,
    val classId: Int
)

// Feature-Map für einen Bereich/Gebäude
data class FeatureMap(
    val id: String,
    val name: String,
    val building: String,
    val floor: Int,
    val landmarks: List<FeatureLandmark>,
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis()
)



// Feature-Match Ergebnis
data class FeatureMatchResult(
    val landmark: FeatureLandmark,
    val matchCount: Int,
    val confidence: Float,
    val distance: Float? = null,
    val angle: Double? = null,
    val screenPosition: android.graphics.PointF? = null
)

// Navigation mit Feature-Matching
data class FeatureNavigationStep(
    val stepNumber: Int,
    val instruction: String,
    val building: String,
    val targetLandmark: FeatureLandmark?,
    val alternativeLandmarks: List<FeatureLandmark> = emptyList(),
    val requiredConfidence: Float = 0.7f
)

// Erweiterte NavigationRoute mit Feature-Mapping
data class FeatureNavigationRoute(
    val totalLength: Double,
    val steps: List<FeatureNavigationStep>,
    val featureMaps: List<FeatureMap> = emptyList()
)



data class ImageMetadata(
    val width: Int,
    val height: Int,
    val timestamp: Long,
    val deviceInfo: String,
    val position: Position? = null,
    val viewingAngle: Float? = null
)

// Erweiterte Feature-Daten für lokale Verarbeitung
data class LandmarkFeatures(
    val keypoints: List<FeatureKeypoint>,
    val descriptors: String // Base64-kodierte OpenCV Descriptors
)

data class FeatureKeypoint(
    val x: Float,
    val y: Float,
    val size: Float,
    val angle: Float,
    val response: Float,
    val octave: Int = 0,
    val classId: Int = -1
)

// Verarbeitetes Landmark für schnelles Matching
data class ProcessedLandmark(
    val landmark: FeatureLandmark,
    val descriptors: org.opencv.core.Mat,
    val keypoints: org.opencv.core.MatOfKeyPoint,
    val image: org.opencv.core.Mat
)

// Feature-Matching Konfiguration
data class MatchingConfig(
    val maxFeatures: Int = 1000,
    val matchDistanceThreshold: Float = 50.0f,
    val minMatchConfidence: Float = 0.6f,
    val useRatioTest: Boolean = true,
    val ratioThreshold: Float = 0.75f
)

// Lokale Feature-Map Cache
data class FeatureMapCache(
    val featureMap: FeatureMap,
    val processedLandmarks: Map<String, ProcessedLandmark>,
    val lastAccessed: Long = System.currentTimeMillis()
)

