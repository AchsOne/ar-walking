package com.example.arwalking.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.IOException

/**
 * Repository für Route-Daten aus JSON-Assets
 */
class RouteRepository(private val context: Context) {
    
    private val TAG = "RouteRepository"
    private val gson = Gson()
    
    suspend fun getRouteFromAssets(filename: String): RouteData? {
        return try {
            Log.i(TAG, "Loading route from assets: $filename")
            
            val jsonString = context.assets.open(filename).bufferedReader().use { it.readText() }
            val routeData = gson.fromJson(jsonString, RouteData::class.java)
            
            Log.i(TAG, "Route loaded successfully from $filename")
            routeData
            
        } catch (e: IOException) {
            Log.e(TAG, "Error reading route file $filename: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing route data: ${e.message}")
            null
        }
    }
    
    suspend fun loadRoute(building: String, floor: String, additionalParam: String): RouteData? {
        Log.d(TAG, "loadRoute called (stub): $building, $floor")
        return getRouteFromAssets("route.json")
    }
}

// Datenklassen für Route-JSON
data class RouteData(
    val route: Route
)

data class Route(
    val path: List<PathItem>
)

data class PathItem(
    val xmlName: String,
    val levelInfo: LevelInfo?,
    val routeParts: List<RoutePart>
)

data class LevelInfo(
    val storey: String?
)

data class RoutePart(
    val instruction: String?,
    val instructionDe: String?,
    val distance: Double?,
    val duration: Int?,
    val landmarks: List<String>?
)