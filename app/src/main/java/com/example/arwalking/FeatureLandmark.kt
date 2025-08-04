package com.example.arwalking

/**
 * Datenklasse für Feature-Landmarks
 */
data class FeatureLandmark(
    val id: String,
    val name: String,
    val description: String = "",
    val building: String = "",
    val floor: Int = 0,
    val position: LandmarkPosition? = null,
    val imageUrl: String? = null,
    val features: List<String> = emptyList()
)

// Position ist in NavigationRoute.kt definiert
typealias LandmarkPosition = Position