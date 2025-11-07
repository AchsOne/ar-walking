package com.example.arwalking.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(name = "favorites_prefs")

object FavoritesRepository {
    private val _favorites = MutableStateFlow<List<FavoriteRoute>>(emptyList())
    val favorites: StateFlow<List<FavoriteRoute>> = _favorites.asStateFlow()

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: Context? = null
    private val KEY_FAVORITES = stringPreferencesKey("favorites_json")

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        scope.launch {
            try {
                val prefs = appContext!!.favoritesDataStore.data.first()
                val json = prefs[KEY_FAVORITES]
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<List<FavoriteRoute>>() {}.type
                    val list: List<FavoriteRoute> = gson.fromJson(json, type)
                    _favorites.value = list
                }
            } catch (_: Exception) {
                // ignore and keep empty
            }
        }
    }

    fun addFavorite(startLocation: String, destination: String) {
        val newFavorite = FavoriteRoute(
            id = "${startLocation}_${destination}_${System.currentTimeMillis()}",
            startLocation = startLocation,
            destination = destination
        )

        // Prevent duplicates
        val exists = _favorites.value.any {
            it.startLocation == startLocation && it.destination == destination
        }

        if (!exists) {
            _favorites.value = _favorites.value + newFavorite
            persist()
        }
    }

    fun removeFavorite(favoriteRoute: FavoriteRoute) {
        _favorites.value = _favorites.value.filter { it.id != favoriteRoute.id }
        persist()
    }

    fun isFavorite(startLocation: String, destination: String): Boolean {
        return _favorites.value.any {
            it.startLocation == startLocation && it.destination == destination
        }
    }

    private fun persist() {
        val ctx = appContext ?: return
        val json = gson.toJson(_favorites.value)
        scope.launch {
            try {
                ctx.favoritesDataStore.edit { prefs ->
                    prefs[KEY_FAVORITES] = json
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}
