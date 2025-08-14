package com.example.arwalking.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.opencv.core.Mat
import com.example.arwalking.domain.ARNavigationEngine
import com.example.arwalking.data.*
import com.example.arwalking.ar.ArrowPose
import com.google.ar.core.Frame

/**
 * ARNavigationViewModel - ViewModel for AR navigation functionality
 * Integrates with the existing UI and provides reactive state management
 */
class ARNavigationViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "ARNavigationViewModel"
    }
    
    private var arNavigationEngine: ARNavigationEngine? = null
    
    // UI State
    private val _uiState = MutableStateFlow(ARNavigationUIState())
    val uiState: StateFlow<ARNavigationUIState> = _uiState.asStateFlow()
    
    // Navigation State
    private val _currentStep = MutableStateFlow<NavigationStep?>(null)
    val currentStep: StateFlow<NavigationStep?> = _currentStep.asStateFlow()
    
    // AR State
    private val _arrowPose = MutableStateFlow<ArrowPose?>(null)
    val arrowPose: StateFlow<ArrowPose?> = _arrowPose.asStateFlow()
    
    // Debug/Dev State
    private val _debugInfo = MutableStateFlow(DebugInfo())
    val debugInfo: StateFlow<DebugInfo> = _debugInfo.asStateFlow()
    
    /**
     * Initialize the AR navigation system
     */
    fun initialize(context: Context, config: ARNavigationConfig = ARNavigationConfig()) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Initializing AR Navigation System...")
                
                _uiState.value = _uiState.value.copy(
                    isInitializing = true,
                    initializationMessage = "Starting initialization..."
                )
                
                arNavigationEngine = ARNavigationEngine(context, config).apply {
                    // Set up callbacks
                    setOnStepChangeCallback { step ->
                        _currentStep.value = step
                        _uiState.value = _uiState.value.copy(
                            currentInstruction = step.instructionDe,
                            currentStepIndex = getCurrentStepIndex(),
                            totalSteps = getTotalSteps()
                        )
                    }
                    
                    setOnArrowPoseUpdateCallback { direction, pose ->
                        _arrowPose.value = pose
                        _uiState.value = _uiState.value.copy(
                            arrowDirection = direction,
                            isArrowVisible = pose?.isVisible ?: false
                        )
                    }
                }
                
                // Initialize the engine
                val success = arNavigationEngine!!.initialize()
                
                if (success) {
                    // Observe engine state
                    observeEngineState()
                    
                    _uiState.value = _uiState.value.copy(
                        isInitialized = true,
                        isInitializing = false,
                        initializationMessage = "Initialization complete"
                    )
                    
                    Log.d(TAG, "AR Navigation System initialized successfully")
                } else {
                    throw Exception("Engine initialization failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AR Navigation System", e)
                _uiState.value = _uiState.value.copy(
                    isInitialized = false,
                    isInitializing = false,
                    error = "Initialization failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Load a navigation route
     */
    fun loadRoute(routeFilename: String) {
        val engine = arNavigationEngine ?: return
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoadingRoute = true,
                    loadingMessage = "Loading route..."
                )
                
                val success = engine.loadRoute(routeFilename)
                
                if (success) {
                    _uiState.value = _uiState.value.copy(
                        isRouteLoaded = true,
                        isLoadingRoute = false,
                        loadingMessage = "Route loaded successfully",
                        currentStepIndex = 0,
                        totalSteps = getTotalSteps()
                    )
                    
                    Log.d(TAG, "Route loaded: $routeFilename")
                } else {
                    throw Exception("Failed to load route")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load route: $routeFilename", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingRoute = false,
                    error = "Failed to load route: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Start AR session
     */
    fun startARSession() {
        val engine = arNavigationEngine ?: return
        
        val success = engine.startARSession()
        _uiState.value = _uiState.value.copy(
            isARSessionActive = success,
            error = if (!success) "Failed to start AR session" else null
        )
        
        if (success) {
            Log.d(TAG, "AR session started")
        }
    }
    
    /**
     * Pause AR session
     */
    fun pauseARSession() {
        arNavigationEngine?.pauseARSession()
        _uiState.value = _uiState.value.copy(isARSessionActive = false)
        Log.d(TAG, "AR session paused")
    }
    
    /**
     * Process camera frame for landmark recognition
     */
    fun processFrame(frame: Mat) {
        val engine = arNavigationEngine ?: return
        
        viewModelScope.launch {
            engine.processFrame(frame)
        }
    }
    
    /**
     * Update AR session and get current frame
     */
    fun updateARSession(): Frame? {
        return arNavigationEngine?.updateARSession()
    }
    
    /**
     * Handle screen tap for arrow placement
     */
    fun onScreenTap(frame: Frame, x: Float, y: Float) {
        arNavigationEngine?.onScreenTap(frame, x, y)
    }
    
    /**
     * Manually advance to next step
     */
    fun nextStep() {
        val success = arNavigationEngine?.advanceToNextStep() ?: false
        if (success) {
            _uiState.value = _uiState.value.copy(
                currentStepIndex = getCurrentStepIndex()
            )
        }
    }
    
    /**
     * Manually go to previous step
     */
    fun previousStep() {
        val success = arNavigationEngine?.goToPreviousStep() ?: false
        if (success) {
            _uiState.value = _uiState.value.copy(
                currentStepIndex = getCurrentStepIndex()
            )
        }
    }
    
    /**
     * Jump to specific step
     */
    fun jumpToStep(stepIndex: Int) {
        val success = arNavigationEngine?.jumpToStep(stepIndex) ?: false
        if (success) {
            _uiState.value = _uiState.value.copy(
                currentStepIndex = getCurrentStepIndex()
            )
        }
    }
    
    /**
     * Reset navigation
     */
    fun resetNavigation() {
        arNavigationEngine?.resetNavigation()
        
        _currentStep.value = null
        _arrowPose.value = null
        _uiState.value = _uiState.value.copy(
            currentInstruction = "",
            currentStepIndex = 0,
            isArrowVisible = false,
            arrowDirection = ArrowDirection.FORWARD
        )
        
        Log.d(TAG, "Navigation reset")
    }
    
    /**
     * Get available routes
     */
    fun getAvailableRoutes(callback: (List<String>) -> Unit) {
        val engine = arNavigationEngine ?: return
        
        viewModelScope.launch {
            val routes = engine.getAvailableRoutes()
            callback(routes)
        }
    }
    
    /**
     * Toggle debug overlay
     */
    fun toggleDebugOverlay() {
        _debugInfo.value = _debugInfo.value.copy(
            isVisible = !_debugInfo.value.isVisible
        )
    }
    
    /**
     * Update debug information
     */
    private fun updateDebugInfo() {
        val engine = arNavigationEngine ?: return
        
        viewModelScope.launch {
            val navStats = engine.getNavigationStats()
            val perfMetrics = engine.getPerformanceMetrics()
            
            _debugInfo.value = _debugInfo.value.copy(
                navigationStats = navStats,
                performanceMetrics = perfMetrics,
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Observe engine state changes
     */
    private fun observeEngineState() {
        val engine = arNavigationEngine ?: return
        
        viewModelScope.launch {
            engine.engineState.collect { engineState ->
                _uiState.value = _uiState.value.copy(
                    isInitializing = engineState.isInitializing,
                    initializationMessage = engineState.initializationMessage,
                    isLoadingRoute = engineState.isLoadingRoute,
                    loadingMessage = engineState.loadingMessage,
                    isRouteLoaded = engineState.isRouteLoaded,
                    error = engineState.error
                )
            }
        }
        
        viewModelScope.launch {
            engine.navigationStatus.collect { navStatus ->
                _uiState.value = _uiState.value.copy(
                    currentLandmarkId = navStatus.currentLandmarkId,
                    matchConfidence = navStatus.matchConfidence,
                    isTracking = navStatus.isTracking,
                    trackingQuality = navStatus.trackingQuality
                )
                
                // Update debug info periodically
                if (System.currentTimeMillis() - _debugInfo.value.lastUpdateTime > 1000) {
                    updateDebugInfo()
                }
            }
        }
    }
    
    /**
     * Get current step index
     */
    private fun getCurrentStepIndex(): Int {
        return arNavigationEngine?.getNavigationStats()?.completedSteps?.minus(1) ?: 0
    }
    
    /**
     * Get total steps
     */
    private fun getTotalSteps(): Int {
        return arNavigationEngine?.getNavigationStats()?.totalSteps ?: 0
    }
    
    /**
     * Clean up resources
     */
    override fun onCleared() {
        super.onCleared()
        arNavigationEngine?.release()
        Log.d(TAG, "ARNavigationViewModel cleared")
    }
}

/**
 * UI State for AR Navigation
 */
data class ARNavigationUIState(
    // Initialization
    val isInitialized: Boolean = false,
    val isInitializing: Boolean = false,
    val initializationMessage: String = "",
    
    // Route Loading
    val isRouteLoaded: Boolean = false,
    val isLoadingRoute: Boolean = false,
    val loadingMessage: String = "",
    
    // AR Session
    val isARSessionActive: Boolean = false,
    
    // Navigation
    val currentInstruction: String = "",
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentLandmarkId: String? = null,
    val matchConfidence: Float = 0f,
    
    // AR Arrow
    val isArrowVisible: Boolean = false,
    val arrowDirection: ArrowDirection = ArrowDirection.FORWARD,
    
    // Tracking
    val isTracking: Boolean = false,
    val trackingQuality: TrackingQuality = TrackingQuality.NONE,
    
    // Error handling
    val error: String? = null
) {
    val progress: Float get() = if (totalSteps > 0) currentStepIndex.toFloat() / totalSteps else 0f
    val isNavigating: Boolean get() = isRouteLoaded && isARSessionActive
    val hasError: Boolean get() = error != null
}

/**
 * Debug information for development overlay
 */
data class DebugInfo(
    val isVisible: Boolean = false,
    val navigationStats: NavigationStats? = null,
    val performanceMetrics: PerformanceMetrics? = null,
    val lastUpdateTime: Long = 0
)