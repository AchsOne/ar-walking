// NavigationModels.kt
package com.example.arwalking

data class NavigationRoute(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val startPoint: String = "",
    val endPoint: String = "",
    val totalLength: Double,
    val steps: List<NavigationStep>,
    val totalDistance: Double = totalLength,
    val estimatedTime: Int = 0
)

// Ein einzelner Schritt der Navigation
data class NavigationStep(
    val stepNumber: Int,
    val instruction: String,
    val building: String,
    val floor: Int = 0,
    val landmarks: List<RouteLandmarkData> = emptyList(),
    val landmarkIds: List<String> = emptyList(), // Nur die IDs der Landmarks
    val distance: Double = 0.0,
    val expectedWalkDistance: Double = 0.0, // NEU: aus lengthInMeters der JSON edges
    val estimatedTime: Int = 0
) {
    // Computed property to get landmark IDs from landmarks
    val computedLandmarkIds: List<String>
        get() = if (landmarkIds.isNotEmpty()) landmarkIds else landmarks.map { it.id }
}
