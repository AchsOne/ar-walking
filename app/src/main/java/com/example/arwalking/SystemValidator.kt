package com.example.arwalking

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * System-Validator zur Überprüfung der AR-Walking-Funktionalität
 */
class SystemValidator(private val context: Context) {
    
    private val TAG = "SystemValidator"
    
    /**
     * Validiert das gesamte AR-Walking-System
     */
    fun validateSystem(routeViewModel: RouteViewModel) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i(TAG, "=== AR-WALKING SYSTEM VALIDATION START ===")
            
            // 1. Route-Loading validieren
            validateRouteLoading(routeViewModel)
            
            // 2. Feature-Mapping validieren
            validateFeatureMapping(routeViewModel)
            
            // 3. Landmark-Zuordnung validieren
            validateLandmarkMapping(routeViewModel)
            
            // 4. Navigation-Schritte validieren
            validateNavigationSteps(routeViewModel)
            
            Log.i(TAG, "=== AR-WALKING SYSTEM VALIDATION END ===")
        }
    }
    
    private fun validateRouteLoading(routeViewModel: RouteViewModel) {
        Log.i(TAG, "--- Validiere Route-Loading ---")
        
        val route = routeViewModel.loadNavigationRoute(context)
        if (route != null) {
            Log.i(TAG, "✓ Route erfolgreich geladen")
            Log.i(TAG, "  Schritte: ${route.steps.size}")
            Log.i(TAG, "  Startpunkt: ${routeViewModel.getCurrentStartPoint()}")
            Log.i(TAG, "  Endpunkt: ${routeViewModel.getCurrentEndPoint()}")
        } else {
            Log.e(TAG, "✗ Route-Loading fehlgeschlagen")
        }
    }
    
    private suspend fun validateFeatureMapping(routeViewModel: RouteViewModel) {
        Log.i(TAG, "--- Validiere Feature-Mapping ---")
        
        routeViewModel.initializeStorage(context)
        
        // Warte kurz auf Initialisierung
        kotlinx.coroutines.delay(1000)
        
        val landmarks = routeViewModel.getAvailableLandmarks()
        if (landmarks.isNotEmpty()) {
            Log.i(TAG, "✓ Feature-Mapping initialisiert")
            Log.i(TAG, "  Verfügbare Landmarks: ${landmarks.size}")
            landmarks.forEach { landmark ->
                Log.i(TAG, "    - ${landmark.name} (${landmark.id})")
            }
        } else {
            Log.e(TAG, "✗ Feature-Mapping Initialisierung fehlgeschlagen")
        }
    }
    
    private fun validateLandmarkMapping(routeViewModel: RouteViewModel) {
        Log.i(TAG, "--- Validiere Landmark-Zuordnung ---")
        
        val steps = routeViewModel.getCurrentNavigationSteps()
        val landmarks = routeViewModel.getAvailableLandmarks()
        
        var mappedSteps = 0
        steps.forEach { step ->
            val hasLandmarks = step.landmarkIds.isNotEmpty()
            if (hasLandmarks) {
                mappedSteps++
                Log.i(TAG, "✓ Schritt ${step.stepNumber}: ${step.landmarkIds.size} Landmarks")
                step.landmarkIds.forEach { landmarkId ->
                    val landmark = landmarks.find { it.id == landmarkId }
                    if (landmark != null) {
                        Log.i(TAG, "    ✓ ${landmarkId} -> ${landmark.name}")
                    } else {
                        Log.w(TAG, "    ✗ ${landmarkId} -> Landmark nicht gefunden")
                    }
                }
            } else {
                Log.w(TAG, "✗ Schritt ${step.stepNumber}: Keine Landmarks zugeordnet")
            }
        }
        
        Log.i(TAG, "Landmark-Zuordnung: $mappedSteps/${steps.size} Schritte haben Landmarks")
    }
    
    private fun validateNavigationSteps(routeViewModel: RouteViewModel) {
        Log.i(TAG, "--- Validiere Navigation-Schritte ---")
        
        val steps = routeViewModel.getCurrentNavigationSteps()
        if (steps.isNotEmpty()) {
            Log.i(TAG, "✓ Navigation-Schritte verfügbar: ${steps.size}")
            
            steps.forEachIndexed { index, step ->
                Log.i(TAG, "  Schritt ${index + 1}:")
                Log.i(TAG, "    Anweisung: ${step.instruction}")
                Log.i(TAG, "    Gebäude: ${step.building}")
                Log.i(TAG, "    Landmarks: ${step.landmarkIds.joinToString(", ")}")
            }
            
            // Test der Schritt-Navigation
            Log.i(TAG, "--- Teste Schritt-Navigation ---")
            routeViewModel.setCurrentNavigationStep(1)
            val currentStep = routeViewModel.getCurrentStep()
            if (currentStep != null) {
                Log.i(TAG, "✓ Aktueller Schritt: ${currentStep.instruction}")
            } else {
                Log.e(TAG, "✗ Aktueller Schritt nicht verfügbar")
            }
            
        } else {
            Log.e(TAG, "✗ Keine Navigation-Schritte verfügbar")
        }
    }
    
    /**
     * Simuliert Feature-Matching für Testzwecke
     */
    fun simulateFeatureMatching(routeViewModel: RouteViewModel, landmarkId: String) {
        Log.i(TAG, "--- Simuliere Feature-Matching für $landmarkId ---")
        
        val landmarks = routeViewModel.getAvailableLandmarks()
        val testLandmark = landmarks.find { it.id == landmarkId }
        
        if (testLandmark != null) {
            // Simuliere ein Match-Ergebnis
            val mockMatch = FeatureMatchResult(
                landmark = testLandmark,
                matchCount = 25,
                confidence = 0.85f,
                distance = 2.5f,
                screenPosition = android.graphics.PointF(400f, 300f)
            )
            
            Log.i(TAG, "✓ Simuliertes Match erstellt:")
            Log.i(TAG, "    Landmark: ${mockMatch.landmark.name}")
            Log.i(TAG, "    Confidence: ${mockMatch.confidence}")
            Log.i(TAG, "    Matches: ${mockMatch.matchCount}")
            Log.i(TAG, "    Position: (${mockMatch.screenPosition?.x}, ${mockMatch.screenPosition?.y})")
            
        } else {
            Log.e(TAG, "✗ Landmark $landmarkId nicht gefunden")
        }
    }
}