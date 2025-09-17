package com.example.arwalking.storage

/**
 * Metadaten zu einem Landmark-Bild
 */
data class LandmarkInfo(
    val id: String,            // z.B. "berlin_tv_tower"
    val filename: String,      // z.B. "berlin_tv_tower.jpg"
    val fileSize: Long,        // Dateigröße (0 bei Assets)
    val lastModified: Long,    // Änderungszeit (0 bei Assets)
    val extension: String      // Dateiendung, z.B. "jpg"
)