package com.example.arwalking

/**
 * Verarbeitete Landmark für Feature-Matching
 */
data class ProcessedLandmark(
    val id: String,
    val name: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val type: String = "landmark"
)