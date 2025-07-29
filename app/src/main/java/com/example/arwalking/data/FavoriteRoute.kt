package com.example.arwalking.data

data class FavoriteRoute(
    val id: String,
    val startLocation: String,
    val destination: String,
    val timestamp: Long = System.currentTimeMillis()
)