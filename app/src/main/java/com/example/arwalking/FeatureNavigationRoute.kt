package com.example.arwalking

/**
 * Datenklassen für Feature-Navigation
 */
data class FeatureNavigationRoute(
    val id: String,
    val name: String,
    val steps: List<FeatureNavigationStep>
)

data class FeatureNavigationStep(
    val stepNumber: Int,
    val instruction: String,
    val landmarks: List<FeatureLandmark>,
    val expectedFeatures: List<String> = emptyList()
)

// FeatureMatchResult ist in FeatureMatchingEngine.kt definiert