package com.example.arwalking.domain

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.arwalking.data.NavigationRoute
import com.example.arwalking.data.NavigationStep
import com.example.arwalking.data.ARNavigationConfig
import com.example.arwalking.data.MatchResult

/**
 * Navigator - Manages navigation state and step transitions
 * Handles landmark to step mapping and confidence-based step updates
 */
class Navigator(private val config: ARNavigationConfig) {
    
    companion object {
        private const val TAG = "Navigator"
    }
    
    private val _navigationState = MutableStateFlow(NavigationState())
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()
    
    private val _currentStep = MutableStateFlow<NavigationStep?>(null)
    val currentStep: StateFlow<NavigationStep?> = _currentStep.asStateFlow()
    
    private var currentRoute: NavigationRoute? = null
    private var stepIndex = 0
    
    // Confidence tracking for step transitions
    private val landmarkConfidenceHistory = mutableMapOf<String, MutableList<Float>>()
    private val maxConfidenceHistory = 10
    
    /**
     * Load a navigation route
     */
    fun loadRoute(route: NavigationRoute) {
        currentRoute = route
        stepIndex = 0
        
        val firstStep = route.steps.firstOrNull()
        _currentStep.value = firstStep
        
        _navigationState.value = NavigationState(
            routeId = route.id,
            routeName = route.name,
            totalSteps = route.steps.size,
            currentStepIndex = 0,
            currentStepId = firstStep?.id,
            isNavigating = true,
            progress = 0f
        )
        
        landmarkConfidenceHistory.clear()
        
        Log.d(TAG, "Loaded route: ${route.name} with ${route.steps.size} steps")
    }
    
    /**
     * Update navigation based on landmark recognition
     */
    fun updateWithLandmarkMatch(matchResult: MatchResult?) {
        val route = currentRoute ?: return
        
        if (matchResult != null) {
            updateLandmarkConfidence(matchResult.landmarkId, matchResult.confidence)
            
            // Check if this landmark indicates a step transition
            val targetStep = findStepForLandmark(matchResult.landmarkId)
            if (targetStep != null) {
                val shouldTransition = shouldTransitionToStep(targetStep, matchResult)
                
                if (shouldTransition) {
                    transitionToStep(targetStep)
                }
            }
        }
        
        // Update navigation state with current match info
        _navigationState.value = _navigationState.value.copy(
            currentLandmarkId = matchResult?.landmarkId,
            matchConfidence = matchResult?.confidence ?: 0f,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
    
    /**
     * Find the navigation step associated with a landmark
     */
    private fun findStepForLandmark(landmarkId: String): NavigationStep? {
        return currentRoute?.steps?.find { it.landmarkId == landmarkId }
    }
    
    /**
     * Determine if we should transition to a new step
     */
    private fun shouldTransitionToStep(targetStep: NavigationStep, matchResult: MatchResult): Boolean {
        val route = currentRoute ?: return false
        val currentStep = _currentStep.value
        
        // Don't transition to the same step
        if (currentStep?.id == targetStep.id) {
            return false
        }
        
        // Find target step index
        val targetIndex = route.steps.indexOfFirst { it.id == targetStep.id }
        if (targetIndex == -1) {
            return false
        }
        
        // Check confidence threshold
        if (matchResult.confidence < config.thresholds.promote) {
            return false
        }
        
        // Check if this is a reasonable step transition
        val currentIndex = stepIndex
        val stepDifference = targetIndex - currentIndex
        
        when {
            stepDifference == 1 -> {
                // Normal forward progression
                return true
            }
            stepDifference > 1 && stepDifference <= 3 -> {
                // Skip ahead (user might have moved quickly)
                Log.d(TAG, "Skipping ahead from step $currentIndex to $targetIndex")
                return matchResult.confidence > config.thresholds.promote + 0.1f
            }
            stepDifference < 0 && stepDifference >= -2 -> {
                // Going backwards (user might have backtracked)
                Log.d(TAG, "Going backwards from step $currentIndex to $targetIndex")
                return matchResult.confidence > config.thresholds.promote + 0.15f
            }
            else -> {
                // Large jump - require very high confidence
                Log.d(TAG, "Large step jump from $currentIndex to $targetIndex")
                return matchResult.confidence > 0.8f
            }
        }
    }
    
    /**
     * Transition to a new navigation step
     */
    private fun transitionToStep(targetStep: NavigationStep) {
        val route = currentRoute ?: return
        
        val targetIndex = route.steps.indexOfFirst { it.id == targetStep.id }
        if (targetIndex == -1) return
        
        val previousStep = _currentStep.value
        stepIndex = targetIndex
        _currentStep.value = targetStep
        
        val progress = if (route.steps.isNotEmpty()) {
            (targetIndex + 1).toFloat() / route.steps.size
        } else {
            0f
        }
        
        _navigationState.value = _navigationState.value.copy(
            currentStepIndex = targetIndex,
            currentStepId = targetStep.id,
            progress = progress,
            stepTransitionCount = _navigationState.value.stepTransitionCount + 1
        )
        
        Log.d(TAG, "Transitioned from step ${previousStep?.id} to ${targetStep.id} (index $targetIndex)")
        
        // Check if route is completed
        if (targetIndex >= route.steps.size - 1) {
            completeNavigation()
        }
    }
    
    /**
     * Update confidence history for a landmark
     */
    private fun updateLandmarkConfidence(landmarkId: String, confidence: Float) {
        val history = landmarkConfidenceHistory.getOrPut(landmarkId) { mutableListOf() }
        
        history.add(confidence)
        if (history.size > maxConfidenceHistory) {
            history.removeAt(0)
        }
    }
    
    /**
     * Get average confidence for a landmark
     */
    private fun getAverageConfidence(landmarkId: String): Float {
        val history = landmarkConfidenceHistory[landmarkId] ?: return 0f
        return if (history.isNotEmpty()) history.average().toFloat() else 0f
    }
    
    /**
     * Manually advance to next step
     */
    fun advanceToNextStep(): Boolean {
        val route = currentRoute ?: return false
        
        if (stepIndex < route.steps.size - 1) {
            val nextStep = route.steps[stepIndex + 1]
            transitionToStep(nextStep)
            return true
        }
        
        return false
    }
    
    /**
     * Manually go back to previous step
     */
    fun goToPreviousStep(): Boolean {
        val route = currentRoute ?: return false
        
        if (stepIndex > 0) {
            val previousStep = route.steps[stepIndex - 1]
            transitionToStep(previousStep)
            return true
        }
        
        return false
    }
    
    /**
     * Jump to a specific step by index
     */
    fun jumpToStep(index: Int): Boolean {
        val route = currentRoute ?: return false
        
        if (index in 0 until route.steps.size) {
            val targetStep = route.steps[index]
            transitionToStep(targetStep)
            return true
        }
        
        return false
    }
    
    /**
     * Complete the navigation
     */
    private fun completeNavigation() {
        _navigationState.value = _navigationState.value.copy(
            isNavigating = false,
            isCompleted = true,
            progress = 1f,
            completionTime = System.currentTimeMillis()
        )
        
        Log.d(TAG, "Navigation completed for route: ${currentRoute?.name}")
    }
    
    /**
     * Reset navigation state
     */
    fun reset() {
        currentRoute = null
        stepIndex = 0
        landmarkConfidenceHistory.clear()
        
        _currentStep.value = null
        _navigationState.value = NavigationState()
        
        Log.d(TAG, "Reset navigation state")
    }
    
    /**
     * Get navigation statistics
     */
    fun getNavigationStats(): NavigationStats {
        val state = _navigationState.value
        val route = currentRoute
        
        return NavigationStats(
            routeId = state.routeId,
            routeName = state.routeName,
            totalSteps = state.totalSteps,
            completedSteps = state.currentStepIndex + 1,
            progress = state.progress,
            stepTransitions = state.stepTransitionCount,
            navigationTimeMs = if (state.startTime > 0) {
                (state.completionTime ?: System.currentTimeMillis()) - state.startTime
            } else 0,
            landmarksRecognized = landmarkConfidenceHistory.size,
            avgLandmarkConfidence = landmarkConfidenceHistory.values
                .flatten()
                .takeIf { it.isNotEmpty() }
                ?.average()?.toFloat() ?: 0f
        )
    }
    
    /**
     * Get available landmarks for current context
     */
    fun getRelevantLandmarks(): Set<String> {
        val route = currentRoute ?: return emptySet()
        val currentIndex = stepIndex
        
        // Include landmarks from current step and next few steps
        val relevantSteps = route.steps.drop(currentIndex).take(3)
        
        return relevantSteps.mapNotNull { it.landmarkId }.toSet()
    }
    
    /**
     * Check if navigation is active
     */
    fun isNavigating(): Boolean {
        return _navigationState.value.isNavigating
    }
    
    /**
     * Get current route
     */
    fun getCurrentRoute(): NavigationRoute? {
        return currentRoute
    }
}

/**
 * Current navigation state
 */
data class NavigationState(
    val routeId: String? = null,
    val routeName: String? = null,
    val totalSteps: Int = 0,
    val currentStepIndex: Int = 0,
    val currentStepId: String? = null,
    val currentLandmarkId: String? = null,
    val matchConfidence: Float = 0f,
    val progress: Float = 0f,
    val isNavigating: Boolean = false,
    val isCompleted: Boolean = false,
    val stepTransitionCount: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val completionTime: Long? = null,
    val lastUpdateTime: Long = 0
)

/**
 * Navigation statistics
 */
data class NavigationStats(
    val routeId: String?,
    val routeName: String?,
    val totalSteps: Int,
    val completedSteps: Int,
    val progress: Float,
    val stepTransitions: Int,
    val navigationTimeMs: Long,
    val landmarksRecognized: Int,
    val avgLandmarkConfidence: Float
)