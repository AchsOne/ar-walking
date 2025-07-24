// RouteViewModel.kt (erweitert)
package com.example.arwalking

import RouteData
import androidx.lifecycle.ViewModel
import android.util.Log
import android.content.Context
import com.google.gson.Gson

class RouteViewModel : ViewModel() {

    private val TAG = "RouteViewModel"

    // Die neue Funktion die das NavigationRoute-Objekt zurückgibt
    fun loadNavigationRoute(context: Context): NavigationRoute? {
        return try {
            Log.i(TAG, "Route wird geladen...")

            // JSON laden und parsen (wie vorher)
            val jsonString = loadJSONFromAsset(context, "route.json")
            val gson = Gson()
            val routeData = gson.fromJson(jsonString, RouteData::class.java)

            // In NavigationRoute umwandeln
            convertToNavigationRoute(routeData)

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der Route: ${e.message}")
            null
        }
    }

    // Konvertierung von RouteData zu NavigationRoute
    private fun convertToNavigationRoute(routeData: RouteData): NavigationRoute {
        val steps = mutableListOf<NavigationStep>()
        var stepNumber = 1

        routeData.route.path.forEach { pathItem ->
            pathItem.routeParts.forEach { routePart ->
                val landmarkIds = routePart.landmarks.map { it.id }

                val step = NavigationStep(
                    stepNumber = stepNumber,
                    instruction = routePart.instructionDe,
                    building = pathItem.xmlName,
                    landmarkIds = landmarkIds
                )

                steps.add(step)
                stepNumber++
            }
        }

        return NavigationRoute(
            totalLength = routeData.route.routeInfo.routeLength,
            steps = steps
        )
    }

    // Optional: Für Debugging - die alte Logging-Funktion angepasst
    fun logNavigationRoute(navigationRoute: NavigationRoute) {
        Log.i(TAG, "=== NAVIGATION ROUTE START ===")
        Log.i(TAG, "Gesamte Routenlänge: ${navigationRoute.totalLength} Meter")
        Log.i(TAG, "Anzahl Schritte: ${navigationRoute.steps.size}")
        Log.i(TAG, "")

        navigationRoute.steps.forEach { step ->
            Log.i(TAG, "Schritt ${step.stepNumber}: ${step.instruction}")
            Log.i(TAG, "  Gebäude: ${step.building}")
            if (step.landmarkIds.isNotEmpty()) {
                Log.i(TAG, "  Landmark IDs: ${step.landmarkIds.joinToString(", ")}")
            } else {
                Log.i(TAG, "  Keine Landmarks")
            }
            Log.i(TAG, "")
        }

        Log.i(TAG, "=== NAVIGATION ROUTE ENDE ===")
    }

    private fun loadJSONFromAsset(context: Context, filename: String): String {
        return context.assets.open(filename).bufferedReader().use { it.readText() }
    }
}