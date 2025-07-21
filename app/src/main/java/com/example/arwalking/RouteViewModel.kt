// RouteViewModel.kt
package com.example.arwalking

import RouteData
import androidx.lifecycle.ViewModel
import android.util.Log
import android.content.Context
import com.google.gson.Gson

class RouteViewModel : ViewModel() {

    private val TAG = "RouteViewModel"

    // JSON-Parsing-Logik
    fun loadAndParseRoute(context: Context) {
        try {
            Log.i(TAG, "Route wird geladen...")

            // JSON aus Assets laden
            val jsonString = loadJSONFromAsset(context, "route.json")

            // JSON mit Gson parsen
            val gson = Gson()
            val routeData = gson.fromJson(jsonString, RouteData::class.java)

            // Wegbeschreibung extrahieren und loggen
            extractAndLogRoute(routeData)

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der Route: ${e.message}")
        }
    }

    private fun loadJSONFromAsset(context: Context, filename: String): String {
        return context.assets.open(filename).bufferedReader().use { it.readText() }
    }

    private fun extractAndLogRoute(routeData: RouteData) {
        Log.i(TAG, "=== WEGBESCHREIBUNG START ===")
        Log.i(TAG, "Gesamte Routenlänge: ${routeData.route.routeInfo.routeLength} Meter")
        Log.i(TAG, "")

        var stepNumber = 1

        routeData.route.path.forEach { pathItem ->
            Log.i(TAG, "Gebäude: ${pathItem.xmlName}")

            pathItem.routeParts.forEach { routePart ->
                // Instruction loggen
                Log.i(TAG, "Schritt $stepNumber: ${routePart.instructionDe}")

                // Landmarks zu diesem Schritt
                if (routePart.landmarks.isNotEmpty()) {
                    Log.i(TAG, "  Landmarks:")
                    routePart.landmarks.forEach { landmark ->
                        Log.i(TAG, "    - ID: ${landmark.id}")
                        Log.i(TAG, "      Name: ${landmark.nameDe}")
                        Log.i(TAG, "      Typ: ${landmark.type}")
                        Log.i(TAG, "      Position: (${landmark.x}, ${landmark.y})")
                        landmark.lsf?.let {
                            Log.i(TAG, "      LSF: $it")
                        }
                    }
                } else {
                    Log.i(TAG, "  Keine Landmarks")
                }

                Log.i(TAG, "")
                stepNumber++
            }
        }

        Log.i(TAG, "=== WEGBESCHREIBUNG ENDE ===")
    }
}