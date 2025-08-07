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
        return getRouteFromAssets("models/final-route.json")
    }
}

// Datenklassen für Route-JSON
data class RouteData(
    val route: Route
)

data class Route(
    val path: List<PathItem>,
    val routeInfo: RouteInfo? = null
)

data class PathItem(
    val xmlName: String,
    val xmlNameEn: String?,
    val xmlNameDe: String?,
    val xmlFile: String?,
    val levelInfo: LevelInfo?,
    val routeParts: List<RoutePart>
)

data class LevelInfo(
    val storeyNameEn: String?,
    val storeyName: String?,
    val storeyNameDe: String?,
    val storey: String?,
    val id: String?,
    val width: String?,
    val height: String?,
    val mapfile: String?
)

data class RoutePart(
    val iconID: String?,
    val instruction: String?,
    val instructionEn: String?,
    val instructionDe: String?,
    val nodes: List<RouteNode>?,
    val landmarks: List<RouteLandmark>?,
    val landmarkFromInstruction: String? // Die wichtige Landmark-ID!
)

data class RouteNode(
    val node: NodeInfo?,
    val edge: EdgeInfo?
)

data class NodeInfo(
    val x: String?,
    val y: String?,
    val id: String?,
    val label: String?,
    val type: String?,
    val name: String?,
    val roomid: String?,
    val isdestination: String?,
    val lsf: String?,
    val oldroomid: String?
)

data class EdgeInfo(
    val dx: String?,
    val dy: String?,
    val cx: String?,
    val cy: String?,
    val ax: String?,
    val ay: String?,
    val bx: String?,
    val by: String?,
    val lengthInMeters: String?,
    val id: String?,
    val type: String?
)

data class RouteLandmark(
    val nameDe: String?,
    val nameEn: String?,
    val x: String?,
    val y: String?,
    val id: String?, // Die Landmark-ID für Feature-Matching
    val type: String?,
    val lsf: String?
)

data class RouteInfo(
    val routeLength: Double,
    val estimatedTime: Int? = null,
    val difficulty: String? = null
)