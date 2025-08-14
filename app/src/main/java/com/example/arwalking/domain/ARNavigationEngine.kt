package com.example.arwalking.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.opencv.core.Mat
import com.example.arwalking.data.*
import com.example.arwalking.vision.*
import com.example.arwalking.ar.ArrowPlacer
import com.google.ar.core.Frame
import com.google.ar.core.HitResult

/**
 * ARNavigationEngine - Main coordinator for the AR navigation system
 * Integrates all components: route loading, landmark recognition, navigation, and AR rendering
 */
class ARNavigationEngine(
    private val context: Context,
    private val config: ARNavigationConfig = ARNavigationConfig()
) {
    
    companion object {
        private const val TAG = "ARNavigationEngine"
    }
    
    // Core components
    private val routeLoader = RouteLoader(context)
    private val landmarkStore = LandmarkStore(context, config)
    private val featureCache = FeatureCache(context, config)
    private val featureEngine = FeatureEngine(config)
    private val candidateSelector = CandidateSelector(config, landmarkStore)
    private val landmarkRecognizer = LandmarkRecognizer(config, featureEngine, candidateSelector, featureCache)
    private val navigator = Navigator(config)
    private val arrowPlacer = ArrowPlacer(context, config)
    
    // State flows
    private val _engineState = MutableStateFlow(EngineState())
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()
    
    private val _navigationStatus = MutableStateFlow(NavigationStatus())
    val navigationStatus: StateFlow<NavigationStatus> = _navigationStatus.asStateFlow()
    
    // Coroutine scope for engine operations
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Callbacks
    private var onStepChangeCallback: ((NavigationStep) -> Unit)? = null
    private var onArrowPoseUpdateCallback: ((ArrowDirection, com.example.arwalking.ar.ArrowPose?) -> Unit)? = null
    
    init {
        setupStateObservers()
    }
    
    /**
     * Initialize the AR navigation engine
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        try {
            Log.d(TAG, "Initializing AR Navigation Engine...")
            
            _engineState.value = _engineState.value.copy(
                isInitializing = true,
                initializationProgress = 0f
            )
            
            // Initialize ARCore
            updateInitializationProgress(0.1f, "Initializing ARCore...")
            val arInitialized = arrowPlacer.initializeAR()
            if (!arInitialized) {
                throw Exception("Failed to initialize ARCore")
            }
            
            // Initialize OpenCV (assumed to be done in MainActivity)
            updateInitializationProgress(0.3f, "OpenCV initialized")
            
            // Start landmark recognizer
            updateInitializationProgress(0.5f, "Starting recognition engine...")
            landmarkRecognizer.start()
            
            updateInitializationProgress(1.0f, "Initialization complete")
            
            _engineState.value = _engineState.value.copy(
                isInitialized = true,
                isInitializing = false,
                initializationProgress = 1f,
                initializationMessage = "Ready"
            )
            
            Log.d(TAG, "AR Navigation Engine initialized successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AR Navigation Engine", e)
            _engineState.value = _engineState.value.copy(
                isInitialized = false,
                isInitializing = false,
                error = "Initialization failed: ${e.message}"
            )
            false
        }
    }
    
    /**
     * Load and start navigation with a route
     */
    suspend fun loadRoute(routeFilename: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading route: $routeFilename")
            
            _engineState.value = _engineState.value.copy(
                isLoadingRoute = true,
                loadingProgress = 0f
            )
            
            // Load route data
            updateLoadingProgress(0.1f, "Loading route data...")
            val route = routeLoader.loadRoute(routeFilename)
            if (route == null) {
                throw Exception("Failed to load route from $routeFilename")
            }
            
            // Validate landmark mapping
            updateLoadingProgress(0.2f, "Validating landmarks...")
            val validation = routeLoader.validateLandmarkMapping(route)
            if (!validation.isValid) {
                Log.w(TAG, "Landmark mapping validation warnings: ${validation.conflicts}")
            }
            
            // Load landmark images
            updateLoadingProgress(0.3f, "Loading landmark images...")
            val loadResult = landmarkStore.loadLandmarks(route.landmarkIds)
            if (!loadResult.isSuccess && !loadResult.hasWarnings) {
                throw Exception("Failed to load landmark images: ${loadResult.errors}")
            }
            
            // Precompute features for landmarks
            updateLoadingProgress(0.5f, "Computing features...")
            val featureStats = precomputeFeatures(route.landmarkIds)
            
            // Load route into navigator
            updateLoadingProgress(0.8f, "Initializing navigation...")
            navigator.loadRoute(route)
            
            updateLoadingProgress(1.0f, "Route loaded successfully")
            
            _engineState.value = _engineState.value.copy(
                isLoadingRoute = false,
                isRouteLoaded = true,
                currentRoute = route,
                loadingProgress = 1f,
                loadingMessage = "Route loaded: ${route.name}",
                landmarkStats = featureStats
            )
            
            Log.d(TAG, "Route loaded successfully: ${route.name} with ${route.steps.size} steps")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load route", e)
            _engineState.value = _engineState.value.copy(
                isLoadingRoute = false,
                error = "Failed to load route: ${e.message}"
            )
            false
        }
    }
    
    /**
     * Start AR session
     */
    fun startARSession(): Boolean {
        return arrowPlacer.startSession()
    }
    
    /**
     * Pause AR session
     */
    fun pauseARSession() {
        arrowPlacer.pauseSession()
    }
    
    /**
     * Process camera frame for landmark recognition
     */
    suspend fun processFrame(frame: Mat) {
        if (!_engineState.value.isInitialized || !_engineState.value.isRouteLoaded) {
            return
        }
        
        val currentStep = navigator.currentStep.value
        val relevantLandmarks = navigator.getRelevantLandmarks()
        
        landmarkRecognizer.processFrame(frame, relevantLandmarks, currentStep)
    }
    
    /**
     * Update AR session and handle arrow placement
     */
    fun updateARSession(): Frame? {
        val frame = arrowPlacer.updateSession()
        
        // Update navigation status with AR state
        val arState = arrowPlacer.getARState()
        _navigationStatus.value = _navigationStatus.value.copy(
            isTracking = arState.isSessionActive && arState.trackingQuality != TrackingQuality.NONE,
            trackingQuality = arState.trackingQuality
        )
        
        return frame
    }
    
    /**
     * Handle hit test for arrow placement
     */
    fun onScreenTap(frame: Frame, x: Float, y: Float) {
        if (!_engineState.value.isRouteLoaded) return
        
        val hitResults = arrowPlacer.hitTest(frame, x, y)
        if (hitResults.isNotEmpty()) {
            val currentStep = navigator.currentStep.value
            val direction = currentStep?.arrowDirection ?: ArrowDirection.FORWARD
            
            arrowPlacer.placeArrow(hitResults.first(), direction)
        }
    }
    
    /**
     * Manually advance to next step
     */
    fun advanceToNextStep(): Boolean {
        return navigator.advanceToNextStep()
    }
    
    /**
     * Manually go to previous step
     */
    fun goToPreviousStep(): Boolean {
        return navigator.goToPreviousStep()
    }
    
    /**
     * Jump to specific step
     */
    fun jumpToStep(stepIndex: Int): Boolean {
        return navigator.jumpToStep(stepIndex)
    }
    
    /**
     * Set callback for step changes
     */
    fun setOnStepChangeCallback(callback: (NavigationStep) -> Unit) {
        onStepChangeCallback = callback
    }
    
    /**
     * Set callback for arrow pose updates
     */
    fun setOnArrowPoseUpdateCallback(callback: (ArrowDirection, com.example.arwalking.ar.ArrowPose?) -> Unit) {
        onArrowPoseUpdateCallback = callback
    }
    
    /**
     * Get current navigation statistics
     */
    fun getNavigationStats(): NavigationStats {
        return navigator.getNavigationStats()
    }
    
    /**
     * Get performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return landmarkRecognizer.getPerformanceMetrics()
    }
    
    /**
     * Get available routes
     */
    suspend fun getAvailableRoutes(): List<String> {
        return routeLoader.getAvailableRoutes()
    }
    
    /**
     * Reset navigation state
     */
    fun resetNavigation() {
        navigator.reset()
        landmarkRecognizer.reset()
        arrowPlacer.removeArrow()
        candidateSelector.reset()
        
        _navigationStatus.value = NavigationStatus()
        
        Log.d(TAG, "Navigation state reset")
    }
    
    /**
     * Release all resources
     */
    fun release() {
        engineScope.cancel()
        
        landmarkRecognizer.stop()
        arrowPlacer.release()
        featureEngine.release()
        landmarkStore.clear()
        
        _engineState.value = EngineState()
        _navigationStatus.value = NavigationStatus()
        
        Log.d(TAG, "AR Navigation Engine released")
    }
    
    /**
     * Setup state observers
     */
    private fun setupStateObservers() {
        // Observe landmark recognition results
        engineScope.launch {
            landmarkRecognizer.currentMatch.collect { matchResult ->
                navigator.updateWithLandmarkMatch(matchResult)
                
                _navigationStatus.value = _navigationStatus.value.copy(
                    currentLandmarkId = matchResult?.landmarkId,
                    matchConfidence = matchResult?.confidence ?: 0f
                )
            }
        }
        
        // Observe navigation step changes
        engineScope.launch {
            navigator.currentStep.collect { step ->
                _navigationStatus.value = _navigationStatus.value.copy(
                    currentStepId = step?.id
                )
                
                // Update arrow direction when step changes
                if (step != null) {
                    updateArrowForStep(step)
                    onStepChangeCallback?.invoke(step)
                }
            }
        }
        
        // Observe arrow pose updates
        engineScope.launch {
            arrowPlacer.arrowPose.collect { arrowPose ->
                val currentStep = navigator.currentStep.value
                val direction = currentStep?.arrowDirection ?: ArrowDirection.FORWARD
                onArrowPoseUpdateCallback?.invoke(direction, arrowPose)
            }
        }
        
        // Observe navigation state
        engineScope.launch {
            navigator.navigationState.collect { navState ->
                _navigationStatus.value = _navigationStatus.value.copy(
                    currentStepId = navState.currentStepId
                )
            }
        }
    }
    
    /**
     * Update arrow for current step
     */
    private fun updateArrowForStep(step: NavigationStep) {
        // If we have an existing arrow, update its direction
        if (arrowPlacer.arrowPose.value?.isVisible == true) {
            arrowPlacer.placeArrow(null, step.arrowDirection, forceReplace = false)
        }
    }
    
    /**
     * Precompute features for landmarks
     */
    private suspend fun precomputeFeatures(landmarkIds: Set<String>): LandmarkFeatureStats = withContext(Dispatchers.IO) {
        var totalImages = 0
        var cachedImages = 0
        var computedFeatures = 0
        
        landmarkIds.forEach { landmarkId ->
            val images = landmarkStore.getLandmarkImages(landmarkId)
            totalImages += images.size
            
            images.forEach { image ->
                if (featureCache.isCached(landmarkId, image.path)) {
                    cachedImages++
                } else {
                    // Compute and cache features
                    val features = featureEngine.extractFeatures(image.mat)
                    if (features != null) {
                        featureCache.cacheFeatures(landmarkId, image.path, features.keypoints, features.descriptors)
                        computedFeatures++
                        features.keypoints.release()
                        features.descriptors.release()
                    }
                }
            }
        }
        
        LandmarkFeatureStats(
            totalLandmarks = landmarkIds.size,
            totalImages = totalImages,
            cachedImages = cachedImages,
            computedFeatures = computedFeatures
        )
    }
    
    /**
     * Update initialization progress
     */
    private fun updateInitializationProgress(progress: Float, message: String) {
        _engineState.value = _engineState.value.copy(
            initializationProgress = progress,
            initializationMessage = message
        )
    }
    
    /**
     * Update loading progress
     */
    private fun updateLoadingProgress(progress: Float, message: String) {
        _engineState.value = _engineState.value.copy(
            loadingProgress = progress,
            loadingMessage = message
        )
    }
}

/**
 * Overall engine state
 */
data class EngineState(
    val isInitialized: Boolean = false,
    val isInitializing: Boolean = false,
    val initializationProgress: Float = 0f,
    val initializationMessage: String = "",
    val isRouteLoaded: Boolean = false,
    val isLoadingRoute: Boolean = false,
    val loadingProgress: Float = 0f,
    val loadingMessage: String = "",
    val currentRoute: NavigationRoute? = null,
    val landmarkStats: LandmarkFeatureStats? = null,
    val error: String? = null
)

/**
 * Landmark feature statistics
 */
data class LandmarkFeatureStats(
    val totalLandmarks: Int,
    val totalImages: Int,
    val cachedImages: Int,
    val computedFeatures: Int
) {
    val cacheHitRate: Float get() = if (totalImages > 0) cachedImages.toFloat() / totalImages else 0f
}