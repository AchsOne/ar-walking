package com.example.arwalking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository für Favoriten-Management
 */
object FavoritesRepository {
    
    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()
    
    fun addFavorite(favorite: Favorite) {
        val currentFavorites = _favorites.value.toMutableList()
        if (!currentFavorites.contains(favorite)) {
            currentFavorites.add(favorite)
            _favorites.value = currentFavorites
        }
    }
    
    fun removeFavorite(favorite: Favorite) {
        val currentFavorites = _favorites.value.toMutableList()
        currentFavorites.remove(favorite)
        _favorites.value = currentFavorites
    }
}

data class Favorite(
    val startLocation: String,
    val destination: String,
    val name: String = "$startLocation → $destination"
)