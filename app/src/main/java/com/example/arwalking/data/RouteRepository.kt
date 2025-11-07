package com.example.arwalking.data

import android.content.Context
import com.example.arwalking.RouteData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    // Loads a route from the assets
    suspend fun getRouteFromAssets(filename: String): RouteData? {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = context.assets.open(filename).bufferedReader().use { it.readText() }
                gson.fromJson(jsonString, RouteData::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    // Placeholder for a future API call
    suspend fun getRouteFromApi(startPoint: String, endPoint: String): RouteData? {
        return withContext(Dispatchers.IO) {
            try {
                // The API call will be implemented here later
                // val response = apiService.getRoute(startPoint, endPoint)
                // gson.fromJson(response, RouteData::class.java)
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}