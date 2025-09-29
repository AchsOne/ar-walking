package com.example.arwalking.data

import android.content.Context
import com.example.arwalking.RouteData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RouteRepository(private val context: Context) {

    private val gson = Gson()

    // Lädt eine Route aus den Assets
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

    // Platz für einen künftigen API-Call
    suspend fun getRouteFromApi(startPoint: String, endPoint: String): RouteData? {
        return withContext(Dispatchers.IO) {
            try {
                // Hier wird später der API-Call implementiert
                // val response = apiService.getRoute(startPoint, endPoint)
                // gson.fromJson(response, RouteData::class.java)
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}