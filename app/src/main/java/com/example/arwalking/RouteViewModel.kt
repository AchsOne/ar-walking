package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.AKAZE
import org.opencv.features2d.DescriptorMatcher
import org.opencv.imgproc.Imgproc
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * ViewModel für Route-Management und Feature-Mapping
 */
class RouteViewModel : ViewModel() {

    // ========== ROUTE MANAGEMENT STATE ==========

    private val _currentRoute = MutableStateFlow<NavigationRoute?>(null)
    val currentRoute: StateFlow<NavigationRoute?> = _currentRoute.asStateFlow()

    private val _currentNavigationStep = MutableStateFlow(0)
    val currentNavigationStep: StateFlow<Int> = _currentNavigationStep.asStateFlow()

    private var isStorageInitialized = false

    // ========== FEATURE MAPPING STATE ==========

    private val _currentMatches = MutableStateFlow<List<LandmarkMatch>>(emptyList())
    val currentMatches: StateFlow<List<LandmarkMatch>> = _currentMatches.asStateFlow()

    private val _isFeatureMappingEnabled = MutableStateFlow(false)
    val isFeatureMappingEnabled: StateFlow<Boolean> = _isFeatureMappingEnabled.asStateFlow()

    private val _isProcessingFrame = MutableStateFlow(false)
    val isProcessingFrame: StateFlow<Boolean> = _isProcessingFrame.asStateFlow()
    
    // ========== INTELLIGENTE NAVIGATION STATE ==========
    
    private val _completedSteps = MutableStateFlow<Set<Int>>(emptySet())
    val completedSteps: StateFlow<Set<Int>> = _completedSteps.asStateFlow()
    
    private val _deletedSteps = MutableStateFlow<Set<Int>>(emptySet())
    val deletedSteps: StateFlow<Set<Int>> = _deletedSteps.asStateFlow()
    
    private val _navigationStatus = MutableStateFlow<NavigationStatus>(NavigationStatus.WAITING)
    val navigationStatus: StateFlow<NavigationStatus> = _navigationStatus.asStateFlow()
    
    // Landmark-basierte Navigation
    private var landmarkDetectionHistory = mutableMapOf<String, DetectionHistory>()
    private val confidenceThreshold = 0.66f
    private val stabilityDuration = 3000L // 3 Sekunden
    private val lowConfidenceThreshold = 0.40f
    private val lostTrackingDuration = 10000L // 10 Sekunden

    // Auto-Advance Konfiguration (anpassbar)
    private var autoAdvanceThreshold = 0.50f // 50% Standard

    // Feature Detector - AKAZE für bessere Indoor-Performance
    private var akazeDetector: AKAZE? = null
    private var matcher: DescriptorMatcher? = null

    // Landmark Features Cache
    private val landmarkFeaturesCache = mutableMapOf<String, LandmarkFeatures>()

    // Frame Processing Configuration
    private var lastProcessedTime = 0L
    private val frameProcessingInterval = 500L // Process every 500ms
    private var frameCounter = 0

    // ========== DATA CLASSES ==========

    data class LandmarkFeatures(
        val id: String,
        val keypoints: MatOfKeyPoint,
        val descriptors: Mat
    )

    data class LandmarkMatch(
        val landmark: RouteLandmarkData,
        val matchCount: Int,
        val confidence: Float,
        val distance: Float
    )
    
    data class DetectionHistory(
        val landmarkId: String,
        val stepNumber: Int,
        var firstDetectionTime: Long = 0L,
        var lastDetectionTime: Long = 0L,
        var highestConfidence: Float = 0f,
        var isStable: Boolean = false
    )
    
    enum class NavigationStatus {
        WAITING,           // Warte auf erste Landmark-Erkennung
        TRACKING,          // Aktive Landmark-Verfolgung
        STEP_COMPLETED,    // Schritt erfolgreich abgeschlossen
        LOST_TRACKING,     // Tracking verloren
        ROUTE_COMPLETED    // Gesamte Route abgeschlossen
    }
    
    data class StepTransition(
        val fromStep: Int,
        val toStep: Int,
        val trigger: String,
        val confidence: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    // ========== ROUTE MANAGEMENT METHODS ==========

    /**
     * Initialisiert das Storage-System
     */
    fun initializeStorage(context: Context) {
        if (!isStorageInitialized) {
            Log.i("RouteViewModel", "Storage initialisiert")
            isStorageInitialized = true
        }
    }

    /**
     * Aktiviert das Storage-System sofort
     */
    fun enableStorageSystemImmediately(context: Context) {
        initializeStorage(context)
        Log.i("RouteViewModel", "Storage-System aktiviert")
    }

    /**
     * Lädt die Navigationsroute aus route.json
     * Parst das komplexe verschachtelte Format mit Gson
     */
    fun loadNavigationRoute(context: Context): NavigationRoute? {
        return try {
            val inputStream = context.assets.open("route.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }

            // Parse mit Gson
            val gson = Gson()
            val routeData = gson.fromJson(jsonString, RouteData::class.java)

            if (routeData.route.path.isEmpty()) {
                Log.e("RouteViewModel", "Keine Path-Daten gefunden")
                return null
            }

            val pathItem = routeData.route.path[0]
            val routeParts = pathItem.routeParts

            // Konvertiere RouteParts zu NavigationSteps
            val steps = routeParts.mapIndexed { index, routePart ->
                NavigationStep(
                    stepNumber = index,
                    instruction = routePart.instructionDe,
                    building = pathItem.xmlName,
                    floor = pathItem.levelInfo?.storey?.toIntOrNull() ?: 0,
                    landmarks = routePart.landmarks,
                    distance = routePart.distance ?: 0.0,
                    estimatedTime = routePart.duration ?: 0
                )
            }

            // Extrahiere Start und Ziel aus erster und letzter Instruction
            val startPoint = if (steps.isNotEmpty()) {
                extractLocationFromInstruction(steps.first().instruction, isStart = true)
            } else "Unbekannter Start"

            val endPoint = if (steps.isNotEmpty()) {
                extractLocationFromInstruction(steps.last().instruction, isStart = false)
            } else "Unbekanntes Ziel"

            // Berechne Gesamtlänge
            val totalLength = routeData.route.routeInfo?.routeLength ?: 0.0

            val route = NavigationRoute(
                id = "route_${System.currentTimeMillis()}",
                name = "$startPoint → $endPoint",
                description = "Navigation von $startPoint nach $endPoint",
                startPoint = startPoint,
                endPoint = endPoint,
                totalLength = totalLength,
                steps = steps,
                totalDistance = totalLength,
                estimatedTime = routeData.route.routeInfo?.estimatedTime ?: 0
            )

            _currentRoute.value = route
            Log.i("RouteViewModel", "✅ Route geladen: ${steps.size} Schritte, ${totalLength}m")

            route
        } catch (e: Exception) {
            Log.e("RouteViewModel", "❌ Fehler beim Laden der Route: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * Extrahiert Ortsnamen aus Instruction-Text
     */
    private fun extractLocationFromInstruction(instruction: String, isStart: Boolean): String {
        return try {
            // Entferne HTML-Tags
            val cleanText = instruction.replace("<b>", "").replace("</b>", "").replace("</", "")

            // Suche nach Raumnummern im Format (PT X.X.XXX)
            val roomPattern = "\\(PT [0-9.]+[A-Z]*\\)".toRegex()
            val roomMatch = roomPattern.find(cleanText)

            if (roomMatch != null) {
                // Extrahiere Text vor der Raumnummer
                val beforeRoom = cleanText.substring(0, roomMatch.range.first).trim()
                val words = beforeRoom.split(" ")

                // Letzten Teil vor Raumnummer nehmen (meist der Name)
                val name = words.takeLast(2).joinToString(" ")
                return "$name ${roomMatch.value}"
            }

            // Fallback: Nutze die Instruction selbst
            if (isStart) "Startpunkt" else "Zielpunkt"
        } catch (e: Exception) {
            if (isStart) "Startpunkt" else "Zielpunkt"
        }
    }

    /**
     * Gibt den Startpunkt der aktuellen Route zurück
     */
    fun getCurrentStartPoint(): String {
        return _currentRoute.value?.startPoint ?: "Unbekannter Start"
    }

    /**
     * Gibt den Endpunkt der aktuellen Route zurück
     */
    fun getCurrentEndPoint(): String {
        return _currentRoute.value?.endPoint ?: "Unbekanntes Ziel"
    }

    /**
     * Gibt alle Navigationsschritte zurück
     */
    fun getCurrentNavigationSteps(): List<NavigationStep> {
        return _currentRoute.value?.steps ?: emptyList()
    }

    /**
     * Setzt den aktuellen Navigationsschritt
     */
    fun setCurrentNavigationStep(stepNumber: Int) {
        _currentNavigationStep.value = stepNumber
        Log.i("RouteViewModel", "📍 Navigationsschritt gesetzt: $stepNumber")
    }

    /**
     * Setzt die Schwelle für automatischen Schrittwechsel (0.0 - 1.0)
     */
    fun setAutoAdvanceThreshold(value: Float) {
        autoAdvanceThreshold = value.coerceIn(0f, 1f)
        Log.i("RouteViewModel", "🛠 Auto-Advance Threshold gesetzt auf ${(autoAdvanceThreshold * 100).toInt()}%")
    }

    /**
     * Überspringt den aktuellen Navigationsschritt manuell:
     * - Markiert den aktuellen Schritt als gelöscht
     * - Erhöht den aktuellen Schritt auf den nächsten (falls vorhanden)
     * - Setzt den Navigationsstatus passend
     */
    fun skipCurrentStep() {
        val route = _currentRoute.value
        if (route == null) {
            Log.w("RouteViewModel", "⚠ Kann Schritt nicht überspringen: keine Route geladen")
            return
        }

        val current = _currentNavigationStep.value
        val lastIndex = route.steps.lastIndex

        // Markiere aktuellen Schritt als gelöscht
        val newDeleted = _deletedSteps.value.toMutableSet()
        newDeleted.add(current)
        _deletedSteps.value = newDeleted
        Log.i("RouteViewModel", "⏭ Schritt $current manuell übersprungen (als gelöscht markiert)")

        // Falls möglich, zum nächsten Schritt springen, sonst Route abschließen
        if (current < lastIndex) {
            _currentNavigationStep.value = current + 1
            _navigationStatus.value = NavigationStatus.STEP_COMPLETED
            Log.i("RouteViewModel", "➡ Wechsle zu Schritt ${current + 1}")
        } else {
            _navigationStatus.value = NavigationStatus.ROUTE_COMPLETED
            Log.i("RouteViewModel", "🏁 Letzter Schritt übersprungen – Route abgeschlossen")
        }

        // Detection-Historie zurücksetzen, damit neue Landmark-Erkennung sauber startet
        landmarkDetectionHistory.clear()
    }

    /**
     * Loggt die geladene Route
     */
    fun logNavigationRoute(route: NavigationRoute) {
        Log.i("RouteViewModel", "=== ROUTE DETAILS ===")
        Log.i("RouteViewModel", "Start: ${route.startPoint}")
        Log.i("RouteViewModel", "Ziel: ${route.endPoint}")
        Log.i("RouteViewModel", "Länge: ${route.totalDistance}m")
        Log.i("RouteViewModel", "Schritte: ${route.steps.size}")

        route.steps.forEachIndexed { index, step ->
            Log.i("RouteViewModel", "Schritt ${index + 1}: ${step.instruction}")
            if (step.landmarks.isNotEmpty()) {
                Log.i("RouteViewModel", "  Landmarks: ${step.landmarks.size}")
                step.landmarks.forEach { landmark ->
                    val name = landmark.name.takeIf { !it.isNullOrBlank() } ?: "(kein Name)"
                    Log.i("RouteViewModel", "    - $name (${landmark.id})")
                }
            }
        }
    }

    /**
     * Gibt alle verfügbaren Landmarks aus der aktuellen Route zurück
     */
    fun getAvailableLandmarks(): List<RouteLandmarkData> {
        val route = _currentRoute.value ?: return emptyList()

        // Sammle alle Landmarks aus allen Schritten
        val allLandmarks = mutableListOf<RouteLandmarkData>()
        route.steps.forEach { step ->
            allLandmarks.addAll(step.landmarks)
        }

        // Entferne Duplikate basierend auf ID
        return allLandmarks.distinctBy { it.id }
    }
    
    // ========== INTELLIGENTE NAVIGATION METHODS ==========
    
    /**
     * 🎯 Intelligente Schritt-Erkennung basierend auf Landmark-Matches
     */
    private fun analyzeStepProgression(matches: List<LandmarkMatch>) {
        Log.i("Navigation", "🎯 analyzeStepProgression gestartet mit ${matches.size} matches")
        
        if (matches.isEmpty()) {
            Log.i("Navigation", "⚠ Keine Matches - handleNoLandmarkDetection")
            handleNoLandmarkDetection()
            return
        }
        
        // 1) Direkte Regel: Wenn der erwartete Landmark des aktuellen Schritts mit >= 50% erkannt wurde,
        //    automatisch zum nächsten Schritt springen.
        val currentStepDirect = _currentNavigationStep.value
        val expectedIds = getExpectedLandmarksForStep(currentStepDirect).map { it.id }.toSet()
        val strongExpectedMatch = matches.firstOrNull { it.landmark.id in expectedIds && it.confidence >= autoAdvanceThreshold }
        val routeSize = _currentRoute.value?.steps?.size ?: 0
        if (strongExpectedMatch != null && currentStepDirect < routeSize - 1) {
            Log.i(
                "Navigation",
                "✅ Direkter Auto-Sprung: erkannter expected landmark ${strongExpectedMatch.landmark.id} mit ${(strongExpectedMatch.confidence * 100).toInt()}%"
            )
            performStepTransition(
                fromStep = currentStepDirect,
                toStep = currentStepDirect + 1,
                trigger = "auto_landmark_${strongExpectedMatch.landmark.id}",
                confidence = strongExpectedMatch.confidence
            )
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val bestMatch = matches.first() // Bereits nach Confidence sortiert
        Log.i("Navigation", "🎯 Bester Match: ${bestMatch.landmark.id} mit ${(bestMatch.confidence * 100).toInt()}% confidence")
        
        // Finde den Schritt, der diese Landmark enthält
        val targetStep = findStepForLandmark(bestMatch.landmark.id)
        Log.i("Navigation", "🗺 Landmark ${bestMatch.landmark.id} gehört zu Schritt: $targetStep")
        
        if (targetStep == -1) {
            Log.w("Navigation", "⚠ Landmark ${bestMatch.landmark.id} keinem Schritt zugeordnet")
            return
        }
        
        // Aktualisiere Detection History
        updateDetectionHistory(bestMatch.landmark.id, targetStep, bestMatch.confidence, currentTime)
        
        // Prüfe ob Schritt-Wechsel ausgelöst werden soll
        val history = landmarkDetectionHistory[bestMatch.landmark.id]
        if (history?.isStable == true && bestMatch.confidence >= confidenceThreshold) {
            handleStepTransition(targetStep, bestMatch.confidence, "landmark_${bestMatch.landmark.id}")
        }
    }
    
    /**
     * Findet den Schritt-Index für eine gegebene Landmark-ID
     */
    private fun findStepForLandmark(landmarkId: String): Int {
        val route = _currentRoute.value ?: return -1
        
        route.steps.forEachIndexed { index, step ->
            if (step.landmarks.any { it.id == landmarkId }) {
                return index
            }
        }
        return -1
    }
    
    /**
     * Aktualisiert die Detection History für eine Landmark
     */
    private fun updateDetectionHistory(
        landmarkId: String,
        stepNumber: Int,
        confidence: Float,
        currentTime: Long
    ) {
        val existing = landmarkDetectionHistory[landmarkId]
        
        if (existing == null) {
            // Erste Erkennung
            landmarkDetectionHistory[landmarkId] = DetectionHistory(
                landmarkId = landmarkId,
                stepNumber = stepNumber,
                firstDetectionTime = currentTime,
                lastDetectionTime = currentTime,
                highestConfidence = confidence,
                isStable = false
            )
            Log.d("Navigation", "🆕 Erste Erkennung: $landmarkId (${(confidence * 100).toInt()}%)")
        } else {
            // Aktualisiere bestehende History
            existing.lastDetectionTime = currentTime
            existing.highestConfidence = maxOf(existing.highestConfidence, confidence)
            
            // Prüfe Stabilität (mindestens X Sekunden kontinuierliche Erkennung)
            val detectionDuration = currentTime - existing.firstDetectionTime
            if (detectionDuration >= stabilityDuration && confidence >= confidenceThreshold) {
                if (!existing.isStable) {
                    existing.isStable = true
                    Log.i("Navigation", "✅ Stabile Erkennung: $landmarkId nach ${detectionDuration}ms")
                }
            }
        }
    }
    
    /**
     * 🎯 Behandelt Schritt-Übergänge mit intelligentem Scoring
     */
    private fun handleStepTransition(targetStep: Int, confidence: Float, trigger: String) {
        val currentStep = _currentNavigationStep.value
        
        if (targetStep == currentStep) {
            // Gleicher Schritt - bestätige aktuelle Position
            Log.d("Navigation", "📍 Position bestätigt: Schritt $currentStep (${(confidence * 100).toInt()}%)")
            return
        }
        
        // Gewichteter Score für Schritt-Übergang
        val stepDistance = kotlin.math.abs(targetStep - currentStep)
        val stepProximityScore = when {
            stepDistance == 1 -> 1.0f // Nächster/vorheriger Schritt
            stepDistance <= 2 -> 0.7f // Nahegelegene Schritte
            else -> 0.3f // Weit entfernte Schritte
        }
        
        val historyScore = if (targetStep > currentStep) 0.9f else 0.6f // Bevorzuge Vorwärts-Navigation
        
        // 🎯 BONUS für sehr hohe Confidence (>70%)
        val confidenceBonus = if (confidence >= 0.70f) 0.1f else 0f
        val combinedScore = (confidence * 0.6f) + (stepProximityScore * 0.3f) + (historyScore * 0.1f) + confidenceBonus
        
        Log.i("Navigation", "🧮 Schritt-Score: Step $currentStep→$targetStep, Combined: ${(combinedScore * 100).toInt()}%")
        
        // 🎯 ANGEPASSTE Schwellenwerte für verschiedene Sprung-Distanzen
        val requiredScore = when {
            stepDistance == 1 -> 0.65f      // Nächster Schritt: 65%
            stepDistance <= 3 -> 0.70f      // Nahe Schritte: 70% 
            stepDistance <= 5 -> 0.72f      // Mittlere Sprünge: 72%
            else -> 0.75f                   // Große Sprünge: 75%
        }
        
        Log.i("Navigation", "📊 Schwellenwert für ${stepDistance}-Schritt-Sprung: ${(requiredScore * 100).toInt()}%")
        
        if (combinedScore >= requiredScore) {
            performStepTransition(currentStep, targetStep, trigger, confidence)
        } else {
            Log.d("Navigation", "🚫 Schritt-Wechsel abgelehnt: Score ${(combinedScore * 100).toInt()}% < ${(requiredScore * 100).toInt()}% (${stepDistance} Schritte)")
        }
    }
    
    /**
     * Führt tatsächlichen Schritt-Wechsel durch
     */
    private fun performStepTransition(fromStep: Int, toStep: Int, trigger: String, confidence: Float) {
        Log.i("Navigation", "🎯 Schritt-Wechsel: $fromStep → $toStep (Trigger: $trigger, ${(confidence * 100).toInt()}%)")
        
        // Markiere vorherigen Schritt als abgeschlossen
        if (toStep > fromStep) {
            val newCompleted = _completedSteps.value.toMutableSet()
            for (step in fromStep until toStep) {
                newCompleted.add(step)
            }
            _completedSteps.value = newCompleted
            Log.i("Navigation", "✅ Schritte $fromStep bis ${toStep - 1} als abgeschlossen markiert")
        }
        
        // Wechsle zum neuen Schritt
        _currentNavigationStep.value = toStep
        _navigationStatus.value = NavigationStatus.STEP_COMPLETED
        
        // Reset Detection History für sauberen Übergang
        landmarkDetectionHistory.clear()
        
        // Logge Transition
        val transition = StepTransition(fromStep, toStep, trigger, confidence)
        Log.i("Navigation", "📝 Transition geloggt: $transition")
    }
    
    /**
     * Behandelt den Fall, wenn keine Landmarks erkannt werden
     */
    private fun handleNoLandmarkDetection() {
        val currentTime = System.currentTimeMillis()
        
        // Prüfe ob wir lange keine Landmarks gesehen haben
        val lastDetection = landmarkDetectionHistory.values.maxByOrNull { it.lastDetectionTime }
        if (lastDetection != null) {
            val timeSinceLastDetection = currentTime - lastDetection.lastDetectionTime
            
            if (timeSinceLastDetection > lostTrackingDuration) {
                if (_navigationStatus.value != NavigationStatus.LOST_TRACKING) {
                    _navigationStatus.value = NavigationStatus.LOST_TRACKING
                    Log.w("Navigation", "⚠ Tracking verloren nach ${timeSinceLastDetection}ms ohne Landmark")
                }
            }
        }
    }
    
    /**
     * 🗑 Markiert einen Schritt als gelöscht (Swipe-to-Delete)
     */
    fun deleteCompletedStep(stepNumber: Int) {
        val completed = _completedSteps.value
        if (stepNumber in completed) {
            val newDeleted = _deletedSteps.value.toMutableSet()
            newDeleted.add(stepNumber)
            _deletedSteps.value = newDeleted
            Log.i("Navigation", "🗑 Schritt $stepNumber als gelöscht markiert")
            
            // Auto-restore nach 10 Sekunden
            viewModelScope.launch {
                kotlinx.coroutines.delay(10000L)
                restoreDeletedStep(stepNumber)
            }
        }
    }
    
    /**
     * 🔄 Stellt einen gelöschten Schritt wieder her
     */
    fun restoreDeletedStep(stepNumber: Int) {
        val newDeleted = _deletedSteps.value.toMutableSet()
        if (newDeleted.remove(stepNumber)) {
            _deletedSteps.value = newDeleted
            Log.i("Navigation", "🔄 Schritt $stepNumber wiederhergestellt")
        }
    }
    
    /**
     * 🔄 Setzt die komplette Navigation zurück
     */
    fun resetNavigation() {
        Log.i("Navigation", "🔄 Navigation wird zurückgesetzt")
        
        _currentNavigationStep.value = 0
        _completedSteps.value = emptySet()
        _deletedSteps.value = emptySet()
        _navigationStatus.value = NavigationStatus.WAITING
        
        landmarkDetectionHistory.clear()
        
        Log.i("Navigation", "✅ Navigation erfolgreich zurückgesetzt")
    }

    // ========== FEATURE MAPPING METHODS ==========

    /**
     * Initialisiert das Feature Mapping System
     */
    fun initializeFeatureMapping(context: Context) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                Log.i("FeatureMapping", "🚀 Initialisiere Feature Mapping...")

                // 🎯 Erstelle AKAZE Detector - OPTIMIERT für Indoor AR-Navigation
                akazeDetector = AKAZE.create(
                    // Verwende Standard-Parameter für maximale Kompatibilität
                )
                
                Log.i("FeatureMapping", "✨ AKAZE Detector initialisiert (optimiert für Indoor-Landmarks)")

                // 🎯 Erstelle optimierten Matcher für AKAZE Binary Descriptors
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
                Log.i("FeatureMapping", "🔗 BruteForce-Hamming Matcher für AKAZE-Descriptors initialisiert")

                // Lade Landmark Features
                loadLandmarkFeatures(context)

                _isFeatureMappingEnabled.value = true
                Log.i("FeatureMapping", "✅ Feature Mapping erfolgreich initialisiert")
                Log.i("FeatureMapping", "📍 Geladene Landmarks: ${landmarkFeaturesCache.size}")

            } catch (e: Exception) {
                Log.e("FeatureMapping", "❌ Fehler bei Initialisierung: ${e.message}", e)
                _isFeatureMappingEnabled.value = false
            }
        }
    }

    /**
     * Lädt alle Landmark Features aus den Assets
     */
    private suspend fun loadLandmarkFeatures(context: Context) = withContext(Dispatchers.IO) {
        try {
            val availableLandmarks = getAvailableLandmarks()
            Log.i("FeatureMapping", "📂 Lade Features für ${availableLandmarks.size} Landmarks...")

            availableLandmarks.forEach { landmark ->
                try {
                    val bitmap = loadLandmarkBitmap(context, landmark.id)
                    if (bitmap != null) {
                        val features = extractFeatures(bitmap, landmark.id)
                        if (features != null) {
                            landmarkFeaturesCache[landmark.id] = features
                            Log.d("FeatureMapping", "✓ Features geladen für: ${landmark.id} (${features.keypoints.rows()} keypoints)")
                        } else {
                            Log.w("FeatureMapping", "⚠ Keine Features extrahiert für: ${landmark.id}")
                        }
                    } else {
                        Log.w("FeatureMapping", "⚠ Bitmap konnte nicht geladen werden: ${landmark.id}")
                    }
                } catch (e: Exception) {
                    Log.e("FeatureMapping", "❌ Fehler beim Laden von ${landmark.id}: ${e.message}")
                }
            }

            Log.i("FeatureMapping", "✅ Feature-Cache bereit: ${landmarkFeaturesCache.size}/${availableLandmarks.size} Landmarks")
        } catch (e: Exception) {
            Log.e("FeatureMapping", "❌ Fehler beim Laden der Landmark Features: ${e.message}", e)
        }
    }

    /**
     * Lädt ein Landmark-Bild aus den Assets
     */
    private fun loadLandmarkBitmap(context: Context, landmarkId: String): Bitmap? {
        return try {
            val filename = "$landmarkId.jpg"
            val inputStream = context.assets.open("landmark_images/$filename")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            Log.e("FeatureMapping", "Fehler beim Laden von $landmarkId: ${e.message}")
            null
        }
    }

    /**
     * Extrahiert Features aus einem Bitmap
     */
    private fun extractFeatures(bitmap: Bitmap, landmarkId: String): LandmarkFeatures? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Konvertiere zu Graustufen
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            // Extrahiere Features
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            akazeDetector?.detectAndCompute(gray, Mat(), keypoints, descriptors)

            mat.release()
            gray.release()

            if (keypoints.rows() > 0) {
                LandmarkFeatures(
                    id = landmarkId,
                    keypoints = keypoints,
                    descriptors = descriptors
                )
            } else {
                Log.w("FeatureMapping", "Keine Features gefunden für: $landmarkId")
                null
            }
        } catch (e: Exception) {
            Log.e("FeatureMapping", "Fehler bei Feature-Extraktion für $landmarkId: ${e.message}", e)
            null
        }
    }

    /**
     * Verarbeitet einen Kamera-Frame für Feature Matching
     */
    fun processFrameForFeatureMatching(bitmap: Bitmap) {
        Log.d("FeatureMapping", "📥 Frame empfangen: ${bitmap.width}x${bitmap.height}")

        // Prüfe ob Feature Mapping aktiv ist
        if (!_isFeatureMappingEnabled.value) {
            Log.w("FeatureMapping", "⚠ Feature Mapping nicht aktiviert")
            return
        }

        // Prüfe ob bereits verarbeitet wird
        if (_isProcessingFrame.value) {
            Log.d("FeatureMapping", "⏳ Frame wird bereits verarbeitet, überspringe...")
            return
        }

        // Frame-Rate Limiting (nur alle 500ms verarbeiten)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < frameProcessingInterval) {
            Log.d("FeatureMapping", "⏱ Frame-Rate Limit (${currentTime - lastProcessedTime}ms), überspringe...")
            return
        }

        frameCounter++
        lastProcessedTime = currentTime

        Log.i("FeatureMapping", "🎥 Starte Verarbeitung von Frame #$frameCounter")

        viewModelScope.launch(Dispatchers.Default) {
            _isProcessingFrame.value = true

            try {
                // Extrahiere Features aus aktuellem Frame
                Log.i("FeatureMapping", "🔬 Extrahiere Features aus Frame...")
                val frameFeatures = extractFrameFeatures(bitmap)

                if (frameFeatures != null) {
                    Log.i("FeatureMapping", "✅ Frame Features extrahiert: ${frameFeatures.keypoints.rows()} keypoints")
                    Log.i("FeatureMapping", "📍 Cache enthält: ${landmarkFeaturesCache.size} Landmarks")
                    
                    // Matche gegen alle bekannten Landmarks
                    Log.i("FeatureMapping", "🎯 Starte Matching-Prozess...")
                    val matches = matchAgainstLandmarks(frameFeatures)
                    Log.i("FeatureMapping", "📊 Matching abgeschlossen: ${matches.size} Ergebnisse")

                    // Aktualisiere UI
                    _currentMatches.value = matches
                    
                    // 🎯 INTELLIGENTE SCHRITT-ANALYSE
                    Log.i("Navigation", "🔍 Starte Schritt-Analyse mit ${matches.size} matches...")
                    try {
                        analyzeStepProgression(matches)
                        Log.i("Navigation", "✅ Schritt-Analyse erfolgreich abgeschlossen")
                    } catch (e: Exception) {
                        Log.e("Navigation", "❌ Fehler in Schritt-Analyse: ${e.message}", e)
                    }

                    // Logge Top-Matches
                    if (matches.isNotEmpty()) {
                        Log.i("FeatureMapping", "=== Frame #$frameCounter ===")
                        matches.take(3).forEach { match ->
                            Log.i("FeatureMapping", "  📍 ${match.landmark.name}: ${(match.confidence * 100).toInt()}% (${match.matchCount} matches)")
                        }
                    }
                } else {
                    _currentMatches.value = emptyList()
                }

                // Cleanup
                frameFeatures?.keypoints?.release()
                frameFeatures?.descriptors?.release()

            } catch (e: Exception) {
                Log.e("FeatureMapping", "❌ Fehler bei Frame-Verarbeitung: ${e.message}", e)
                _currentMatches.value = emptyList()
            } finally {
                _isProcessingFrame.value = false
            }
        }
    }

    /**
     * Extrahiert Features aus einem Kamera-Frame
     */
    private fun extractFrameFeatures(bitmap: Bitmap): LandmarkFeatures? {
        return try {
            Log.d("FeatureMapping", "🖼 Bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
            
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            Log.d("FeatureMapping", "📐 Mat erstellt: ${mat.rows()}x${mat.cols()} channels: ${mat.channels()}")

            // Konvertiere zu Graustufen
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
            Log.d("FeatureMapping", "🔘 Graustufen-Konvertierung abgeschlossen: ${gray.rows()}x${gray.cols()}")

            // Extrahiere Features
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            
            if (akazeDetector == null) {
                Log.e("FeatureMapping", "❌ AKAZE Detector ist null!")
                return null
            }
            
            akazeDetector?.detectAndCompute(gray, Mat(), keypoints, descriptors)
            Log.d("FeatureMapping", "🔍 Feature-Extraktion: ${keypoints.rows()} keypoints, ${descriptors.rows()} descriptors")

            mat.release()
            gray.release()

            if (keypoints.rows() > 0) {
                Log.i("FeatureMapping", "✅ Frame Features erfolgreich: ${keypoints.rows()} keypoints")
                LandmarkFeatures(
                    id = "frame",
                    keypoints = keypoints,
                    descriptors = descriptors
                )
            } else {
                Log.w("FeatureMapping", "⚠ Keine Features im Frame gefunden")
                null
            }
        } catch (e: Exception) {
            Log.e("FeatureMapping", "❌ Fehler bei Frame Feature-Extraktion: ${e.message}", e)
            null
        }
    }

    /**
     * Matched Frame-Features gegen alle bekannten Landmarks
     */
    private fun matchAgainstLandmarks(frameFeatures: LandmarkFeatures): List<LandmarkMatch> {
        val matches = mutableListOf<LandmarkMatch>()

        try {
            val currentStep = _currentNavigationStep.value
            // DEBUG: Teste gegen ALLE Landmarks, nicht nur erwartete
            val allLandmarks = getAvailableLandmarks()
            Log.d("FeatureMapping", "🔍 Teste gegen ${allLandmarks.size} Landmarks (aktueller Schritt: $currentStep)")

            allLandmarks.forEach { landmark ->
                val landmarkFeatures = landmarkFeaturesCache[landmark.id]

                if (landmarkFeatures != null) {
                    val matchResult = matchFeatures(frameFeatures, landmarkFeatures)

                    // 🎯 AKAZE-OPTIMIERTE SCHWELLEN für bessere Qualität
                    val minMatches = 3      // AKAZE: Höhere Schwelle da bessere Feature-Qualität
                    val minConfidence = 0.15f  // 15% Mindest-Confidence für AKAZE
                    
                    if (matchResult.matchCount >= minMatches && matchResult.confidence >= minConfidence) {
                        matches.add(matchResult)
                        Log.d("FeatureMapping", "🎯 Match gefunden: ${matchResult.landmark.name} - ${matchResult.matchCount} matches, ${(matchResult.confidence * 100).toInt()}% confidence")
                    } else {
                        Log.i("FeatureMapping", "🚫 Schwacher Match ignoriert: ${matchResult.landmark.name ?: "null"} - ${matchResult.matchCount} matches, ${(matchResult.confidence * 100).toInt()}% confidence")
                    }
                }
            }

            // Sortiere nach Confidence (höchste zuerst)
            return matches.sortedByDescending { it.confidence }

        } catch (e: Exception) {
            Log.e("FeatureMapping", "❌ Fehler beim Matching: ${e.message}", e)
            return emptyList()
        }
    }

    /**
     * Matched zwei Feature-Sets
     */
    private fun matchFeatures(
        frameFeatures: LandmarkFeatures,
        landmarkFeatures: LandmarkFeatures
    ): LandmarkMatch {
        Log.d("FeatureMapping", "🎯 Matching: Frame(${frameFeatures.keypoints.rows()}) vs ${landmarkFeatures.id}(${landmarkFeatures.keypoints.rows()})")
        try {
            // 🎯 ROBUSTES MATCHING für Emulator
            val knnMatches = mutableListOf<MatOfDMatch>()
            
            if (matcher == null) {
                Log.e("FeatureMapping", "❌ Matcher ist null!")
                return createEmptyMatch(landmarkFeatures.id)
            }
            
            // Prüfe ob Descriptors valid sind
            if (frameFeatures.descriptors.rows() == 0 || landmarkFeatures.descriptors.rows() == 0) {
                Log.w("FeatureMapping", "⚠ Leere Descriptors: Frame(${frameFeatures.descriptors.rows()}) Landmark(${landmarkFeatures.descriptors.rows()})")
                return createEmptyMatch(landmarkFeatures.id)
            }
            
            Log.d("FeatureMapping", "🔍 KNN Matching wird durchgeführt...")
            matcher?.knnMatch(
                frameFeatures.descriptors,
                landmarkFeatures.descriptors,
                knnMatches,
                2
            )
            Log.d("FeatureMapping", "📊 KNN Matching abgeschlossen: ${knnMatches.size} Match-Paare")

            // Lowe's Ratio Test
            var goodMatches = 0
            var totalDistance = 0f

            knnMatches.forEach { match ->
                val matches = match.toArray()
                if (matches.size >= 2) {
                    val m = matches[0]
                    val n = matches[1]

                    // 🎯 AKAZE-OPTIMIERTER Ratio Test für höchste Qualität
                    val ratioThreshold = 0.7f  // AKAZE erlaubt schärfere Schwellen dank besserer Descriptors
                    if (m.distance < ratioThreshold * n.distance) {
                        goodMatches++
                        totalDistance += m.distance
                    }
                }
            }

            // Berechne Confidence (0-1) - VERBESSERTE VERSION
            val avgDistance = if (goodMatches > 0) totalDistance / goodMatches else Float.MAX_VALUE
            
            val frameKeypointCount = frameFeatures.keypoints.rows()
            val landmarkKeypointCount = landmarkFeatures.keypoints.rows()
            val minKeypoints = minOf(frameKeypointCount, landmarkKeypointCount)
            
            // 🎯 AKAZE-OPTIMIERTE Confidence-Berechnung für Indoor-Navigation
            val confidence = if (goodMatches > 0) {
                when {
                    // AKAZE Low-Feature Modus: Konservative Bewertung
                    minKeypoints <= 15 -> {
                        val score = (goodMatches.toFloat() / 8f).coerceAtMost(1f)  // Höhere Schwelle für AKAZE
                        Log.d("FeatureMapping", "🎯 AKAZE Low-Feature: $goodMatches matches -> ${(score * 100).toInt()}%")
                        score
                    }
                    // AKAZE Standard-Modus: Optimiert für hohe Qualität
                    else -> {
                        val matchRatio = goodMatches.toFloat() / minKeypoints.toFloat()
                        val qualityScore = (goodMatches.toFloat() / 15f).coerceAtMost(1f) // AKAZE: 15 Matches = 100%
                        val distanceScore = if (avgDistance != Float.MAX_VALUE) {
                            (100f / (avgDistance + 1f)).coerceIn(0f, 1f)  // Berücksichtige Match-Distanz
                        } else 0f
                        
                        // Gewichtete Kombinierung: Ratio(40%) + Quality(40%) + Distance(20%)
                        val combinedScore = (matchRatio * 0.4f + qualityScore * 0.4f + distanceScore * 0.2f).coerceIn(0f, 1f)
                        Log.d("FeatureMapping", "✨ AKAZE Quality: ratio=${(matchRatio*100).toInt()}%, quality=${(qualityScore*100).toInt()}%, distance=${(distanceScore*100).toInt()}% -> ${(combinedScore*100).toInt()}%")
                        combinedScore
                    }
                }
            } else {
                0f
            }
            
            // Debug-Ausgabe für Confidence-Berechnung
            Log.d("FeatureMapping", "📊 Confidence Debug: ${landmarkFeatures.id}")
            Log.d("FeatureMapping", "   Frame keypoints: $frameKeypointCount, Landmark keypoints: $landmarkKeypointCount")
            Log.d("FeatureMapping", "   Good matches: $goodMatches, Confidence: ${(confidence * 100).toInt()}%")

            // Cleanup
            knnMatches.forEach { it.release() }

            // Erstelle Match-Result
            val landmark = getAvailableLandmarks().find { it.id == landmarkFeatures.id }
                ?: RouteLandmarkData(landmarkFeatures.id, landmarkFeatures.id)

            return LandmarkMatch(
                landmark = landmark,
                matchCount = goodMatches,
                confidence = confidence,
                distance = avgDistance
            )

        } catch (e: Exception) {
            Log.e("FeatureMapping", "❌ Fehler beim Feature-Matching: ${e.message}")
            val landmark = getAvailableLandmarks().find { it.id == landmarkFeatures.id }
                ?: RouteLandmarkData(
                    id = landmarkFeatures.id,
                    name = landmarkFeatures.id  // Nutze ID als Fallback-Name
                )
            return LandmarkMatch(landmark, 0, 0f, Float.MAX_VALUE)
        }
    }

    /**
     * Gibt die erwarteten Landmarks für einen Navigationsschritt zurück
     */
    private fun getExpectedLandmarksForStep(stepNumber: Int): List<RouteLandmarkData> {
        val route = _currentRoute.value ?: return emptyList()

        if (stepNumber < 0 || stepNumber >= route.steps.size) {
            return emptyList()
        }

        val step = route.steps[stepNumber]
        return step.landmarks
    }

    /**
     * Startet die Frame-Verarbeitung
     */
    fun startFrameProcessing() {
        Log.i("FeatureMapping", "▶ Frame-Verarbeitung gestartet")
    }

    /**
     * Stoppt die Frame-Verarbeitung
     */
    fun stopFrameProcessing() {
        Log.i("FeatureMapping", "⏸ Frame-Verarbeitung gestoppt")
        _currentMatches.value = emptyList()
    }

    /**
     * Cleanup bei ViewModel-Zerstörung
     */
    override fun onCleared() {
        super.onCleared()

        // Release OpenCV Resources
        akazeDetector = null
        matcher = null

        // Release cached features
        landmarkFeaturesCache.values.forEach { features ->
            try {
                features.keypoints.release()
                features.descriptors.release()
            } catch (e: Exception) {
                Log.w("FeatureMapping", "Fehler beim Release: ${e.message}")
            }
        }
        landmarkFeaturesCache.clear()

        Log.i("FeatureMapping", "🧹 ViewModel cleanup abgeschlossen")
    }
    
    /**
     * Erstellt einen leeren Match für Debug-Zwecke
     */
    private fun createEmptyMatch(landmarkId: String): LandmarkMatch {
        val landmark = getAvailableLandmarks().find { it.id == landmarkId }
            ?: RouteLandmarkData(landmarkId, landmarkId)
        return LandmarkMatch(landmark, 0, 0f, Float.MAX_VALUE)
    }
}
