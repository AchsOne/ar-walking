package com.example.arwalking.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FavoritesRepository {
    private val _favorites = MutableStateFlow<List<FavoriteRoute>>(emptyList())
    val favorites: StateFlow<List<FavoriteRoute>> = _favorites.asStateFlow()

    fun addFavorite(startLocation: String, destination: String) {
        val newFavorite = FavoriteRoute(
            id = "${startLocation}_${destination}_${System.currentTimeMillis()}",
            startLocation = startLocation,
            destination = destination
        )
        
        // Check if this exact route already exists
        val exists = _favorites.value.any { 
            it.startLocation == startLocation && it.destination == destination 
        }
        
        if (!exists) {
            _favorites.value = _favorites.value + newFavorite
        }
    }

    fun removeFavorite(favoriteRoute: FavoriteRoute) {
        _favorites.value = _favorites.value.filter { it.id != favoriteRoute.id }
    }

    fun isFavorite(startLocation: String, destination: String): Boolean {
        return _favorites.value.any { 
            it.startLocation == startLocation && it.destination == destination 
        }
    }
}