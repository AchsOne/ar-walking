package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.example.arwalking.navigation.*
import com.example.arwalking.managers.PositionTrackingManager
import com.example.arwalking.managers.StepProgressionManager
import com.example.arwalking.managers.LandmarkMatchingManager
import com.example.arwalking.navigation.SmartStepProgressionManager
import com.example.arwalking.navigation.ProgressionResult
import com.example.arwalking.navigation.Vec3

/**
 * RouteViewModel using manager classes for better separation of concerns.
 * Manages route loading, coordinates managers, and maintains state.
 */
class RouteViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "RouteViewModel"
    }

    // Manager instances
    private val positionTrackingManager = PositionTrackingManager()
    private val stepProgressionManager = StepProgressionManager()
    private val landmarkMatchingManager = LandmarkMatchingManager()
    private val smartStepProgressionManager = SmartStepProgressionManager()
    
    // Route state
    private val _currentRoute = MutableStateFlow<NavigationRoute?>(null)
    val currentRoute: StateFlow<NavigationRoute?> = _currentRoute.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _deletedSteps = MutableStateFlow<Set<Int>>(emptySet())
    val deletedSteps: StateFlow<Set<Int>> = _deletedSteps.asStateFlow()

    private val _routePath = MutableStateFlow<RoutePath?>(null)
    val routePath: StateFlow<RoutePath?> = _routePath.asStateFlow()
    
    private var routeBuilder: RouteBuilder = RouteBuilderImpl()
    private var session: Session? = null

    // State flows managed directly for compatibility
    private val _completedSteps = MutableStateFlow<Set<Int>>(emptySet())
    val completedSteps: StateFlow<Set<Int>> = _completedSteps.asStateFlow()
    
    private val _navigationStatus = MutableStateFlow(NavigationStatus.WAITING)
    val navigationStatus: StateFlow<NavigationStatus> = _navigationStatus.asStateFlow()
    
    private val _currentMatches = MutableStateFlow<List<LandmarkMatch>>(emptyList())
    val currentMatches: StateFlow<List<LandmarkMatch>> = _currentMatches.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _featureMappingEnabled = MutableStateFlow(false)
    val featureMappingEnabled: StateFlow<Boolean> = _featureMappingEnabled.asStateFlow()
    
    private val _arrowState = MutableStateFlow(ArrowController.ArrowState())
    val arrowState: StateFlow<ArrowController.ArrowState> = _arrowState.asStateFlow()
    
    private val _userProgress = MutableStateFlow(0f)
    val userProgress: StateFlow<Float> = _userProgress.asStateFlow()
    
    private val _distanceToNext = MutableStateFlow(Float.MAX_VALUE)
    val distanceToNext: StateFlow<Float> = _distanceToNext.asStateFlow()

    fun setSession(arSession: Session) {
        session = arSession
    }

    /**
     * Load route from JSON asset
     */
    fun loadRoute(context: Context): NavigationRoute? {
        return try {
            val json = context.assets.open("route.json")
                .bufferedReader()
                .use { it.readText() }

            val routeData = Gson().fromJson(json, RouteData::class.java)

            if (routeData.route.path.isEmpty()) {
                Log.e(TAG, "No path data found")
                return null
            }

            val pathItem = routeData.route.path[0]
            val steps = pathItem.routeParts.mapIndexed { index, part ->
                // Calculate expected walking distance from edge lengths
                val walkDistance = part.nodes?.sumOf { nodeWrapper -> 
                    nodeWrapper.edge?.lengthInMeters?.toDoubleOrNull() ?: 0.0 
                } ?: 0.0
                
                Log.d(TAG, "Step $index: ${part.instructionDe} - Expected distance: ${"%.2f".format(walkDistance)}m (${part.landmarks.size} landmarks)")
                
                NavigationStep(
                    stepNumber = index,
                    instruction = part.instructionDe,
                    building = pathItem.xmlName,
                    floor = pathItem.levelInfo?.storey?.toIntOrNull() ?: 0,
                    landmarks = part.landmarks,
                    distance = part.distance ?: 0.0,
                    expectedWalkDistance = walkDistance,
                    estimatedTime = part.duration ?: 0
                )
            }

            val startPoint = steps.firstOrNull()?.instruction?.let {
                extractLocation(it, isStart = true)
            } ?: "Unknown start"

            val endPoint = steps.lastOrNull()?.instruction?.let {
                extractLocation(it, isStart = false)
            } ?: "Unknown destination"

            val route = NavigationRoute(
                id = "route_${System.currentTimeMillis()}",
                name = "$startPoint → $endPoint",
                description = "Navigation from $startPoint to $endPoint",
                startPoint = startPoint,
                endPoint = endPoint,
                totalLength = routeData.route.routeInfo?.routeLength ?: 0.0,
                steps = steps,
                totalDistance = routeData.route.routeInfo?.routeLength ?: 0.0,
                estimatedTime = routeData.route.routeInfo?.estimatedTime ?: 0
            )

            _currentRoute.value = route
            
            // Reset to step 0 when loading new route
            _currentStep.value = 0
            _completedSteps.value = emptySet()
            _navigationStatus.value = NavigationStatus.WAITING
            smartStepProgressionManager.reset()
            Log.d(TAG, "Route state reset: currentStep=0, completedSteps=[], status=WAITING")
            
            // Build navigation route path and initialize position tracking
            val routePath = routeBuilder.buildFromJson(routeData)
            if (routePath != null) {
                _routePath.value = routePath
                viewModelScope.launch {
                    positionTrackingManager.initializeComponents(routePath)
                }
                Log.d(TAG, "Navigation route built: ${routePath.vertices.size} vertices, ${routePath.totalLength}m, ${routePath.maneuvers.size} maneuvers")
            } else {
                Log.w(TAG, "Failed to build navigation route path")
            }
            
            // Initialize landmark matching with route context
            landmarkMatchingManager.initializeFeatureDetection()
            // Pre-load landmark features for the route
            viewModelScope.launch {
                landmarkMatchingManager.preloadLandmarkFeatures(route, context)
            }
            
            Log.d(TAG, "Route loaded: ${steps.size} steps, ${route.totalLength}m")
            route
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load route", e)
            null
        }
    }

    /**
     * Enable/disable feature mapping
     */
    fun setFeatureMappingEnabled(enabled: Boolean) {
        _featureMappingEnabled.value = enabled
        landmarkMatchingManager.setFeatureMappingEnabled(enabled)
    }

    /**
     * Process camera frame for landmark detection and navigation updates
     * Simplified version that maintains compatibility
     */
    suspend fun processFrame(bitmap: Bitmap, context: Context) {
        if (!_featureMappingEnabled.value) return
        
        _isProcessing.value = true
        
        try {
            // Basic landmark matching using manager
            landmarkMatchingManager.processFrame(
                bitmap = bitmap,
                currentRoute = _currentRoute.value,
                context = context,
                onProgressUpdate = { matches ->
                    // Convert matches and update state
                    val convertedMatches = matches.map { match ->
                        LandmarkMatch(match.landmark, match.matchCount, match.confidence, match.distance)
                    }
                    _currentMatches.value = convertedMatches
                    
                    // Smart step progression with landmark + distance tracking
                    // This will be called from updatePosition with actual ARCore position
                    
                    // Debug: Log matches
                    if (convertedMatches.isNotEmpty()) {
                        val best = convertedMatches.first()
                        Log.d(TAG, "🎯 Match found: ${best.landmark.id} with ${(best.confidence * 100).toInt()}% confidence (${best.matchCount} matches)")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing failed", e)
        } finally {
            _isProcessing.value = false
        }
    }

    /**
     * Update position based on ARCore frame
     */
    suspend fun updatePosition(frame: Frame) {
        try {
            positionTrackingManager.updatePosition(
                frame = frame,
                currentStep = _currentStep.value,
                currentRoute = _currentRoute.value,
                hasLandmarkMatches = _currentMatches.value.isNotEmpty()
            )
            
            // Sync state from position manager
            _userProgress.value = positionTrackingManager.getCurrentProgress()
            
            // Smart step progression using ARCore position
            val cameraPose = frame.camera.pose
            val currentPosition = Vec3(
                cameraPose.translation[0],
                cameraPose.translation[1], 
                cameraPose.translation[2]
            )
            
            val progressionResult = smartStepProgressionManager.analyzeStepProgression(
                currentStep = _currentStep.value,
                currentRoute = _currentRoute.value,
                landmarkMatches = _currentMatches.value,
                currentPosition = currentPosition
            )
            
            when (progressionResult) {
                is ProgressionResult.AdvanceToStep -> {
                    val currentStep = _currentStep.value
                    _currentStep.value = progressionResult.newStep
                    _completedSteps.value = _completedSteps.value + currentStep
                    _navigationStatus.value = NavigationStatus.STEP_COMPLETED
                    smartStepProgressionManager.onStepAdvanced(progressionResult.newStep)
                    
                    Log.d(TAG, "✅ Step advanced: $currentStep -> ${progressionResult.newStep} (${progressionResult.reason})")
                    
                    // Log distance stats
                    val stats = smartStepProgressionManager.getDistanceStats()
                    Log.d(TAG, "📊 Distance stats: step=${String.format("%.2f", stats.currentStepDistance)}m, total=${String.format("%.2f", stats.totalDistance)}m")
                }
                is ProgressionResult.NoAdvance -> {
                    Log.v(TAG, "🔄 No advance: ${progressionResult.reason}")
                    
                    // Log current step progress
                    val currentRoute = _currentRoute.value
                    val currentStep = _currentStep.value
                    if (currentRoute != null && currentStep < currentRoute.steps.size) {
                        val step = currentRoute.steps[currentStep]
                        val stepProgress = smartStepProgressionManager.getCurrentStepProgress()
                        val progressPercent = smartStepProgressionManager.getStepProgressPercent(step.expectedWalkDistance)
                        Log.v(TAG, "📍 Step progress: ${String.format("%.2f", stepProgress)}m / ${String.format("%.2f", step.expectedWalkDistance)}m (${String.format("%.1f", progressPercent)}%)")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Position update failed", e)
        }
    }

    /**
     * Skip current step (for testing/debugging)
     */
    fun skipStep() {
        val currentRoute = _currentRoute.value ?: return
        val currentStep = _currentStep.value
        val nextStep = currentStep + 1
        
        if (nextStep < currentRoute.steps.size) {
            _currentStep.value = nextStep
            _completedSteps.value = _completedSteps.value + currentStep
            _navigationStatus.value = NavigationStatus.STEP_COMPLETED
            Log.d(TAG, "Step manually skipped: $currentStep -> $nextStep")
        }
    }

    /**
     * Start processing (for compatibility)
     */
    fun startProcessing() {
        Log.d(TAG, "Frame processing started")
    }
    
    /**
     * Log route information (for compatibility)
     */
    fun logRoute(route: NavigationRoute) {
        Log.d(TAG, "Route loaded: ${route.name} with ${route.steps.size} steps")
    }
    
    /**
     * Initialize feature mapping (for compatibility)
     */
    fun initFeatureMapping(context: Context) {
        setFeatureMappingEnabled(true)
        
        // Pre-load landmark features if route is available
        val route = _currentRoute.value
        if (route != null) {
            viewModelScope.launch {
                landmarkMatchingManager.preloadLandmarkFeatures(route, context)
            }
        }
        
        Log.d(TAG, "Feature mapping initialized")
    }

    private fun extractLocation(instruction: String, isStart: Boolean): String {
        return try {
            val clean = instruction.replace(Regex("</?b>"), "")
            val roomPattern = "\\(PT [0-9.]+[A-Z]*\\)".toRegex()
            val match = roomPattern.find(clean)

            if (match != null) {
                val before = clean.substring(0, match.range.first).trim()
                val words = before.split(" ")
                val name = words.takeLast(2).joinToString(" ")
                "$name ${match.value}"
            } else {
                if (isStart) "Start" else "Destination"
            }
        } catch (e: Exception) {
            if (isStart) "Start" else "Destination"
        }
    }

    override fun onCleared() {
        super.onCleared()
        
        // Clean up managers
        try {
            positionTrackingManager.cleanup()
            landmarkMatchingManager.cleanup()
        } catch (e: Exception) {
            Log.w(TAG, "Manager cleanup failed", e)
        }
        
        session = null
        Log.d(TAG, "RouteViewModel cleared")
    }

    // Legacy data classes for compatibility (should eventually be moved to shared package)
    data class LandmarkFeatures(
        val id: String,
        val keypoints: org.opencv.core.MatOfKeyPoint,
        val descriptors: org.opencv.core.Mat
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
    
    data class SmartStartResult(
        val targetStep: Int,
        val match: LandmarkMatch
    )

    enum class NavigationStatus {
        WAITING,
        TRACKING,
        STEP_COMPLETED,
        LOST_TRACKING,
        ROUTE_COMPLETED
    }
}