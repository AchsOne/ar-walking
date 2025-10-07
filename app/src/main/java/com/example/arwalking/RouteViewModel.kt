package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import com.example.arwalking.storage.ArWalkingStorageManager
import com.example.arwalking.storage.SaveResult
import com.example.arwalking.data.RouteRepository
import com.example.arwalking.vision.ContextualFeatureMatcher
import com.example.arwalking.vision.MatchResult

/**
 * RouteViewModel mit integriertem Feature-Matching
 */
class RouteViewModel : ViewModel() {

    private val TAG = "RouteViewModel"

    // Storage & Repository
    private var storageManager: ArWalkingStorageManager? = null
    private var routeRepository: RouteRepository? = null

    // Feature-Matching
    private var featureMatcher: ContextualFeatureMatcher? = null
    private var isFeatureMatchingReady = false

    // Route States
    private val _currentRoute = MutableStateFlow<RouteData?>(null)
    val currentRoute: StateFlow<RouteData?> = _currentRoute.asStateFlow()

    private val _currentNavigationStep = MutableStateFlow(1)
    val currentNavigationStep: StateFlow<Int> = _currentNavigationStep.asStateFlow()

    // Feature-Mapping States
    private val _currentMatches = MutableStateFlow<List<FeatureMatchResult>>(emptyList())
    val currentMatches: StateFlow<List<FeatureMatchResult>> = _currentMatches.asStateFlow()

    private val _isFeatureMappingEnabled = MutableStateFlow(false)
    val isFeatureMappingEnabled: StateFlow<Boolean> = _isFeatureMappingEnabled.asStateFlow()

    // Match-Historie für sequentielle Validierung
    private val matchHistory = mutableListOf<String>()
    private val maxHistorySize = 5

    /**
     * Initialisiert Feature-Mapping System
     */
    fun initializeFeatureMapping(context: Context) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Initialisiere Feature-Mapping System...")

                // Erstelle Matcher
                featureMatcher = ContextualFeatureMatcher(context)

                // Lade alle Landmark-Signaturen
                val success = featureMatcher!!.initialize()

                if (success) {
                    isFeatureMatchingReady = true
                    _isFeatureMappingEnabled.value = true
                    Log.i(TAG, "Feature-Mapping bereit!")
                } else {
                    Log.w(TAG, "Feature-Mapping konnte nicht initialisiert werden")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei Feature-Mapping Init: ${e.message}")
                _isFeatureMappingEnabled.value = false
            }
        }
    }

    /**
     * Verarbeitet Kamera-Frame für Feature-Matching
     * DIES IST DIE HAUPTMETHODE FÜR NAVIGATION
     */
    fun processFrameForFeatureMatching(cameraBitmap: Bitmap) {
        viewModelScope.launch {
            try {
                if (!isFeatureMatchingReady) {
                    Log.w(TAG, "Feature-Matching nicht bereit")
                    return@launch
                }

                // Hole aktuellen Navigationsschritt
                val currentStep = getCurrentStep()
                if (currentStep == null) {
                    Log.w(TAG, "Kein aktueller Navigationsschritt")
                    return@launch
                }

                // Finde erwarteten Landmark aus dem Schritt
                val expectedLandmark = getExpectedLandmark(currentStep)
                if (expectedLandmark == null) {
                    Log.w(TAG, "Kein erwarteter Landmark im Schritt")
                    return@launch
                }

                Log.i(TAG, "=== Frame-Processing ===")
                Log.i(TAG, "Schritt ${currentStep.stepNumber}: ${currentStep.instruction}")
                Log.i(TAG, "Erwarteter Landmark: $expectedLandmark")

                // Finde mögliche alternative Landmarks (z.B. Nachbar-Landmarks)
                val alternatives = getPossibleAlternatives(currentStep)

                // Führe kontextuelles Matching durch
                val matchResult = featureMatcher!!.matchExpectedLandmark(
                    cameraFrame = cameraBitmap,
                    expectedLandmarkId = expectedLandmark,
                    allowedAlternatives = alternatives
                )

                // Verarbeite Match-Ergebnis
                handleMatchResult(matchResult, expectedLandmark)

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Frame-Processing: ${e.message}")
                _currentMatches.value = emptyList()
            }
        }
    }

    /**
     * Verarbeitet Match-Ergebnis und aktualisiert UI
     */
    private fun handleMatchResult(matchResult: MatchResult, expectedLandmark: String) {
        when (matchResult) {
            is MatchResult.StrongMatch -> {
                Log.i(TAG, "STARKER MATCH: ${matchResult.landmarkId} (${(matchResult.confidence * 100).toInt()}%)")

                // Füge zu Historie hinzu
                addToMatchHistory(matchResult.landmarkId)

                // Erstelle FeatureMatchResult für UI
                val result = FeatureMatchResult(
                    landmark = createLandmark(matchResult.landmarkId, matchResult.confidence),
                    matchCount = (matchResult.confidence * 100).toInt(),
                    confidence = matchResult.confidence,
                    distance = 2.0f // Nahbereich
                )

                _currentMatches.value = listOf(result)

                // Prüfe ob User länger am gleichen Ort steht (für Auto-Advance)
                checkForStablePosition(matchResult.landmarkId)
            }

            is MatchResult.WeakMatch -> {
                Log.w(TAG, "SCHWACHER MATCH: ${matchResult.landmarkId} (${(matchResult.confidence * 100).toInt()}%) - ${matchResult.warning}")

                val result = FeatureMatchResult(
                    landmark = createLandmark(matchResult.landmarkId, matchResult.confidence),
                    matchCount = (matchResult.confidence * 100).toInt(),
                    confidence = matchResult.confidence,
                    distance = 5.0f // Mittelbereich
                )

                _currentMatches.value = listOf(result)

                // Nicht zur Historie hinzufügen - zu unsicher
            }

            is MatchResult.Ambiguous -> {
                Log.e(TAG, "MEHRDEUTIG: Erwartete $expectedLandmark aber ${matchResult.confusingLandmark} passt besser!")
                Log.e(TAG, "Erwartet: ${(matchResult.expectedSimilarity * 100).toInt()}% vs Verwechslung: ${(matchResult.confusingSimilarity * 100).toInt()}%")

                // Zeige beide Kandidaten
                val results = listOf(
                    FeatureMatchResult(
                        landmark = createLandmark(matchResult.confusingLandmark, matchResult.confusingSimilarity),
                        matchCount = (matchResult.confusingSimilarity * 100).toInt(),
                        confidence = matchResult.confusingSimilarity,
                        distance = 3.0f
                    ),
                    FeatureMatchResult(
                        landmark = createLandmark(expectedLandmark, matchResult.expectedSimilarity),
                        matchCount = (matchResult.expectedSimilarity * 100).toInt(),
                        confidence = matchResult.expectedSimilarity,
                        distance = 5.0f
                    )
                )

                _currentMatches.value = results

                // Warnung: User ist wahrscheinlich am falschen Ort
                Log.w(TAG, "USER WAHRSCHEINLICH AM FALSCHEN ORT!")
            }

            is MatchResult.NoMatch -> {
                Log.w(TAG, "KEIN MATCH: ${matchResult.reason}")
                _currentMatches.value = emptyList()
            }
        }
    }

    /**
     * Findet erwarteten Landmark aus Navigationsschritt
     */
    private fun getExpectedLandmark(step: NavigationStep): String? {
        // Prüfe ob Schritt Landmarks hat
        if (step.landmarks.isEmpty()) {
            return null
        }

        // Extrahiere ID vom ersten Landmark
        return step.landmarks.first().id  // Änderung: .id hinzugefügt
    }

    /**
     * Findet mögliche alternative Landmarks (z.B. vom nächsten Schritt)
     */
    private fun getPossibleAlternatives(currentStep: NavigationStep): List<String> {
        val alternatives = mutableListOf<String>()

        try {
            val steps = getCurrentNavigationSteps()
            val currentIndex = steps.indexOf(currentStep)

            // Füge Landmarks vom nächsten Schritt hinzu (falls User schon weiter ist)
            if (currentIndex >= 0 && currentIndex < steps.size - 1) {
                val nextStep = steps[currentIndex + 1]
                alternatives.addAll(nextStep.landmarks.map { it.id })  // Änderung: .map { it.id }
            }

            // Füge Landmarks vom vorherigen Schritt hinzu (falls User zurück ist)
            if (currentIndex > 0) {
                val prevStep = steps[currentIndex - 1]
                alternatives.addAll(prevStep.landmarks.map { it.id })  // Änderung: .map { it.id }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Finden der Alternativen: ${e.message}")
        }

        return alternatives.distinct()
    }

    /**
     * Fügt Match zur Historie hinzu
     */
    private fun addToMatchHistory(landmarkId: String) {
        matchHistory.add(landmarkId)

        if (matchHistory.size > maxHistorySize) {
            matchHistory.removeAt(0)
        }

        Log.d(TAG, "Match-Historie: ${matchHistory.takeLast(3).joinToString()}")
    }

    /**
     * Prüft ob User stabil am gleichen Ort steht
     */
    private fun checkForStablePosition(landmarkId: String) {
        val recentMatches = matchHistory.takeLast(3)

        if (recentMatches.size >= 3 && recentMatches.all { it == landmarkId }) {
            Log.i(TAG, "STABILE POSITION: User steht seit ${recentMatches.size} Frames bei $landmarkId")

            // Optional: Auto-advance zum nächsten Schritt
            // nextNavigationStep()
        }
    }

    /**
     * Erstellt FeatureLandmark für UI
     */
    private fun createLandmark(landmarkId: String, confidence: Float): FeatureLandmark {
        return FeatureLandmark(
            id = landmarkId,
            name = landmarkId,
            description = "Landmark $landmarkId",
            position = Position(0.0, 0.0, 0.0),
            imageUrl = "",
            confidence = confidence
        )
    }

    // =====================================
    // BESTEHENDE METHODEN (unverändert)
    // =====================================

    fun loadNavigationRoute(context: Context): NavigationRoute? {
        return try {
            Log.i(TAG, "Lade Route aus JSON-Datei...")

            if (routeRepository == null) {
                routeRepository = RouteRepository(context)
            }

            viewModelScope.launch {
                val routeData = routeRepository?.getRouteFromAssets("route.json")
                _currentRoute.value = routeData

                if (routeData != null) {
                    Log.i(TAG, "Route erfolgreich aus JSON geladen")
                } else {
                    Log.w(TAG, "Keine Route in JSON-Datei gefunden")
                }
            }

            NavigationRoute(
                id = "default_route",
                name = "Standard Route",
                description = "Lade Route...",
                totalLength = 0.0,
                steps = emptyList()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der Route: ${e.message}")
            null
        }
    }

    fun initializeStorage(context: Context) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Initialisiere Storage-System...")
                storageManager = ArWalkingStorageManager(context)

                val availableLandmarks = storageManager!!.getAvailableProjectLandmarks()
                Log.i(TAG, "Verfügbare Landmark-Bilder: ${availableLandmarks.size}")

                _isFeatureMappingEnabled.value = true

                val status = storageManager!!.getStorageStatus()
                Log.i(TAG, "Storage-Status: ${status.getHealthStatus()}")

            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei Storage-Initialisierung: ${e.message}")
                _isFeatureMappingEnabled.value = false
            }
        }
    }

    fun enableStorageSystemImmediately(context: Context) {
        viewModelScope.launch {
            try {
                if (storageManager == null) {
                    initializeStorage(context)
                    return@launch
                }

                _isFeatureMappingEnabled.value = true
                Log.i(TAG, "Storage-System sofort aktiviert")

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim sofortigen Aktivieren: ${e.message}")
            }
        }
    }

    fun setCurrentNavigationStep(step: Int) {
        _currentNavigationStep.value = step
        Log.d(TAG, "Navigationsschritt gesetzt: $step")
    }

    fun nextNavigationStep() {
        val currentStep = _currentNavigationStep.value
        _currentNavigationStep.value = currentStep + 1
        Log.d(TAG, "Nächster Navigationsschritt: ${currentStep + 1}")
    }

    fun previousNavigationStep() {
        val currentStep = _currentNavigationStep.value
        if (currentStep > 1) {
            _currentNavigationStep.value = currentStep - 1
            Log.d(TAG, "Vorheriger Navigationsschritt: ${currentStep - 1}")
        }
    }

    private fun convertToNavigationRoute(routeData: RouteData): NavigationRoute {
        val steps = mutableListOf<NavigationStep>()
        var stepNumber = 1

        routeData.route.path.forEach { pathItem ->
            pathItem.routeParts.forEach { routePart ->
                steps.add(
                    NavigationStep(
                        stepNumber = stepNumber++,
                        instruction = routePart.instructionDe,
                        building = pathItem.xmlName,
                        floor = 0,
                        landmarks = routePart.landmarks,
                        distance = routePart.distance ?: 0.0,
                        estimatedTime = routePart.duration ?: 60
                    )
                )
            }
        }

        return NavigationRoute(
            id = "route_${System.currentTimeMillis()}",
            name = "Navigation Route",
            description = "Generated from route data",
            totalLength = steps.sumOf { it.distance },
            steps = steps,
            estimatedTime = steps.sumOf { it.estimatedTime }
        )
    }

    fun getCurrentNavigationSteps(): List<NavigationStep> {
        return try {
            val route = _currentRoute.value
            if (route != null) {
                convertToNavigationRoute(route).steps
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der Navigationsschritte: ${e.message}")
            emptyList()
        }
    }

    fun getCurrentStep(): NavigationStep? {
        return try {
            val steps = getCurrentNavigationSteps()
            val currentStepNumber = _currentNavigationStep.value

            if (steps.isNotEmpty() && currentStepNumber > 0 && currentStepNumber <= steps.size) {
                steps[currentStepNumber - 1]
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des aktuellen Schritts: ${e.message}")
            null
        }
    }

    fun logNavigationRoute(navigationRoute: NavigationRoute) {
        Log.i(TAG, "=== Navigation Route Details ===")
        Log.i(TAG, "Route ID: ${navigationRoute.id}")
        Log.i(TAG, "Route Name: ${navigationRoute.name}")
        Log.i(TAG, "Anzahl Schritte: ${navigationRoute.steps.size}")

        navigationRoute.steps.forEachIndexed { index, step ->
            Log.d(TAG, "Schritt ${index + 1}: ${step.instruction}")
            Log.d(TAG, "  - Landmarks: ${step.landmarks.joinToString()}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                storageManager?.logPerformanceSummary()
                Log.i(TAG, "RouteViewModel bereinigt")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Bereinigen: ${e.message}")
            }
        }
    }
    // In RouteViewModel.kt - Füge diese Methoden hinzu:

    fun getCurrentStartPoint(): String? {
        return try {
            val steps = getCurrentNavigationSteps()
            steps.firstOrNull()?.landmarks?.firstOrNull()?.name
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Startpunkts: ${e.message}")
            null
        }
    }

    fun getCurrentEndPoint(): String? {
        return try {
            val steps = getCurrentNavigationSteps()
            steps.lastOrNull()?.landmarks?.lastOrNull()?.name
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Endpunkts: ${e.message}")
            null
        }
    }

    fun getAvailableLandmarks(): List<FeatureLandmark> {
        return try {
            val steps = getCurrentNavigationSteps()
            val allLandmarks = mutableListOf<FeatureLandmark>()

            steps.forEach { step ->
                step.landmarks.forEach { landmark ->
                    allLandmarks.add(
                        FeatureLandmark(
                            id = landmark.id,
                            name = landmark.name,
                            description = landmark.name,
                            position = Position(0.0, 0.0, 0.0),
                            imageUrl = "",
                            confidence = 0.0f
                        )
                    )
                }
            }

            allLandmarks.distinctBy { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der Landmarks: ${e.message}")
            emptyList()
        }
    }

    fun startFrameProcessing() {
        viewModelScope.launch {
            Log.i(TAG, "Frame-Processing gestartet")
            _isFeatureMappingEnabled.value = true
        }
    }
}