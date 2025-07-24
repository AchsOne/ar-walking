// NavigationModels.kt
package com.example.arwalking

data class NavigationRoute(
    val totalLength: Double,
    val steps: List<NavigationStep>
)

// Ein einzelner Schritt der Navigation
data class NavigationStep(
    val stepNumber: Int,
    val instruction: String,
    val building: String,
    val landmarkIds: List<String> // Nur die IDs der Landmarks
)
