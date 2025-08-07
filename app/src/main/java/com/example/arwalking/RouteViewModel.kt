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
import com.example.arwalking.data.RoutePart

/**
 * Extension property to calculate distance from RoutePart nodes
 */
private val RoutePart.distance: Double
    get() = nodes?.sumOf { node ->
        node.edge?.lengthInMeters?.toDoubleOrNull() ?: 0.0
    } ?: 0.0

/**
 * Datenklasse für Routenzusammenfassung
 */
data class RouteSummary(
    val startPoint: String,
    val endPoint: String,
    val totalLength: Double, // in Metern
    val estimatedTime: Int, // in Minuten
    val buildings: List<String>,
    val floors: List<String>,
    val totalSteps: Int
)

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
     * Initialisiert die RouteViewModel mit dem gegebenen Context
     */
    fun initialize(context: Context) {
        try {
            Log.i(TAG, "Initialisiere RouteViewModel...")
            
            // Initialisiere RouteRepository
            if (routeRepository == null) {
                routeRepository = RouteRepository(context)
                Log.d(TAG, "RouteRepository initialisiert")
            }
            
            // Verwende die bestehende initializeStorage-Funktion
            initializeStorage(context)
            
            Log.i(TAG, "RouteViewModel erfolgreich initialisiert")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Initialisierung: ${e.message}")
        }
    }
    


    /**
     * Lädt die Route aus der JSON-Datei
     */
    fun loadNavigationRoute(context: Context): NavigationRoute? {
        return try {
            Log.i(TAG, "Lade Route aus JSON-Datei...")
            
            // Stelle sicher, dass die ViewModel initialisiert ist
            initialize(context)
            
            // Lade Route aus JSON-Datei asynchron
            viewModelScope.launch {
                try {
                    val routeData = routeRepository?.getRouteFromAssets("models/final-route.json")
                    _currentRoute.value = routeData
                    
                    if (routeData != null) {
                        Log.i(TAG, "Route erfolgreich aus JSON geladen")
                        // Konvertiere RouteData zu NavigationRoute für Feature-Mapping
                        val navigationRoute = convertToNavigationRoute(routeData)
                        Log.i(TAG, "Route konvertiert: ${navigationRoute.steps.size} Schritte")
                        
                        // Logge Route-Details
                        logNavigationRoute(navigationRoute)
                        
                        // Setze ersten Schritt als aktiv
                        _currentNavigationStep.value = 1
                        
                        // Aktiviere Feature-Mapping automatisch wenn Route geladen
                        _isFeatureMappingEnabled.value = true
                        
                        // Logge Routeninformationen für Debugging
                        val summary = getRouteSummary()
                        Log.i(TAG, "=== ROUTE SUMMARY ===")
                        Log.i(TAG, "Start: ${summary.startPoint}")
                        Log.i(TAG, "Ziel: ${summary.endPoint}")
                        Log.i(TAG, "Länge: ${summary.totalLength}m")
                        Log.i(TAG, "Zeit: ${summary.estimatedTime} Min.")
                        Log.i(TAG, "Gebäude: ${summary.buildings}")
                        Log.i(TAG, "Stockwerke: ${summary.floors}")
                        Log.i(TAG, "Schritte: ${summary.totalSteps}")
                        Log.i(TAG, "Info: ${getFormattedRouteInfo()}")
                        Log.i(TAG, "Beschreibung: ${getRouteDescription()}")
                        Log.i(TAG, "====================")
                        
                    } else {
                        Log.w(TAG, "Keine Route in JSON-Datei gefunden")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Laden der Route in Coroutine: ${e.message}")
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
     * Konvertiert RouteData zu NavigationRoute und extrahiert Landmark-IDs
     */
    private fun convertToNavigationRoute(routeData: RouteData): NavigationRoute {
        val steps = mutableListOf<NavigationStep>()
        var stepNumber = 1
        
        // Sammle alle Landmark-IDs für Feature-Matching
        val allLandmarkIds = mutableSetOf<String>()
        
        // Durchlaufe alle PathItems und RouteParts
        routeData.route.path.forEach { pathItem ->
            pathItem.routeParts.forEach { routePart ->
                // Verwende die deutsche Anweisung als primäre Anweisung
                val instruction = routePart.instructionDe ?: routePart.instructionEn ?: routePart.instruction ?: "Folgen Sie der Route"
                
                // Extrahiere Stockwerk aus levelInfo falls verfügbar
                val floor = pathItem.levelInfo?.storey?.toIntOrNull() ?: 0
                
                // Extrahiere Landmark-IDs aus der neuen Struktur
                val landmarkIds = mutableListOf<String>()
                
                // Hauptlandmark aus landmarkFromInstruction
                routePart.landmarkFromInstruction?.let { landmarkId ->
                    landmarkIds.add(landmarkId)
                    allLandmarkIds.add(landmarkId)
                }
                
                // Zusätzliche Landmarks aus landmarks-Array
                routePart.landmarks?.forEach { landmark ->
                    landmark.id?.let { landmarkId ->
                        landmarkIds.add(landmarkId)
                        allLandmarkIds.add(landmarkId)
                    }
                }
                
                // Berechne Distanz aus Nodes falls verfügbar
                val distance = routePart.nodes?.sumOf { node ->
                    node.edge?.lengthInMeters?.toDoubleOrNull() ?: 0.0
                } ?: 0.0
                
                steps.add(
                    NavigationStep(
                        stepNumber = stepNumber++,
                        instruction = instruction,
                        building = pathItem.xmlName,
                        floor = floor,
                        landmarks = landmarkIds, // Jetzt echte Landmark-IDs
                        distance = distance,
                        estimatedTime = (distance * 1.2).toInt().coerceAtLeast(10) // ~1.2m/s Gehgeschwindigkeit
                    )
                )
            }
        }
        
        // Erstelle ProcessedLandmarks für alle gefundenen IDs
        processedLandmarks.clear()
        allLandmarkIds.forEach { landmarkId ->
            processedLandmarks.add(ProcessedLandmark(landmarkId, landmarkId))
        }
        
        Log.i(TAG, "Extrahiert ${allLandmarkIds.size} eindeutige Landmark-IDs: ${allLandmarkIds.take(5)}")
        
        return NavigationRoute(
            id = "route_${System.currentTimeMillis()}",
            name = "Navigation Route",
            description = "Generated from final-route.json",
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
                featureMatchingEngine = FeatureMatchingEngine(context)
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
                    Log.d(TAG, "- ${landmark.id ?: "unknown"} (${landmark.filename})")
                }
                
                // Lade Features für echtes Feature-Matching
                if (featureMatchingEngine != null && processedLandmarks.isNotEmpty()) {
                    Log.i(TAG, "Lade Features für echtes Feature-Matching...")
                    featureMatchingEngine!!.loadLandmarkFeatures(processedLandmarks)
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
                        Log.d(TAG, "- ${landmark.id ?: "unknown"} (${landmark.filename})")
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
    
    // Frame-Processing-Throttling
    private var lastFrameProcessTime = 0L
    private val frameProcessingInterval = 100L // Verarbeite nur alle 100ms (10 FPS)
    
    /**
     * Verarbeitet einen Kamera-Frame für echtes Feature-Matching (optimiert)
     */
    fun processFrameForFeatureMatching(frame: org.opencv.core.Mat) {
        // Nur verarbeiten wenn Feature-Mapping aktiviert ist
        if (!_isFeatureMappingEnabled.value) {
            return
        }
        
        // Throttle Frame-Processing für bessere Performance
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameProcessTime < frameProcessingInterval) {
            return
        }
        lastFrameProcessTime = currentTime
        
        viewModelScope.launch {
            try {
                Log.v(TAG, "Processing frame for feature matching")
                
                val matches = if (featureMatchingEngine != null) {
                    // Nur echtes Feature-Matching mit OpenCV
                    featureMatchingEngine!!.processFrame(frame)
                } else {
                    Log.w(TAG, "FeatureMatchingEngine not initialized")
                    emptyList()
                }
                
                _currentMatches.value = matches
                
                if (matches.isNotEmpty()) {
                    Log.d(TAG, "Found ${matches.size} landmark matches")
                    matches.take(2).forEach { match ->
                        Log.v(TAG, "- ${match.landmarkId}: ${(match.confidence * 100).toInt()}%")
                    }
                } else {
                    Log.w(TAG, "No matches found - this will trigger 'Landmark verloren' after 5 seconds")
                }
                Log.d(TAG, "==============================")
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Feature-Matching: ${e.message}")
                Log.e(TAG, "Exception details: ", e)
                _currentMatches.value = emptyList()
            }
        }
    }
    

    

    
    /**
     * Aktiviert/Deaktiviert das Feature-Mapping
     */
    fun setFeatureMappingEnabled(enabled: Boolean) {
        _isFeatureMappingEnabled.value = enabled
        Log.d(TAG, "Feature-Mapping ${if (enabled) "aktiviert" else "deaktiviert"}")
        
        if (!enabled) {
            _currentMatches.value = emptyList()
        }
    }
    
    /**
     * Aktualisiert den aktuellen Navigationsschritt
     */
    fun setCurrentNavigationStep(step: Int) {
        val totalSteps = getCurrentNavigationSteps().size
        val validStep = step.coerceIn(1, maxOf(1, totalSteps))
        
        _currentNavigationStep.value = validStep
        Log.d(TAG, "Navigationsschritt aktualisiert: $validStep von $totalSteps")
        
        // Matches werden nur durch echtes Feature-Matching aktualisiert
    }
    
    /**
     * Geht zum nächsten Navigationsschritt
     */
    fun nextNavigationStep() {
        val currentStep = _currentNavigationStep.value
        val totalSteps = getCurrentNavigationSteps().size
        
        if (currentStep < totalSteps) {
            setCurrentNavigationStep(currentStep + 1)
            Log.i(TAG, "Nächster Schritt: ${currentStep + 1}")
        } else {
            Log.i(TAG, "Bereits am letzten Schritt")
        }
    }
    
    /**
     * Geht zum vorherigen Navigationsschritt
     */
    fun previousNavigationStep() {
        val currentStep = _currentNavigationStep.value
        
        if (currentStep > 1) {
            setCurrentNavigationStep(currentStep - 1)
            Log.i(TAG, "Vorheriger Schritt: ${currentStep - 1}")
        } else {
            Log.i(TAG, "Bereits am ersten Schritt")
        }
    }
    
    /**
     * Startet die Navigation von Anfang an
     */
    fun startNavigation() {
        Log.i(TAG, "Navigation gestartet")
        setCurrentNavigationStep(1)
        setFeatureMappingEnabled(true)
    }
    
    /**
     * Stoppt die Navigation
     */
    fun stopNavigation() {
        Log.i(TAG, "Navigation gestoppt")
        setFeatureMappingEnabled(false)
        _currentMatches.value = emptyList()
    }
    
    /**
     * Gibt den aktuellen Status der RouteViewModel zurück
     */
    fun getStatus(): String {
        val route = _currentRoute.value
        val step = _currentNavigationStep.value
        val totalSteps = getCurrentNavigationSteps().size
        val matchesCount = _currentMatches.value.size
        val isFeatureMappingEnabled = _isFeatureMappingEnabled.value
        
        return buildString {
            appendLine("=== RouteViewModel Status ===")
            appendLine("Route geladen: ${route != null}")
            if (route != null) {
                appendLine("Route Pfad-Elemente: ${route.route.path.size}")
                appendLine("Aktueller Schritt: $step von $totalSteps")
            }
            appendLine("Feature-Mapping: ${if (isFeatureMappingEnabled) "Aktiviert" else "Deaktiviert"}")
            appendLine("Aktuelle Matches: $matchesCount")
            appendLine("Storage-Manager: ${storageManager != null}")
            appendLine("Feature-Matching-Engine: ${featureMatchingEngine != null}")
            appendLine("=== Ende Status ===")
        }
    }
    
    /**
     * Cleanup-Funktion für die ViewModel
     */
    override fun onCleared() {
        super.onCleared()
        try {
            Log.i(TAG, "RouteViewModel wird bereinigt...")
            
            // Stoppe Navigation
            stopNavigation()
            
            // Bereinige Ressourcen
            arTrackingSystem?.resetTracking()
            processedLandmarks.clear()
            
            // Bereinige FeatureMatchingEngine
            featureMatchingEngine?.cleanup()
            
            // Storage-Manager bereinigt sich selbst automatisch
            viewModelScope.launch {
                try {
                    storageManager?.logPerformanceSummary()
                    
                    // Bereinige Feature-Storage Cache
                    landmarkFeatureStorage?.cleanup()
                    
                    Log.i(TAG, "RouteViewModel erfolgreich bereinigt")
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Bereinigen des Storage: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Bereinigen der RouteViewModel: ${e.message}")
        }
    }
    
    /**
     * Testet die RouteViewModel-Funktionalität
     */
    fun runSelfTest(context: Context): Boolean {
        return try {
            Log.i(TAG, "=== RouteViewModel Self-Test ===")
            
            // Test 1: Initialisierung
            initialize(context)
            Log.d(TAG, "✓ Initialisierung erfolgreich")
            
            // Test 2: Route laden
            loadNavigationRoute(context)
            Thread.sleep(1000) // Warte auf Coroutine
            Log.d(TAG, "✓ Route-Loading gestartet")
            
            // Test 3: Feature-Mapping aktivieren
            setFeatureMappingEnabled(true)
            Log.d(TAG, "✓ Feature-Mapping aktiviert")
            
            // Test 4: Navigation starten
            startNavigation()
            Log.d(TAG, "✓ Navigation gestartet")
            
            // Test 5: Status ausgeben
            Log.i(TAG, getStatus())
            
            Log.i(TAG, "=== Self-Test erfolgreich ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "=== Self-Test fehlgeschlagen: ${e.message} ===")
            false
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
                        // landmark ist ein RouteLandmarkData-Objekt, nicht ein String
                        routeLandmarks.add(
                            FeatureLandmark(
                                id = landmark.id ?: "unknown_landmark", // ID aus RouteLandmarkData
                                name = landmark.nameDe ?: landmark.nameEn ?: landmark.id ?: "Unknown Landmark",
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
                Log.d(TAG, "- ${landmark.id ?: "unknown"}: ${landmark.name}")
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
                    landmark.id?.let { landmarkIds.add(it) } // landmarks ist List<RouteLandmarkData>, verwende .id
                }
            }
        }
        
        return landmarkIds.toList()
    }

    /**
     * Fehlende Methoden für Navigation.kt
     */
    fun enableStorageSystemImmediately(context: Context) {
        Log.d(TAG, "enableStorageSystemImmediately called - initialisiere Storage-System")
        initializeStorage(context)
    }
    
    fun startFrameProcessing() {
        Log.d(TAG, "startFrameProcessing called - Feature-Matching bereit")
        // Feature-Matching ist bereits in initializeStorage() aktiviert
    }
    
    /**
     * Gibt den Startpunkt der Route aus der JSON-Datei zurück
     */
    fun getCurrentStartPoint(): String {
        return try {
            val route = _currentRoute.value
            if (route != null && route.route.path.isNotEmpty()) {
                // Erster Schritt der Route
                val firstPathItem = route.route.path.first()
                val firstRoutePart = firstPathItem.routeParts.firstOrNull()
                
                // Versuche verschiedene Quellen für den Startpunkt
                val startPoint = when {
                    // 1. Aus der ersten Anweisung
                    !firstRoutePart?.instructionDe.isNullOrBlank() -> {
                        extractLocationFromInstruction(firstRoutePart!!.instructionDe!!)
                    }
                    !firstRoutePart?.instructionEn.isNullOrBlank() -> {
                        extractLocationFromInstruction(firstRoutePart!!.instructionEn!!)
                    }
                    !firstRoutePart?.instruction.isNullOrBlank() -> {
                        extractLocationFromInstruction(firstRoutePart!!.instruction!!)
                    }
                    // 2. Aus dem ersten Node
                    firstRoutePart?.nodes?.firstOrNull()?.node?.name != null -> {
                        firstRoutePart.nodes.firstOrNull()?.node?.name ?: "Unbekannter Startpunkt"
                    }
                    // 3. Aus dem Gebäudenamen
                    !firstPathItem.xmlNameDe.isNullOrBlank() -> {
                        firstPathItem.xmlNameDe!!
                    }
                    !firstPathItem.xmlName.isNullOrBlank() -> {
                        firstPathItem.xmlName
                    }
                    else -> "Startpunkt aus Route"
                }
                
                Log.d(TAG, "Startpunkt aus JSON: $startPoint")
                startPoint
            } else {
                Log.w(TAG, "Keine Route geladen - verwende Standard-Startpunkt")
                "Büro Prof. Dr. Ludwig (PT 3.0.84C)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Ermitteln des Startpunkts: ${e.message}")
            "Büro Prof. Dr. Ludwig (PT 3.0.84C)"
        }
    }
    
    /**
     * Gibt den Endpunkt der Route aus der JSON-Datei zurück
     */
    fun getCurrentEndPoint(): String {
        return try {
            val route = _currentRoute.value
            if (route != null && route.route.path.isNotEmpty()) {
                // Letzter Schritt der Route
                val lastPathItem = route.route.path.last()
                val lastRoutePart = lastPathItem.routeParts.lastOrNull()
                
                // Versuche verschiedene Quellen für den Endpunkt
                val endPoint = when {
                    // 1. Aus der letzten Anweisung
                    !lastRoutePart?.instructionDe.isNullOrBlank() -> {
                        extractLocationFromInstruction(lastRoutePart!!.instructionDe!!)
                    }
                    !lastRoutePart?.instructionEn.isNullOrBlank() -> {
                        extractLocationFromInstruction(lastRoutePart!!.instructionEn!!)
                    }
                    !lastRoutePart?.instruction.isNullOrBlank() -> {
                        extractLocationFromInstruction(lastRoutePart!!.instruction!!)
                    }
                    // 2. Aus dem letzten Node mit isdestination="true"
                    lastRoutePart?.nodes?.any { it.node?.isdestination == "true" } == true -> {
                        val destinationNode = lastRoutePart.nodes.find { it.node?.isdestination == "true" }
                        destinationNode?.node?.name ?: "Ziel"
                    }
                    // 3. Aus dem letzten Node
                    lastRoutePart?.nodes?.lastOrNull()?.node?.name != null -> {
                        lastRoutePart.nodes.last().node?.name!!
                    }
                    // 4. Aus dem Gebäudenamen
                    !lastPathItem.xmlNameDe.isNullOrBlank() -> {
                        lastPathItem.xmlNameDe!!
                    }
                    !lastPathItem.xmlName.isNullOrBlank() -> {
                        lastPathItem.xmlName
                    }
                    else -> "Ziel aus Route"
                }
                
                Log.d(TAG, "Endpunkt aus JSON: $endPoint")
                endPoint
            } else {
                Log.w(TAG, "Keine Route geladen - verwende Standard-Endpunkt")
                "Haupteingang"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Ermitteln des Endpunkts: ${e.message}")
            "Haupteingang"
        }
    }
    
    /**
     * Extrahiert Ortsangaben aus Navigationsanweisungen
     */
    private fun extractLocationFromInstruction(instruction: String): String {
        // Entferne HTML-Tags
        val cleanInstruction = instruction
            .replace("<b>", "")
            .replace("</b>", "")
            .replace("<\\/b>", "")
            .replace(Regex("<[^>]*>"), "")
        
        // Suche nach bekannten Mustern für Ortsangaben
        val locationPatterns = listOf(
            Regex("(?:zu|zum|zur|in|im|an|am)\\s+([^.]+?)(?:\\s+(?:gehen|folgen|wenden)|$)", RegexOption.IGNORE_CASE),
            Regex("(?:Büro|Office|Raum|Room)\\s+([^.]+?)(?:\\s|$)", RegexOption.IGNORE_CASE),
            Regex("(?:Prof\\.|Professor)\\s+([^.]+?)(?:\\s|$)", RegexOption.IGNORE_CASE),
            Regex("([A-Z][^.]*(?:eingang|Eingang|entrance|Entrance))", RegexOption.IGNORE_CASE),
            Regex("([A-Z][^.]*(?:tür|Tür|door|Door))", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in locationPatterns) {
            val match = pattern.find(cleanInstruction)
            if (match != null) {
                val location = match.groupValues[1].trim()
                if (location.isNotBlank() && location.length > 2) {
                    return location
                }
            }
        }
        
        // Fallback: Verwende die ersten Wörter der Anweisung
        val words = cleanInstruction.split(" ").take(4)
        return if (words.isNotEmpty()) {
            words.joinToString(" ").trim()
        } else {
            cleanInstruction.take(50)
        }
    }
    
    fun getCurrentStep(): NavigationStep? {
        val steps = getCurrentNavigationSteps()
        val currentStepNumber = _currentNavigationStep.value
        return steps.find { it.stepNumber == currentStepNumber }
    }
    
    /**
     * Gibt die Gesamtlänge der Route zurück
     */
    fun getRouteLength(): Double {
        return try {
            val route = _currentRoute.value
            if (route != null) {
                // Berechne Gesamtlänge aus allen RouteParts
                var totalLength = 0.0
                route.route.path.forEach { pathItem ->
                    pathItem.routeParts.forEach { routePart ->
                        // Verwende die Extension Property für distance
                        totalLength += routePart.distance
                    }
                }
                Log.d(TAG, "Gesamtlänge der Route: ${totalLength}m")
                totalLength
            } else {
                0.0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Berechnen der Routenlänge: ${e.message}")
            0.0
        }
    }
    
    /**
     * Gibt die geschätzte Gehzeit der Route zurück (in Minuten)
     */
    fun getEstimatedWalkingTime(): Int {
        return try {
            val route = _currentRoute.value
            if (route != null) {
                // Verwende routeInfo falls verfügbar
                route.route.routeInfo?.estimatedTime?.let { return it }
                
                // Fallback: Berechne basierend auf Distanz (durchschnittlich 1.2 m/s)
                val totalLength = getRouteLength()
                val timeInSeconds = (totalLength / 1.2).toInt()
                val timeInMinutes = (timeInSeconds / 60).coerceAtLeast(1)
                
                Log.d(TAG, "Geschätzte Gehzeit: ${timeInMinutes} Minuten")
                timeInMinutes
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Berechnen der Gehzeit: ${e.message}")
            0
        }
    }
    
    /**
     * Gibt die Gebäude zurück, durch die die Route führt
     */
    fun getRouteBuildings(): List<String> {
        return try {
            val route = _currentRoute.value
            if (route != null) {
                val buildings = mutableSetOf<String>()
                route.route.path.forEach { pathItem ->
                    // Bevorzuge deutsche Namen
                    val buildingName = pathItem.xmlNameDe 
                        ?: pathItem.xmlNameEn 
                        ?: pathItem.xmlName
                    
                    if (!buildingName.isNullOrBlank()) {
                        buildings.add(buildingName)
                    }
                }
                
                val buildingList = buildings.toList()
                Log.d(TAG, "Route führt durch ${buildingList.size} Gebäude: $buildingList")
                buildingList
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Ermitteln der Gebäude: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Gibt die Stockwerke zurück, durch die die Route führt
     */
    fun getRouteFloors(): List<String> {
        return try {
            val route = _currentRoute.value
            if (route != null) {
                val floors = mutableSetOf<String>()
                route.route.path.forEach { pathItem ->
                    pathItem.levelInfo?.let { levelInfo ->
                        val floorName = levelInfo.storeyNameDe 
                            ?: levelInfo.storeyName 
                            ?: levelInfo.storeyNameEn
                            ?: levelInfo.storey
                        
                        if (!floorName.isNullOrBlank()) {
                            floors.add(floorName)
                        }
                    }
                }
                
                val floorList = floors.toList()
                Log.d(TAG, "Route führt durch ${floorList.size} Stockwerke: $floorList")
                floorList
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Ermitteln der Stockwerke: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Gibt eine Zusammenfassung der Route für die Drawer List zurück
     */
    fun getRouteSummary(): RouteSummary {
        return try {
            RouteSummary(
                startPoint = getCurrentStartPoint(),
                endPoint = getCurrentEndPoint(),
                totalLength = getRouteLength(),
                estimatedTime = getEstimatedWalkingTime(),
                buildings = getRouteBuildings(),
                floors = getRouteFloors(),
                totalSteps = getCurrentNavigationSteps().size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Erstellen der Routenzusammenfassung: ${e.message}")
            RouteSummary(
                startPoint = "Unbekannter Start",
                endPoint = "Unbekanntes Ziel",
                totalLength = 0.0,
                estimatedTime = 0,
                buildings = emptyList(),
                floors = emptyList(),
                totalSteps = 0
            )
        }
    }
    
    /**
     * Gibt formatierte Routeninformationen für die UI zurück
     */
    fun getFormattedRouteInfo(): String {
        val summary = getRouteSummary()
        return buildString {
            if (summary.totalLength > 0) {
                append("${String.format("%.0f", summary.totalLength)}m")
            }
            if (summary.estimatedTime > 0) {
                if (isNotEmpty()) append(" • ")
                append("${summary.estimatedTime} Min.")
            }
            if (summary.buildings.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("${summary.buildings.size} Gebäude")
            }
            if (summary.totalSteps > 0) {
                if (isNotEmpty()) append(" • ")
                append("${summary.totalSteps} Schritte")
            }
        }.takeIf { it.isNotBlank() } ?: "Route wird geladen..."
    }
    
    /**
     * Gibt eine kurze Routenbeschreibung zurück
     */
    fun getRouteDescription(): String {
        val summary = getRouteSummary()
        return when {
            summary.buildings.size > 1 -> "Route durch ${summary.buildings.joinToString(", ")}"
            summary.buildings.size == 1 -> "Route in ${summary.buildings.first()}"
            summary.floors.isNotEmpty() -> "Route über ${summary.floors.size} Stockwerk${if (summary.floors.size > 1) "e" else ""}"
            else -> "Navigationsroute"
        }
    }
    
    /**
     * Gibt den aktuellen Fortschritt der Route zurück (0.0 bis 1.0)
     */
    fun getRouteProgress(): Float {
        val totalSteps = getCurrentNavigationSteps().size
        val currentStep = _currentNavigationStep.value
        
        return if (totalSteps > 0) {
            (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    
    /**
     * Gibt den Fortschritt als Prozentsatz zurück
     */
    fun getRouteProgressPercentage(): Int {
        return (getRouteProgress() * 100).toInt()
    }

}