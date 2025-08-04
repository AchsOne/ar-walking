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
import com.example.arwalking.storage.CleanupSummary
import com.example.arwalking.data.RouteRepository
import com.example.arwalking.data.RouteData

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
    
    // Feature Matching System
    private var featureMatchingEngine: FeatureMatchingEngine? = null
    private var landmarkFeatureStorage: LandmarkFeatureStorage? = null
    private var arTrackingSystem: ARTrackingSystem? = null
    
    // Verarbeitete Landmarks für schnelles Matching
    private var processedLandmarks = mutableListOf<ProcessedLandmark>()
    

    
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
                // Verwende die deutsche Anweisung als primäre Anweisung
                val instruction = routePart.instructionDe ?: routePart.instruction ?: "Folgen Sie der Route"
                
                // Extrahiere Stockwerk aus levelInfo falls verfügbar
                val floor = pathItem.levelInfo?.storey?.toIntOrNull() ?: 0
                
                steps.add(
                    NavigationStep(
                        stepNumber = stepNumber++,
                        instruction = instruction,
                        building = pathItem.xmlName,
                        floor = floor,
                        landmarks = routePart.landmarks ?: emptyList(),
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
     * Initialisiert das neue Storage-System und Feature-Matching (ersetzt Feature-Mapping)
     */
    fun initializeStorage(context: Context) {
        viewModelScope.launch {
            try {
                Log.i(TAG, "Initialisiere neues Storage-System und Feature-Matching...")
                
                // Neues Storage-System initialisieren
                storageManager = ArWalkingStorageManager(context)
                
                // Feature-Matching System initialisieren
                featureMatchingEngine = FeatureMatchingEngine()
                landmarkFeatureStorage = LandmarkFeatureStorage(context)
                arTrackingSystem = ARTrackingSystem()
                
                // Importiere Landmarks aus Assets falls noch nicht vorhanden
                val importedCount = landmarkFeatureStorage!!.importLandmarksFromAssets()
                if (importedCount > 0) {
                    Log.i(TAG, "$importedCount Landmarks aus Assets importiert")
                }
                
                // Lade route-spezifische Landmarks falls Route verfügbar ist
                processedLandmarks.clear()
                
                val currentRoute = _currentRoute.value
                if (currentRoute != null) {
                    // Lade nur die Landmarks, die in der aktuellen Route verwendet werden
                    processedLandmarks.addAll(landmarkFeatureStorage!!.loadRouteSpecificLandmarks(currentRoute.route))
                    Log.i(TAG, "${processedLandmarks.size} route-spezifische Landmarks für Feature-Matching geladen")
                } else {
                    // Fallback: Lade alle verfügbaren Landmarks
                    processedLandmarks.addAll(landmarkFeatureStorage!!.loadAllLandmarks())
                    Log.i(TAG, "${processedLandmarks.size} Landmarks für Feature-Matching geladen (alle verfügbaren)")
                }
                
                // Prüfe verfügbare Bilder im Projektverzeichnis
                val availableLandmarks = storageManager!!.getAvailableProjectLandmarks()
                Log.i(TAG, "Verfügbare Landmark-Bilder: ${availableLandmarks.size}")
                
                availableLandmarks.take(5).forEach { landmark ->
                    Log.d(TAG, "- ${landmark.id} (${landmark.filename})")
                }
                
                // Feature-Mapping ist verfügbar wenn Landmarks geladen wurden
                _isFeatureMappingEnabled.value = processedLandmarks.isNotEmpty()
                
                Log.i(TAG, "Feature-Matching System erfolgreich initialisiert")
                
                // Logge Storage-Status
                val status = storageManager!!.getStorageStatus()
                Log.i(TAG, "Storage-Status: ${status.getHealthStatus()}")
                
                val storageStats = landmarkFeatureStorage!!.getStorageStats()
                Log.i(TAG, "Feature-Storage: ${storageStats.landmarkCount} Landmarks, ${"%.1f".format(storageStats.getTotalSizeMB())} MB")
                
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
    suspend fun cleanup(): CleanupSummary? {
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
     * Erstellt einen neuen Landmark (vereinfacht)
     */
    suspend fun createLandmark(
        landmarkId: String,
        name: String,
        description: String,
        bitmap: Bitmap
    ): Boolean {
        return try {
            Log.d(TAG, "createLandmark called (stub): $landmarkId")
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
                
                // Stub - echte Route laden würde hier implementiert werden
                Log.d(TAG, "Route loading stub for $building, floor $floor")
                
                // Fallback: Erstelle eine Demo-Route mit Feature-Navigation
                val demoSteps = listOf(
                    FeatureNavigationStep(
                        stepNumber = 1,
                        instruction = "Gehen Sie geradeaus zum Haupteingang",
                        landmarks = emptyList()
                    ),
                    FeatureNavigationStep(
                        stepNumber = 2,
                        instruction = "Biegen Sie links ab zur Treppe",
                        landmarks = emptyList()
                    ),
                    FeatureNavigationStep(
                        stepNumber = 3,
                        instruction = "Gehen Sie die Treppe hoch zu Stockwerk $floor",
                        landmarks = emptyList()
                    ),
                    FeatureNavigationStep(
                        stepNumber = 4,
                        instruction = "Folgen Sie dem Korridor bis zum Ziel",
                        landmarks = emptyList()
                    )
                )
                
                val featureRoute = FeatureNavigationRoute(
                    id = "demo_route",
                    name = "Demo Route",
                    steps = demoSteps
                )
                
                _featureNavigationRoute.value = featureRoute
                Log.i(TAG, "Feature-Navigation Route geladen: ${demoSteps.size} Schritte")
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden der Feature-Navigation Route: ${e.message}")
            }
        }
    }
    
    /**
     * Verarbeitet einen Kamera-Frame (vereinfacht)
     */
    fun processFrameForFeatureMatching(frame: org.opencv.core.Mat) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "processFrameForFeatureMatching called (stub)")
                // Stub implementation - verhindert Crashes
                _currentMatches.value = emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Feature-Matching: ${e.message}")
                _currentMatches.value = emptyList()
            }
        }
    }
    

    

    
    /**
     * Gibt verfügbare Landmarks zurück - verwendet Landmark-IDs aus der Route
     */
    fun getAvailableLandmarks(): List<FeatureLandmark> {
        return try {
            val routeLandmarks = mutableListOf<FeatureLandmark>()
            
            // Sammle alle Landmark-IDs aus der aktuellen Route
            _currentRoute.value?.route?.path?.forEach { pathItem ->
                pathItem.routeParts.forEach { routePart ->
                    routePart.landmarks?.forEach { landmark ->
                        // landmark ist bereits ein String (Landmark-ID)
                        routeLandmarks.add(
                            FeatureLandmark(
                                id = landmark, // String aus JSON
                                name = landmark,
                                description = "Landmark",
                                position = Position(0.0, 0.0, 0.0),
                                imageUrl = ""
                            )
                        )
                    }
                }
            }
            
            // Zusätzlich: Lade verfügbare Bilder aus dem Storage-System
            if (storageManager != null) {
                val landmarkInfos = runBlocking { storageManager!!.getAvailableProjectLandmarks() }
                landmarkInfos.forEach { landmarkInfo ->
                    // Füge nur hinzu, wenn nicht bereits in der Route vorhanden
                    if (routeLandmarks.none { it.id == landmarkInfo.id }) {
                        routeLandmarks.add(
                            FeatureLandmark(
                                id = landmarkInfo.id, // Exakte ID aus Dateiname
                                name = landmarkInfo.filename.substringBeforeLast('.'),
                                description = "Verfügbares Landmark",
                                position = Position(0.0, 0.0, 0.0),
                                imageUrl = ""
                            )
                        )
                    }
                }
            }
            
            Log.d(TAG, "Verfügbare Landmarks: ${routeLandmarks.size}")
            routeLandmarks.forEach { landmark ->
                Log.d(TAG, "- ${landmark.id}: ${landmark.name}")
            }
            
            routeLandmarks
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
     * Lädt Landmarks neu für eine spezifische Route (vereinfacht)
     */
    private suspend fun reloadLandmarksForRoute(route: com.example.arwalking.data.Route) {
        try {
            Log.d(TAG, "reloadLandmarksForRoute called (stub)")
            // Stub implementation - verhindert Crashes
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Neuladen der Route-Landmarks: ${e.message}")
        }
    }
    
    /**
     * Aktualisiert Landmarks wenn sich die aktuelle Route ändert
     */
    fun updateLandmarksForCurrentRoute() {
        viewModelScope.launch {
            val currentRoute = _currentRoute.value
            if (currentRoute != null) {
                reloadLandmarksForRoute(currentRoute.route)
            }
        }
    }
    
    /**
     * Gibt die aktuell geladenen Landmark-IDs zurück (für Debugging)
     */
    fun getCurrentlyLoadedLandmarkIds(): List<String> {
        Log.d(TAG, "getCurrentlyLoadedLandmarkIds called (stub)")
        return emptyList()
    }
    
    /**
     * Gibt die Landmark-IDs zurück, die in der aktuellen Route benötigt werden
     */
    fun getRequiredLandmarkIds(): List<String> {
        val currentRoute = _currentRoute.value ?: return emptyList()
        
        val landmarkIds = mutableSetOf<String>()
        for (pathItem in currentRoute.route.path) {
            for (routePart in pathItem.routeParts) {
                routePart.landmarks?.forEach { landmark ->
                    landmarkIds.add(landmark) // landmarks ist bereits List<String>
                }
            }
        }
        
        return landmarkIds.toList()
    }

    /**
     * Fehlende Methoden für Navigation.kt
     */
    fun enableStorageSystemImmediately(context: Context) {
        Log.d(TAG, "enableStorageSystemImmediately called (stub)")
        // Stub implementation - verhindert Crashes
    }
    
    fun startFrameProcessing() {
        Log.d(TAG, "startFrameProcessing called (stub)")
        // Stub implementation - verhindert Crashes
    }
    
    fun getCurrentStartPoint(): String {
        Log.d(TAG, "getCurrentStartPoint called (stub)")
        return "Büro Prof. Dr. Ludwig (PT 3.0.84C)"
    }
    
    fun getCurrentEndPoint(): String {
        Log.d(TAG, "getCurrentEndPoint called (stub)")
        return "Haupteingang"
    }
    
    fun setCurrentNavigationStep(step: Int) {
        Log.d(TAG, "setCurrentNavigationStep called (stub): $step")
        _currentNavigationStep.value = step
    }
    
    fun getCurrentStep(): NavigationStep? {
        Log.d(TAG, "getCurrentStep called (stub)")
        val steps = getCurrentNavigationSteps()
        val currentStepNumber = _currentNavigationStep.value
        return steps.find { it.stepNumber == currentStepNumber }
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
                
                // Bereinige Feature-Storage Cache
                landmarkFeatureStorage?.cleanup()
                
                Log.i(TAG, "RouteViewModel bereinigt")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Bereinigen: ${e.message}")
            }
        }
    }
}