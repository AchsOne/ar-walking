package com.example.arwalking

/**
 * Datenklassen für Navigation und Routing
 */
data class NavigationRoute(
    val id: String,
    val name: String,
    val description: String,
    val totalLength: Double,
    val steps: List<NavigationStep>,
    val estimatedTime: Int = 0
)

data class NavigationStep(
    val stepNumber: Int,
    val instruction: String,
    val building: String,
    val floor: Int,
    val landmarks: List<String>,
    val distance: Double,
    val estimatedTime: Int
)

// FeatureNavigationRoute und FeatureNavigationStep sind in FeatureNavigationRoute.kt definiert
// FeatureLandmark ist in FeatureLandmark.kt definiert

data class Position(
    val x: Double,
    val y: Double,
    val z: Double
)

data class TrackedLandmark(
    val landmark: FeatureLandmark,
    val position: android.graphics.PointF,
    val confidence: Float,
    val frameCount: Int,
    val isStable: Boolean,
    val trackingQuality: Float
)