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
import com.example.arwalking.managers.StepSensorManager
import com.example.arwalking.navigation.SmartStepProgressionManager
import com.example.arwalking.navigation.ProgressionResult
import com.example.arwalking.navigation.Vec3

/**
 * RouteViewModel using manager classes for better separation of concerns.
 * Manages route loading, coordinates managers, and maintains state.
 */
class RouteViewModel : ViewModel() {
    
    /**
     * Filter landmarks for route matching - prioritize Entry over Office normally,
     * but prioritize Office for final destination steps
     */
    private fun filterLandmarksForMatching(landmarks: List<RouteLandmarkData>, isFinalStep: Boolean = false): List<RouteLandmarkData> {
        if (landmarks.size <= 1) return landmarks
        
        val hasEntry = landmarks.any { it.type == "Entry" }
        val hasOffice = landmarks.any { it.type == "Office" }
        
        return when {
            hasEntry && hasOffice -> {
                if (isFinalStep) {
                    // For final step: Keep Office for destination, exclude Entry
                    val filtered = landmarks.filter { it.type != "Entry" }
                    Log.d(TAG, "Final step landmarks: kept ${filtered.size} (Office), removed Entry")
                    filtered
                } else {
                    // For regular steps: Keep Entry for navigation, exclude Office from CV matching
                    val filtered = landmarks.filter { it.type != "Office" }
                    Log.d(TAG, "Regular step landmarks: kept ${filtered.size} (Entry), removed Office")
                    filtered
                }
            }
            else -> landmarks // Keep all other combinations
        }
    }
    
    /**
     * Merge the final two steps into one destination step with combined instruction
     */
    private fun mergeFinalSteps(steps: List<NavigationStep>): List<NavigationStep> {
        if (steps.size < 2) return steps
        
        val regularSteps = steps.dropLast(2)
        val secondLastStep = steps[steps.size - 2]
        val lastStep = steps[steps.size - 1]
        
        // Combine landmarks from both steps, prioritizing Office for final destination
        val allLandmarks = secondLastStep.landmarks + lastStep.landmarks
        val finalLandmarks = filterLandmarksForMatching(allLandmarks, isFinalStep = true)
        
        // Create merged final step
        val mergedFinalStep = NavigationStep(
            stepNumber = regularSteps.size,
            instruction = "${secondLastStep.instruction} ZIEL ERREICHT!",
            building = lastStep.building,
            floor = lastStep.floor,
            landmarks = finalLandmarks,
            distance = secondLastStep.distance + lastStep.distance,
            expectedWalkDistance = secondLastStep.expectedWalkDistance + lastStep.expectedWalkDistance,
            estimatedTime = secondLastStep.estimatedTime + lastStep.estimatedTime
        )
        
        Log.d(TAG, "Merged final steps: '${secondLastStep.instruction}' + '${lastStep.instruction}' -> '${mergedFinalStep.instruction}'")
        Log.d(TAG, "Final step landmarks: ${finalLandmarks.map { "${it.id} (${it.type})" }}")
        
        return regularSteps + listOf(mergedFinalStep)
    }
    
    /**
     * Merge office exit steps with immediate turn instructions
     * Skip the "leave office" step and go directly to the turn instruction
     */
    private fun mergeExitWithTurnSteps(steps: List<NavigationStep>): List<NavigationStep> {
        val optimizedSteps = mutableListOf<NavigationStep>()
        var i = 0
        
        while (i < steps.size) {
            val currentStep = steps[i]
            val nextStep = if (i + 1 < steps.size) steps[i + 1] else null
            
            // Check if current step is "leave office" and next step is a turn
            if (isExitOfficeStep(currentStep) && nextStep != null && isTurnStep(nextStep)) {
                // Create merged step: skip exit, use turn instruction with office landmarks
                val mergedStep = NavigationStep(
                    stepNumber = optimizedSteps.size,
                    instruction = nextStep.instruction, // Use turn instruction
                    building = currentStep.building,
                    floor = currentStep.floor,
                    landmarks = currentStep.landmarks, // Keep office/entry landmarks for initial reference
                    distance = currentStep.distance + nextStep.distance,
                    expectedWalkDistance = currentStep.expectedWalkDistance + nextStep.expectedWalkDistance,
                    estimatedTime = currentStep.estimatedTime + nextStep.estimatedTime
                )
                
                Log.d(TAG, "Merged exit+turn: '${currentStep.instruction}' + '${nextStep.instruction}' -> '${mergedStep.instruction}'")
                Log.d(TAG, "Merged step landmarks: ${mergedStep.landmarks.map { "${it.id} (${it.type})" }}")
                
                optimizedSteps.add(mergedStep)
                i += 2 // Skip both current and next step
            } else {
                // Re-number step
                optimizedSteps.add(currentStep.copy(stepNumber = optimizedSteps.size))
                i++
            }
        }
        
        return optimizedSteps
    }
    
    /**
     * Check if step is an office exit instruction
     */
    private fun isExitOfficeStep(step: NavigationStep): Boolean {
        return step.instruction.contains("Verlassen Sie das", ignoreCase = true) && 
               step.instruction.contains("Büro", ignoreCase = true)
    }
    
    /**
     * Check if step is a turn instruction
     */
    private fun isTurnStep(step: NavigationStep): Boolean {
        return step.instruction.contains("Biegen Sie", ignoreCase = true) && 
               (step.instruction.contains("links", ignoreCase = true) || 
                step.instruction.contains("rechts", ignoreCase = true))
    }
    
    /**
     * Add synthetic landmarks for turn instructions to bridge the gap
     * between abstract turn commands and concrete landmarks
     */
    private fun addSyntheticTurnLandmarks(steps: List<NavigationStep>): List<NavigationStep> {
        val enhancedSteps = mutableListOf<NavigationStep>()
        
        for (i in steps.indices) {
            val currentStep = steps[i]
            val nextStep = if (i + 1 < steps.size) steps[i + 1] else null
            
            // Check if current step is a turn instruction without landmarks
            if (isTurnStep(currentStep) && currentStep.landmarks.isEmpty() && nextStep != null && nextStep.landmarks.isNotEmpty()) {
                // Get the first landmark from the next step
                val nextLandmark = nextStep.landmarks.first()
                val turnDirection = getTurnDirection(currentStep.instruction)
                
                if (turnDirection != null) {
                    // Create synthetic landmark ID
                    val syntheticLandmarkId = "${nextLandmark.id}_${turnDirection}"
                    
                    // Create synthetic landmark data
                    val syntheticLandmark = RouteLandmarkData(
                        id = syntheticLandmarkId,
                        name = "Turn ${turnDirection.lowercase()} to ${nextLandmark.name}",
                        x = nextLandmark.x,
                        y = nextLandmark.y,
                        type = "TurnReference"
                    )
                    
                    // Add enhanced step with synthetic landmark
                    val enhancedStep = currentStep.copy(
                        landmarks = listOf(syntheticLandmark)
                    )
                    
                    Log.d(TAG, "Added synthetic landmark '${syntheticLandmarkId}' for turn instruction: '${currentStep.instruction}'")
                    enhancedSteps.add(enhancedStep)
                } else {
                    enhancedSteps.add(currentStep)
                }
            } else {
                enhancedSteps.add(currentStep)
            }
        }
        
        return enhancedSteps
    }
    
    /**
     * Extract turn direction from instruction text
     */
    private fun getTurnDirection(instruction: String): String? {
        return when {
            instruction.contains("links", ignoreCase = true) -> "L"
            instruction.contains("rechts", ignoreCase = true) -> "R"
            else -> null
        }
    }

    companion object {
        private const val TAG = "RouteViewModel"
        private const val DEFAULT_STRIDE_M = 0.65
        // Disabled step-based speed calculation constants
        // private const val SPEED_EMA_ALPHA = 0.3f
    }
    // Manager instances
    private val positionTrackingManager = PositionTrackingManager()
    private val stepProgressionManager = StepProgressionManager()
    private val landmarkMatchingManager = LandmarkMatchingManager()
    private val smartStepProgressionManager = SmartStepProgressionManager()
    
    // Schrittbasierte Geschwindigkeit deaktiviert
    // private val _pedometerSpeedMps = MutableStateFlow(1.0f)
    // val pedometerSpeedMps: StateFlow<Float> = _pedometerSpeedMps.asStateFlow()
    // private var lastStepEventTimeMs: Long? = null

    private val stepSensorManager = StepSensorManager { stepsDelta ->
        // Convert steps to distance and feed into smart progression
        val deltaMeters = (stepsDelta * DEFAULT_STRIDE_M).toFloat()
        smartStepProgressionManager.addExternalDistance(deltaMeters)
        
        // Schrittbasierte Geschwindigkeitsableitung deaktiviert
        // val now = System.currentTimeMillis()
        // val last = lastStepEventTimeMs
        // if (last != null) {
        //     val dtSec = ((now - last).coerceAtLeast(1L)) / 1000f
        //     val instSpeed = (stepsDelta * DEFAULT_STRIDE_M) / dtSec
        //     val ema = SPEED_EMA_ALPHA * instSpeed + (1 - SPEED_EMA_ALPHA) * _pedometerSpeedMps.value
        //     _pedometerSpeedMps.value = ema.coerceIn(0f, 3.0f)
        // }
        // lastStepEventTimeMs = now

        Log.d(TAG, "Pedometer: +$stepsDelta steps (~${String.format("%.2f", deltaMeters)}m)")
    }
    
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
            val rawSteps = pathItem.routeParts.mapIndexed { index, part ->
                // Calculate expected walking distance from edge lengths
                val walkDistance = part.nodes?.sumOf { nodeWrapper -> 
                    nodeWrapper.edge?.lengthInMeters?.toDoubleOrNull() ?: 0.0 
                } ?: 0.0
                
                // Filter landmarks: prefer Entry over Office when both are present (except for final step)
                val filteredLandmarks = filterLandmarksForMatching(part.landmarks, isFinalStep = false)
                
                Log.d(TAG, "Step $index: ${part.instructionDe} - Expected distance: ${"%.2f".format(walkDistance)}m (${part.landmarks.size} landmarks -> ${filteredLandmarks.size} filtered)")
                
                NavigationStep(
                    stepNumber = index,
                    instruction = part.instructionDe,
                    building = pathItem.xmlName,
                    floor = pathItem.levelInfo?.storey?.toIntOrNull() ?: 0,
                    landmarks = filteredLandmarks,
                    distance = part.distance ?: 0.0,
                    expectedWalkDistance = walkDistance,
                    estimatedTime = part.duration ?: 0
                )
            }
            
            // Apply step optimizations
            val optimizedSteps = mergeExitWithTurnSteps(rawSteps)
            val stepsWithSyntheticLandmarks = addSyntheticTurnLandmarks(optimizedSteps)
            val steps = mergeFinalSteps(stepsWithSyntheticLandmarks)

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
                    
                    // Try to align current step to matched landmark if within a small window (<= 2 steps ahead)
                    tryAlignStepToMatchedLandmark(convertedMatches)
                    
                    // Smart step progression with landmark + distance tracking
                    // This will be called from updatePosition with actual ARCore position
                    
                    // Debug: Log matches
                    if (convertedMatches.isNotEmpty()) {
                        val best = convertedMatches.maxByOrNull { it.confidence }!!
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
            // Schrittbasierter Fallback für RemainingDistance deaktiviert
            val currentRouteVal = _currentRoute.value
            val stepIdx = _currentStep.value
            // val fallbackRemaining: Float? = if (currentRouteVal != null && stepIdx < currentRouteVal.steps.size) {
            //     val step = currentRouteVal.steps[stepIdx]
            //     val stepProgressM = smartStepProgressionManager.getCurrentStepProgress()
            //     (step.expectedWalkDistance.toFloat() - stepProgressM).coerceAtLeast(0f)
            // } else null

            positionTrackingManager.updatePosition(
                frame = frame,
                currentStep = stepIdx,
                currentRoute = currentRouteVal,
                hasLandmarkMatches = _currentMatches.value.isNotEmpty(),
                matchedLandmarkIds = _currentMatches.value.map { it.landmark.id },
                measuredSpeedMps = 1.0f,
                fallbackRemainingDistanceMeters = null
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
            
            // Auto-Advance reaktiviert: aber nur bei Landmark-Erkennung wird weitergeschaltet
            val progressionResult = smartStepProgressionManager.analyzeStepProgression(
                currentStep = _currentStep.value,
                currentRoute = _currentRoute.value,
                landmarkMatches = _currentMatches.value,
                currentPosition = currentPosition
            )
            
            when (progressionResult) {
                is ProgressionResult.AdvanceToStep -> {
                    val isLandmarkBased = progressionResult.reason.contains("Landmark", ignoreCase = true)
                    if (isLandmarkBased) {
                        val currentStep = _currentStep.value
                        _currentStep.value = progressionResult.newStep
                        _completedSteps.value = _completedSteps.value + currentStep
                        _navigationStatus.value = NavigationStatus.STEP_COMPLETED
                        smartStepProgressionManager.onStepAdvanced(progressionResult.newStep)
                        
                        Log.d(TAG, "✅ Step advanced (landmark): $currentStep -> ${progressionResult.newStep} (${progressionResult.reason})")
                        
                        // Log distance stats
                        val stats = smartStepProgressionManager.getDistanceStats()
                        Log.d(TAG, "📊 Distance stats: step=${String.format("%.2f", stats.currentStepDistance)}m, total=${String.format("%.2f", stats.totalDistance)}m")
                    } else {
                        Log.v(TAG, "⏭️ Auto-advance ignored (non-landmark reason): ${progressionResult.reason}")
                    }
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
        
        // Start pedometer fallback for distance tracking
        try {
            stepSensorManager.start(context)
            Log.d(TAG, "Step sensor started")
        } catch (e: Exception) {
            Log.w(TAG, "Step sensor unavailable: ${e.message}")
        }
        
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

    private fun tryAlignStepToMatchedLandmark(matches: List<LandmarkMatch>) {
        if (matches.isEmpty()) return
        val route = _currentRoute.value ?: return
        val current = _currentStep.value
        val threshold = 0.60f
        val best = matches.maxByOrNull { it.confidence } ?: return
        if (best.confidence < threshold) return
        val targetIndex = route.steps.indexOfFirst { step -> step.landmarks.any { it.id == best.landmark.id } }
        if (targetIndex < 0) return
        if (targetIndex <= current) return // don't jump backwards
        if (targetIndex > current + 2) return // prevent large jumps (possible mismatch)

        // Advance to the matched step
        val prev = current
        _currentStep.value = targetIndex
        _completedSteps.value = _completedSteps.value + (prev until targetIndex).toSet()
        _navigationStatus.value = NavigationStatus.STEP_COMPLETED
        smartStepProgressionManager.onStepAdvanced(targetIndex)
        Log.d(TAG, "🔀 Aligned to step $targetIndex due to landmark ${best.landmark.id} (${(best.confidence*100).toInt()}%)")
    }

    override fun onCleared() {
        super.onCleared()
        
        // Clean up managers
        try {
            positionTrackingManager.cleanup()
            landmarkMatchingManager.cleanup()
            stepSensorManager.stop()
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