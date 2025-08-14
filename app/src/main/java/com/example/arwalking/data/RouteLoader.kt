package com.example.arwalking.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.Normalizer
import java.util.Locale

/**
 * RouteLoader - Loads and normalizes route data from JSON files
 * Handles landmark ID slugification and route processing
 */
class RouteLoader(private val context: Context) {
    
    private val gson = Gson()
    
    companion object {
        private const val TAG = "RouteLoader"
        private const val ROUTES_DIR = "routes"
    }
    
    /**
     * Load navigation route from JSON file
     */
    suspend fun loadRoute(filename: String): NavigationRoute? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading route from: $filename")
            
            val jsonString = context.assets.open("$ROUTES_DIR/$filename").use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
            
            val routeData = gson.fromJson(jsonString, RouteData::class.java)
            val navigationRoute = processRouteData(routeData)
            
            Log.d(TAG, "Successfully loaded route with ${navigationRoute.steps.size} steps")
            navigationRoute
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load route file: $filename", e)
            null
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse route JSON: $filename", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading route: $filename", e)
            null
        }
    }
    
    /**
     * Process raw route data into navigation steps
     */
    private fun processRouteData(routeData: RouteData): NavigationRoute {
        val steps = mutableListOf<NavigationStep>()
        var stepIndex = 0
        
        routeData.route.path.forEach { pathElement ->
            pathElement.routeParts.forEach { routePart ->
                val step = createNavigationStep(routePart, stepIndex++)
                steps.add(step)
            }
        }
        
        return NavigationRoute(
            id = "route_${System.currentTimeMillis()}",
            name = routeData.route.path.firstOrNull()?.xmlNameDe ?: "Unknown Route",
            steps = steps,
            landmarkIds = extractLandmarkIds(steps)
        )
    }
    
    /**
     * Create a navigation step from route part
     */
    private fun createNavigationStep(routePart: RoutePart, index: Int): NavigationStep {
        val landmarkId = routePart.landmarkFromInstruction?.let { slugifyLandmarkId(it) }
        val arrowDirection = ArrowDirection.fromInstruction(routePart.instructionDe)
        
        // Get position from first node if available
        val position = routePart.nodes.firstOrNull()?.node?.let { node ->
            Position(
                x = node.x.toFloatOrNull() ?: 0f,
                y = node.y.toFloatOrNull() ?: 0f
            )
        } ?: Position(0f, 0f)
        
        return NavigationStep(
            id = "step_$index",
            instruction = routePart.instruction,
            instructionDe = routePart.instructionDe,
            landmarkId = landmarkId,
            arrowDirection = arrowDirection,
            landmarks = routePart.landmarks,
            position = position
        )
    }
    
    /**
     * Extract all unique landmark IDs from steps
     */
    private fun extractLandmarkIds(steps: List<NavigationStep>): Set<String> {
        return steps.mapNotNull { it.landmarkId }.toSet()
    }
    
    /**
     * Slugify landmark ID for consistent file naming
     * Handles special characters, spaces, and case normalization
     */
    fun slugifyLandmarkId(landmarkId: String): String {
        return try {
            // Normalize unicode characters
            val normalized = Normalizer.normalize(landmarkId, Normalizer.Form.NFD)
            
            // Remove diacritical marks and convert to lowercase
            val cleaned = normalized
                .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
                .lowercase(Locale.getDefault())
            
            // Replace special characters and spaces with underscores
            val slugified = cleaned
                .replace(Regex("[^a-z0-9\\-_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
            
            // Ensure it's not empty
            if (slugified.isBlank()) {
                "landmark_${landmarkId.hashCode().toString().replace("-", "")}"
            } else {
                slugified
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to slugify landmark ID: $landmarkId", e)
            "landmark_${landmarkId.hashCode().toString().replace("-", "")}"
        }
    }
    
    /**
     * Validate landmark mapping for conflicts
     */
    fun validateLandmarkMapping(route: NavigationRoute): ValidationResult {
        val conflicts = mutableListOf<String>()
        val landmarkMap = mutableMapOf<String, MutableList<String>>()
        
        route.steps.forEach { step ->
            step.landmarkId?.let { landmarkId ->
                val originalIds = landmarkMap.getOrPut(landmarkId) { mutableListOf() }
                step.landmarks.forEach { landmark ->
                    if (!originalIds.contains(landmark.id)) {
                        originalIds.add(landmark.id)
                    }
                }
                
                // Check for conflicts (multiple original IDs mapping to same slug)
                if (originalIds.size > 1) {
                    conflicts.add("Landmark ID '$landmarkId' maps to multiple original IDs: $originalIds")
                }
            }
        }
        
        return ValidationResult(
            isValid = conflicts.isEmpty(),
            conflicts = conflicts,
            totalLandmarks = route.landmarkIds.size,
            mappedLandmarks = landmarkMap.size
        )
    }
    
    /**
     * Get available route files
     */
    suspend fun getAvailableRoutes(): List<String> = withContext(Dispatchers.IO) {
        try {
            context.assets.list(ROUTES_DIR)?.filter { it.endsWith(".json") } ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list route files", e)
            emptyList()
        }
    }
}

/**
 * Processed navigation route
 */
data class NavigationRoute(
    val id: String,
    val name: String,
    val steps: List<NavigationStep>,
    val landmarkIds: Set<String>
)

/**
 * Validation result for landmark mapping
 */
data class ValidationResult(
    val isValid: Boolean,
    val conflicts: List<String>,
    val totalLandmarks: Int,
    val mappedLandmarks: Int
)