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

/**
 * ViewModel für Route-Management und Feature-Mapping
 * Verwendet das neue Storage-System - kein Trainingsmodus erforderlich!
 */
class RouteViewModel : ViewModel() {
    
    private val TAG = "RouteViewModel"
    
    // Neues Storage-System (ersetzt LocalFeatureMapManager)
    private var storageManager: ArWalkingStorageManager? = null
    
    // Route Repository für JSON-Daten
    private var routeRepository: RouteRepository? = null
    

    
    // State für geladene Route aus JSON
    private val _currentRoute = MutableStateFlow<RouteData?>(null)
    val currentRoute: StateFlow<RouteData?> = _currentRoute.asStateFlow()
    
    // State für Feature-Navigation
    private val _featureNavigationRoute = MutableStateFlow<FeatureNavigationRoute?>(null)
    val featureNavigationRoute: StateFlow<FeatureNavigationRoute?> = _featureNavigationRoute.asStateFlow()
    
    private val _currentMatches = MutableStateFlow<List<FeatureMatchResult>>(emptyList())
    val currentMatches: StateFlow<List<FeatureMatchResult>> = _currentMatches.asStateFlow()
    
    private val _isFeatureMappingEnabled = MutableStateFlow(false)
    val isFeatureMappingEnabled: StateFlow<Boolean> = _isFeatureMappingEnabled.asStateFlow()
    
    // State für aktuellen Navigationsschritt
    private val _currentNavigationStep = MutableStateFlow(1)
    val currentNavigationStep: StateFlow<Int> = _currentNavigationStep.asStateFlow()

    /**
     * Lädt die Route aus der JSON-Datei
     */
    fun loadNavigationRoute(context: Context): NavigationRoute? {
        return try {
            Log.i(TAG, "Lade Route aus JSON-Datei...")
            
            // Initialisiere RouteRepository falls noch nicht geschehen
            if (routeRepository == null) {
                routeRepository = RouteRepository(context)
            }
            
            // Lade Route aus JSON-Datei
            viewModelScope.launch {
                val routeData = routeRepository?.getRouteFromAssets("route.json")
                _currentRoute.value = routeData
                
                if (routeData != null) {
                    Log.i(TAG, "Route erfolgreich aus JSON geladen")
                    // Konvertiere RouteData zu NavigationRoute für Feature-Mapping
                    val navigationRoute = convertToNavigationRoute(routeData)
                    Log.i(TAG, "Route konvertiert: ${navigationRoute.steps.size} Schritte")
                } else {
                    Log.w(TAG, "Keine Route in JSON-Datei gefunden")
                }
            }
            
            // Erstelle eine Standard-Route für sofortige Rückgabe
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
    
    /**
     * Loggt Informationen über die geladene NavigationRoute
     */
    fun logNavigationRoute(navigationRoute: NavigationRoute) {
        Log.i(TAG, "=== Navigation Route Details ===")
        Log.i(TAG, "Route ID: ${navigationRoute.id}")
        Log.i(TAG, "Route Name: ${navigationRoute.name}")
        Log.i(TAG, "Route Description: ${navigationRoute.description}")
        Log.i(TAG, "Anzahl Schritte: ${navigationRoute.steps.size}")
        
        navigationRoute.steps.forEachIndexed { index, step ->
            Log.d(TAG, "Schritt ${index + 1}: ${step.instruction}")
            Log.d(TAG, "  - Building: ${step.building}")
            Log.d(TAG, "  - Floor: ${step.floor}")
            Log.d(TAG, "  - Distance: ${step.distance}m")
            Log.d(TAG, "  - Estimated Time: ${step.estimatedTime}s")
            Log.d(TAG, "  - Landmarks: ${step.landmarks.size}")
        }
        
        Log.i(TAG, "=== Ende Route Details ===")
    }
    
    /**
     * Konvertiert RouteData zu NavigationRoute
     */
    private fun convertToNavigationRoute(routeData: RouteData): NavigationRoute {
        val steps = mutableListOf<NavigationStep>()
        var stepNumber = 1
        
        // Durchlaufe alle PathItems und RouteParts
        routeData.route.path.forEach { pathItem ->
            pathItem.routeParts.forEach { routePart ->
                steps.add(
                    NavigationStep(
                        stepNumber = stepNumber++,
                        instruction = routePart.instructionDe,
                        building = pathItem.xmlName,
                        floor = 0, // TODO: Extract from levelInfo if available
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
    

    
    /**
     * Initialisiert das neue Storage-System (ersetzt Feature-Mapping)
     */
    fun initializeStorage(context: Context) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Initialisiere neues Storage-System...")
                
                // Neues Storage-System initialisieren
                storageManager = ArWalkingStorageManager(context)
                
                // Prüfe verfügbare Bilder im Projektverzeichnis
                val availableLandmarks = storageManager!!.getAvailableProjectLandmarks()
                Log.i(TAG, "Verfügbare Landmark-Bilder: ${availableLandmarks.size}")
                
                availableLandmarks.take(5).forEach { landmark ->
                    Log.d(TAG, "- ${landmark.id} (${landmark.filename})")
                }
                
                // Storage-System ist immer verfügbar (auch ohne Bilder)
                _isFeatureMappingEnabled.value = true
                
                Log.i(TAG, "Storage-System erfolgreich initialisiert")
                
                // Logge Storage-Status
                val status = storageManager!!.getStorageStatus()
                Log.i(TAG, "Storage-Status: ${status.getHealthStatus()}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei Storage-Initialisierung: ${e.message}")
                _isFeatureMappingEnabled.value = false
            }
        }
    }
    
    /**
     * Lädt verfügbare Landmarks aus dem Projektverzeichnis
     */
    fun loadAvailableLandmarks(context: Context) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Lade verfügbare Landmarks aus Projektverzeichnis...")
                
                if (storageManager == null) {
                    initializeStorage(context)
                }
                
                val landmarks = storageManager?.getAvailableProjectLandmarks() ?: emptyList()
                
                if (landmarks.isNotEmpty()) {
                    Log.i(TAG, "Verfügbare Landmarks: ${landmarks.size}")
                    landmarks.forEach { landmark ->
                        Log.d(TAG, "- ${landmark.id} (${landmark.filename})")
                    }
                } else {
                    Log.w(TAG, "Keine Landmark-Bilder im Projektverzeichnis gefunden")
                    Log.i(TAG, "Tipp: Kopiere Bilder in /Users/florian/Documents/GitHub/ar-walking/landmark_images/")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden der Landmarks: ${e.message}")
            }
        }
    }
    
    /**
     * Lädt ein Landmark-Bild für die Anzeige
     */
    suspend fun loadLandmarkImage(landmarkId: String): Bitmap? {
        return try {
            if (storageManager == null) {
                Log.w(TAG, "Storage-Manager nicht initialisiert")
                return null
            }
            
            val image = storageManager!!.loadFullImage(landmarkId)
            if (image != null) {
                Log.d(TAG, "Landmark-Bild geladen: $landmarkId (${image.width}x${image.height})")
            } else {
                Log.w(TAG, "Landmark-Bild nicht gefunden: $landmarkId")
            }
            image
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Landmark-Bildes $landmarkId: ${e.message}")
            null
        }
    }
    
    /**
     * Lädt ein Landmark-Thumbnail für die Anzeige
     */
    suspend fun loadLandmarkThumbnail(landmarkId: String): Bitmap? {
        return try {
            if (storageManager == null) {
                Log.w(TAG, "Storage-Manager nicht initialisiert")
                return null
            }
            
            val thumbnail = storageManager!!.loadThumbnail(landmarkId)
            if (thumbnail != null) {
                Log.d(TAG, "Landmark-Thumbnail geladen: $landmarkId (${thumbnail.width}x${thumbnail.height})")
            } else {
                Log.w(TAG, "Landmark-Thumbnail nicht gefunden: $landmarkId")
            }
            thumbnail
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Landmark-Thumbnails $landmarkId: ${e.message}")
            null
        }
    }
    
    /**
     * Fügt einen neuen Landmark hinzu (nur lokale Speicherung)
     */
    suspend fun addLandmark(
        context: Context,
        bitmap: Bitmap,
        landmarkId: String,
        landmarkName: String,
        description: String,
        onProgress: (String) -> Unit = {}
    ): Boolean {
        return try {
            Log.i(TAG, "Füge neuen Landmark hinzu: $landmarkId")
            
            if (storageManager != null) {
                val saveResult = storageManager!!.saveImage(
                    bitmap = bitmap,
                    landmarkId = landmarkId,
                    landmarkName = landmarkName,
                    description = description,
                    category = "Training"
                )
                
                when (saveResult) {
                    is SaveResult.Success -> {
                        Log.i(TAG, "Landmark erfolgreich lokal gespeichert: $landmarkId")
                        true
                    }
                    is SaveResult.Error -> {
                        Log.e(TAG, "Fehler beim lokalen Speichern: ${saveResult.message}")
                        false
                    }
                }
            } else {
                Log.w(TAG, "Storage-Manager nicht verfügbar")
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Hinzufügen des Landmarks: ${e.message}")
            false
        }
    }
    

    
    /**
     * Aktiviert Storage-System sofort
     */
    fun enableStorageSystemImmediately(context: Context) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Aktiviere Storage-System sofort...")
                
                // Stelle sicher, dass Storage-System initialisiert ist
                if (storageManager == null) {
                    Log.w(TAG, "Storage-Manager nicht initialisiert - initialisiere jetzt...")
                    initializeStorage(context)
                    return@launch
                }
                
                _isFeatureMappingEnabled.value = true
                Log.i(TAG, "Storage-System sofort aktiviert")
                
                // Lade verfügbare Landmarks
                loadAvailableLandmarks(context)
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim sofortigen Aktivieren des Feature Mappings: ${e.message}")
            }
        }
    }
    
    /**
     * Prüft ob die App im Emulator läuft
     */
    private fun isEmulatorDevice(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK built for x86") ||
                android.os.Build.MANUFACTURER.contains("Genymotion") ||
                android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic") ||
                "google_sdk" == android.os.Build.PRODUCT)
    }
    

    
    /**
     * Lädt ein Thumbnail (schnell) - verwendet neues Storage-System
     */
    suspend fun loadThumbnail(landmarkId: String): android.graphics.Bitmap? {
        return storageManager?.loadThumbnail(landmarkId)
    }
    
    /**
     * Lädt ein Vollbild (mit Cache) - verwendet neues Storage-System
     */
    suspend fun loadFullImage(landmarkId: String): android.graphics.Bitmap? {
        return storageManager?.loadFullImage(landmarkId)
    }
    
    /**
     * Löscht ein Landmark - verwendet neues Storage-System
     */
    suspend fun deleteLandmark(landmarkId: String): Boolean {
        return storageManager?.deleteLandmark(landmarkId) ?: false
    }
    
    /**
     * Gibt Performance-Informationen zurück
     */
    fun getPerformanceInfo(): String {
        return if (storageManager != null) {
            val status = runBlocking { storageManager!!.getStorageStatus() }
            "Storage-Status: ${status.getHealthStatus()}, Bilder: ${status.totalImages}, Cache-Hit-Rate: ${String.format("%.1f", status.cacheHitRate)}%"
        } else {
            "Storage-System nicht verfügbar"
        }
    }
    
    /**
     * Bereinigt Cache und defekte Dateien
     */
    suspend fun cleanup(): com.example.arwalking.storage.CleanupSummary? {
        return storageManager?.cleanup()
    }
    
    /**
     * Lädt verfügbare Landmarks aus dem Storage-System
     */
    private fun loadAvailableStorageData() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Lade verfügbare Storage-Daten...")
                
                if (storageManager != null) {
                    val landmarks = storageManager!!.getAvailableProjectLandmarks()
                    Log.i(TAG, "Verfügbare Landmarks: ${landmarks.size}")
                    
                    val status = storageManager!!.getStorageStatus()
                    Log.i(TAG, "Storage-Status: ${status.getHealthStatus()}")
                } else {
                    Log.w(TAG, "Storage-Manager nicht verfügbar")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden der Feature Maps: ${e.message}")
            }
        }
    }
    
    /**
     * Setzt den aktuellen Navigationsschritt
     */
    fun setCurrentNavigationStep(step: Int) {
        _currentNavigationStep.value = step
        Log.d(TAG, "Navigationsschritt gesetzt: $step")
    }
    
    /**
     * Geht zum nächsten Navigationsschritt
     */
    fun nextNavigationStep() {
        val currentStep = _currentNavigationStep.value
        _currentNavigationStep.value = currentStep + 1
        Log.d(TAG, "Nächster Navigationsschritt: ${currentStep + 1}")
    }
    
    /**
     * Geht zum vorherigen Navigationsschritt
     */
    fun previousNavigationStep() {
        val currentStep = _currentNavigationStep.value
        if (currentStep > 1) {
            _currentNavigationStep.value = currentStep - 1
            Log.d(TAG, "Vorheriger Navigationsschritt: ${currentStep - 1}")
        }
    }
    
    /**
     * Konvertiert Bitmap zu Base64 String
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
    }
    
    /**
     * Erstellt einen neuen Landmark - verwendet neues Storage-System
     */
    suspend fun createLandmark(
        landmarkId: String,
        name: String,
        description: String,
        bitmap: Bitmap
    ): Boolean {
        return try {
            if (storageManager != null) {
                val result = storageManager!!.saveImage(
                    bitmap = bitmap,
                    landmarkId = landmarkId,
                    landmarkName = name,
                    description = description,
                    category = "Manual"
                )
                result is SaveResult.Success
            } else {
                Log.w(TAG, "Storage-Manager nicht verfügbar")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Erstellen des Landmarks: ${e.message}")
            false
        }
    }
    
    /**
     * Lädt Feature-Navigation Route für ein bestimmtes Gebäude und Stockwerk
     */
    fun loadFeatureNavigationRoute(context: Context, building: String, floor: Int) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Lade Feature-Navigation Route für Gebäude: $building, Stockwerk: $floor")
                
                // Erstelle eine Demo-Route mit Feature-Navigation
                val demoSteps = listOf(
                    FeatureNavigationStep(
                        stepNumber = 1,
                        instruction = "Gehen Sie geradeaus zum Haupteingang",
                        building = building,
                        targetLandmark = null
                    ),
                    FeatureNavigationStep(
                        stepNumber = 2,
                        instruction = "Biegen Sie links ab zur Treppe",
                        building = building,
                        targetLandmark = null
                    ),
                    FeatureNavigationStep(
                        stepNumber = 3,
                        instruction = "Gehen Sie die Treppe hoch zu Stockwerk $floor",
                        building = building,
                        targetLandmark = null
                    ),
                    FeatureNavigationStep(
                        stepNumber = 4,
                        instruction = "Folgen Sie dem Korridor bis zum Ziel",
                        building = building,
                        targetLandmark = null
                    )
                )
                
                val featureRoute = FeatureNavigationRoute(
                    totalLength = 150.0, // 150 Meter
                    steps = demoSteps
                )
                
                _featureNavigationRoute.value = featureRoute
                Log.i(TAG, "Feature-Navigation Route geladen: ${demoSteps.size} Schritte, ${featureRoute.totalLength}m")
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden der Feature-Navigation Route: ${e.message}")
            }
        }
    }
    
    /**
     * Verarbeitet einen Kamera-Frame für Feature-Matching
     */
    fun processFrameForFeatureMatching(frame: org.opencv.core.Mat) {
        viewModelScope.launch {
            try {
                // Simuliere Feature-Matching mit Demo-Daten
                delay(100) // Simuliere Verarbeitungszeit
                
                // Erstelle Demo-Matches basierend auf verfügbaren Landmarks
                val demoMatches = mutableListOf<FeatureMatchResult>()
                
                if (storageManager != null) {
                    val availableLandmarks = storageManager!!.getAvailableProjectLandmarks()
                    
                    // Simuliere zufällige Matches mit unterschiedlichen Confidence-Werten
                    availableLandmarks.take(3).forEachIndexed { index, landmarkInfo ->
                        val confidence = when (index) {
                            0 -> 0.85f + (Math.random() * 0.1f).toFloat() // Bestes Match
                            1 -> 0.65f + (Math.random() * 0.15f).toFloat() // Mittleres Match
                            else -> 0.45f + (Math.random() * 0.15f).toFloat() // Schwaches Match
                        }
                        
                        if (confidence > 0.5f) { // Nur Matches über 50% Confidence
                            val landmark = FeatureLandmark(
                                id = landmarkInfo.id,
                                name = landmarkInfo.filename.substringBeforeLast('.'), // Use filename without extension as name
                                description = "Landmark from ${landmarkInfo.filename}",
                                position = Position(0.0, 0.0, 0.0),
                                imageUrl = "",
                                confidence = confidence
                            )
                            
                            demoMatches.add(
                                FeatureMatchResult(
                                    landmark = landmark,
                                    matchCount = (confidence * 100).toInt(),
                                    confidence = confidence,
                                    distance = 5.0f + (Math.random() * 10.0f).toFloat()
                                )
                            )
                        }
                    }
                }
                
                // Sortiere nach Confidence (beste zuerst)
                demoMatches.sortByDescending { it.confidence }
                
                _currentMatches.value = demoMatches
                
                if (demoMatches.isNotEmpty()) {
                    Log.d(TAG, "Feature-Matching: ${demoMatches.size} Matches gefunden, beste Confidence: ${demoMatches.first().confidence}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Feature-Matching: ${e.message}")
                _currentMatches.value = emptyList()
            }
        }
    }
    

    
    /**
     * Startet Frame-Processing für Feature-Matching
     */
    fun startFrameProcessing() {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Starte Frame-Processing für Feature-Matching...")
                _isFeatureMappingEnabled.value = true
                Log.i(TAG, "Frame-Processing gestartet")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Starten des Frame-Processing: ${e.message}")
            }
        }
    }
    
    /**
     * Gibt den aktuellen Startpunkt zurück
     */
    fun getCurrentStartPoint(): String {
        return _currentRoute.value?.route?.path?.firstOrNull()?.xmlName ?: "Unbekannter Start"
    }
    
    /**
     * Gibt den aktuellen Endpunkt zurück
     */
    fun getCurrentEndPoint(): String {
        return _currentRoute.value?.route?.path?.lastOrNull()?.xmlName ?: "Unbekanntes Ziel"
    }
    
    /**
     * Gibt verfügbare Landmarks zurück
     */
    fun getAvailableLandmarks(): List<FeatureLandmark> {
        return try {
            if (storageManager != null) {
                val landmarkInfos = runBlocking { storageManager!!.getAvailableProjectLandmarks() }
                landmarkInfos.map { landmarkInfo ->
                    FeatureLandmark(
                        id = landmarkInfo.id,
                        name = landmarkInfo.filename.substringBeforeLast('.'), // Use filename without extension as name
                        description = "Landmark from ${landmarkInfo.filename}",
                        position = Position(0.0, 0.0, 0.0),
                        imageUrl = "",
                        confidence = 1.0f
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der verfügbaren Landmarks: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Gibt die aktuellen Navigationsschritte zurück
     */
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

    /**
     * Gibt den aktuellen Navigationsschritt zurück
     */
    fun getCurrentStep(): NavigationStep? {
        return try {
            val steps = getCurrentNavigationSteps()
            val currentStepNumber = _currentNavigationStep.value
            
            if (steps.isNotEmpty() && currentStepNumber > 0 && currentStepNumber <= steps.size) {
                steps[currentStepNumber - 1] // Convert to 0-based index
            } else {
                Log.w(TAG, "Aktueller Schritt nicht verfügbar: $currentStepNumber von ${steps.size}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des aktuellen Schritts: ${e.message}")
            null
        }
    }

    /**
     * Cleanup beim Destroy
     */
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                // Storage-Manager bereinigt sich selbst automatisch
                storageManager?.logPerformanceSummary()
                Log.i(TAG, "RouteViewModel bereinigt")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Bereinigen: ${e.message}")
            }
        }
    }
}