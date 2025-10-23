package com.example.arwalking.navigation

import android.util.Log
import com.example.arwalking.NavigationRoute
import com.example.arwalking.RouteViewModel

/**
 * Smart Step Progression Manager that combines landmark detection with ARCore distance tracking
 * for intelligent route advancement (no pedometer usage)
 */
class SmartStepProgressionManager {
    companion object {
        private const val TAG = "SmartStepProgressionManager"
        private const val LANDMARK_CONFIDENCE_THRESHOLD = 0.6f
        private const val DISTANCE_TOLERANCE_SMALL_STEP = 90f  // 90% für Schritte < 2m
        private const val DISTANCE_TOLERANCE_MEDIUM_STEP = 80f // 80% für Schritte 2-5m  
        private const val DISTANCE_TOLERANCE_LARGE_STEP = 70f  // 70% für Schritte > 5m
    }
    
    private val distanceTracker = DistanceTracker()

    /**
     * Strategy for step progression based on step characteristics
     */
    enum class ProgressionStrategy {
        WAIT_FOR_LANDMARK,     // Wait for next step's landmark
        LANDMARK_OR_DISTANCE,  // Either landmark or distance works
        DISTANCE_ONLY,         // Pure distance-based (no landmarks)
        HYBRID                 // Complex logic needed
    }
    
    /**
     * Analyze whether route should advance to next step
     * Combines landmark recognition with distance tracking
     */
    fun analyzeStepProgression(
        currentStep: Int,
        currentRoute: NavigationRoute?,
        landmarkMatches: List<RouteViewModel.LandmarkMatch>,
        currentPosition: Vec3
    ): ProgressionResult {
        
        if (currentRoute == null || currentStep >= currentRoute.steps.size) {
            return ProgressionResult.NoAdvance("Invalid route or step")
        }
        
        // Update distance tracking
        distanceTracker.updatePosition(currentPosition)
        
        val step = currentRoute.steps[currentStep]
        val nextStep = currentRoute.steps.getOrNull(currentStep + 1)
        val strategy = determineProgressionStrategy(step, nextStep)
        
        Log.d(TAG, "🔍 Step $currentStep progression analysis:")
        Log.d(TAG, "   Current: '${step.instruction}' (${step.landmarks.size} landmarks, ${String.format("%.2f", step.expectedWalkDistance)}m)")
        Log.d(TAG, "   Next: ${nextStep?.let { "'${it.instruction}' (${it.landmarks.size} landmarks)" } ?: "ROUTE_END"}")
        Log.d(TAG, "   Strategy: $strategy")
        
        return when (strategy) {
            ProgressionStrategy.WAIT_FOR_LANDMARK -> analyzeWaitForLandmark(currentStep, nextStep!!, landmarkMatches)
            ProgressionStrategy.LANDMARK_OR_DISTANCE -> analyzeLandmarkOrDistance(currentStep, step, nextStep, landmarkMatches)
            ProgressionStrategy.DISTANCE_ONLY -> analyzeDistanceOnly(currentStep, step)
            ProgressionStrategy.HYBRID -> analyzeHybrid(currentStep, step, nextStep, landmarkMatches)
        }
    }
    
    /**
     * Determine the best progression strategy based on current and next step characteristics
     */
    private fun determineProgressionStrategy(
        currentStep: com.example.arwalking.NavigationStep,
        nextStep: com.example.arwalking.NavigationStep?
    ): ProgressionStrategy {
        return when {
            // Next step has landmarks -> wait for landmark detection
            nextStep?.landmarks?.isNotEmpty() == true -> ProgressionStrategy.WAIT_FOR_LANDMARK
            
            // Current step has landmarks, next doesn't -> hybrid approach
            currentStep.landmarks.isNotEmpty() && nextStep?.landmarks?.isEmpty() == true -> 
                ProgressionStrategy.LANDMARK_OR_DISTANCE
                
            // Both have no landmarks -> pure distance
            currentStep.landmarks.isEmpty() && nextStep?.landmarks?.isEmpty() != false -> 
                ProgressionStrategy.DISTANCE_ONLY
                
            else -> ProgressionStrategy.HYBRID
        }
    }
    
    /**
     * Wait for next step's landmark to be detected
     */
    private fun analyzeWaitForLandmark(
        currentStep: Int,
        nextStep: com.example.arwalking.NavigationStep,
        landmarkMatches: List<RouteViewModel.LandmarkMatch>
    ): ProgressionResult {
        // Only consider a tight window: current and next step
        val nextStepLandmarkIds = nextStep.landmarks.map { it.id }
        val nextStepMatch = landmarkMatches.find { match ->
            nextStepLandmarkIds.contains(match.landmark.id) &&
            match.confidence >= LANDMARK_CONFIDENCE_THRESHOLD
        }

        Log.d(TAG, "🎯 WAIT_FOR_LANDMARK: Looking for ${nextStepLandmarkIds}")

        if (nextStepMatch != null) {
            Log.d(TAG, "✅ Next step landmark detected: ${nextStepMatch.landmark.id} (${(nextStepMatch.confidence * 100).toInt()}%)")
            return ProgressionResult.AdvanceToStep(
                newStep = currentStep + 1,
                reason = "Landmark detected: ${nextStepMatch.landmark.id} (${(nextStepMatch.confidence * 100).toInt()}%)"
            )
        }

        // No advance if only current/other landmarks are seen; continue waiting for next-step ID
        // Fallback: If we've walked way too far, advance anyway
        val currentStepDistance = distanceTracker.getStepProgress()
        val expectedDistance = nextStep.expectedWalkDistance
        if (expectedDistance > 0 && currentStepDistance >= expectedDistance * 1.5f) {
            Log.d(TAG, "⚠️ Distance fallback: walked ${String.format("%.2f", currentStepDistance)}m >> expected ${String.format("%.2f", expectedDistance)}m")
            return ProgressionResult.AdvanceToStep(
                newStep = currentStep + 1,
                reason = "Distance fallback: walked ${String.format("%.2f", currentStepDistance)}m (150% of expected)"
            )
        }

        return ProgressionResult.NoAdvance("Waiting for next step landmark: $nextStepLandmarkIds")
    }
    
    /**
     * Either landmark detection or distance threshold works
     */
    private fun analyzeLandmarkOrDistance(
        currentStep: Int,
        step: com.example.arwalking.NavigationStep,
        nextStep: com.example.arwalking.NavigationStep?,
        landmarkMatches: List<RouteViewModel.LandmarkMatch>
    ): ProgressionResult {
        // Windowed matching: consider only current and next step landmarks
        val currentIdsAll = step.landmarks.map { it.id }.toSet()
        val nextIds = nextStep?.landmarks?.map { it.id }?.toSet() ?: emptySet()

        // Restrict current-step IDs if needed (e.g., only Entry when leaving office)
        val allowedCurrent = allowedLandmarkIdsForStep(step)
        val currentIds = if (allowedCurrent.isEmpty()) currentIdsAll else currentIdsAll.intersect(allowedCurrent)

        // Check landmark first with window constraint
        val goodMatches = landmarkMatches.filter { it.confidence >= LANDMARK_CONFIDENCE_THRESHOLD }
        val goodWindowMatches = goodMatches.filter { m -> m.landmark.id in currentIds || m.landmark.id in nextIds }

        // Prefer next-step landmarks to advance exactly one step
        val nextGood = goodWindowMatches.firstOrNull { it.landmark.id in nextIds }
        if (nextGood != null) {
            Log.d(TAG, "🎯 LANDMARK_OR_DISTANCE: Next-step landmark detected: ${nextGood.landmark.id} (${(nextGood.confidence * 100).toInt()}%)")
            return ProgressionResult.AdvanceToStep(
                newStep = currentStep + 1,
                reason = "Landmark: ${nextGood.landmark.id} (${(nextGood.confidence * 100).toInt()}%)"
            )
        }

        // If only current-step landmarks are seen, do not advance yet
        if (goodWindowMatches.any { it.landmark.id in currentIds }) {
            return ProgressionResult.NoAdvance("Current-step landmark detected; waiting for next-step landmark")
        }

        // Fall back to distance
        val tolerance = getDistanceTolerance(step.expectedWalkDistance)
        if (distanceTracker.shouldAdvanceByDistance(step.expectedWalkDistance, tolerance)) {
            val walkedDistance = distanceTracker.getStepProgress()
            return ProgressionResult.AdvanceToStep(
                newStep = currentStep + 1,
                reason = "Distance: ${String.format("%.2f", walkedDistance)}m / ${String.format("%.2f", step.expectedWalkDistance)}m (${String.format("%.0f", tolerance)}%)"
            )
        }
        
        return ProgressionResult.NoAdvance("Waiting for landmark or distance threshold (windowed to current/next)")
    }
    
    /**
     * Pure distance-based progression (no landmarks involved)
     */
    private fun analyzeDistanceOnly(
        currentStep: Int,
        step: com.example.arwalking.NavigationStep
    ): ProgressionResult {
        
        val tolerance = getDistanceTolerance(step.expectedWalkDistance)
        if (distanceTracker.shouldAdvanceByDistance(step.expectedWalkDistance, tolerance)) {
            val walkedDistance = distanceTracker.getStepProgress()
            return ProgressionResult.AdvanceToStep(
                newStep = currentStep + 1,
                reason = "Distance only: ${String.format("%.2f", walkedDistance)}m / ${String.format("%.2f", step.expectedWalkDistance)}m"
            )
        }
        
        return ProgressionResult.NoAdvance("Distance not reached yet")
    }
    
    /**
     * Complex hybrid logic for special cases
     */
    private fun analyzeHybrid(
        currentStep: Int,
        step: com.example.arwalking.NavigationStep,
        nextStep: com.example.arwalking.NavigationStep?,
        landmarkMatches: List<RouteViewModel.LandmarkMatch>
    ): ProgressionResult {
        // For now, use landmark-or-distance logic
        return analyzeLandmarkOrDistance(currentStep, step, nextStep, landmarkMatches)
    }
    
    /**
     * Get distance tolerance percentage based on step length
     */
    private fun getDistanceTolerance(expectedDistance: Double): Float {
        return when {
            expectedDistance < 2.0 -> DISTANCE_TOLERANCE_SMALL_STEP   // 90% für kurze Schritte
            expectedDistance < 5.0 -> DISTANCE_TOLERANCE_MEDIUM_STEP  // 80% für mittlere Schritte
            else -> DISTANCE_TOLERANCE_LARGE_STEP                     // 70% für lange Schritte
        }
    }

    // Determine allowed landmark IDs for the current step.
    // For "Verlassen Sie das Büro" (leave office) we only allow Entry-type landmarks to count,
    // ignoring Office to avoid self-detection at the start.
    private fun allowedLandmarkIdsForStep(step: com.example.arwalking.NavigationStep): Set<String> {
        val instr = step.instruction.lowercase()
        val hasEntry = step.landmarks.any { (it.type ?: "").equals("entry", ignoreCase = true) }
        if (("verlassen" in instr || "leave" in instr) && hasEntry) {
            return step.landmarks.filter { (it.type ?: "").equals("entry", ignoreCase = true) }
                .map { it.id }
                .toSet()
        }
        return emptySet()
    }
    
    /**
     * Called when step advances - reset distance tracking
     */
    fun onStepAdvanced(newStep: Int) {
        Log.d(TAG, "📈 Step advanced to: $newStep")
        distanceTracker.resetStepDistance()
    }
    
    /**
     * Get current distance stats for logging
     */
    fun getDistanceStats(): DistanceStats = distanceTracker.getStats()
    
    /**
     * Get current step progress for UI
     */
    fun getCurrentStepProgress(): Float = distanceTracker.getStepProgress()
    
    /**
     * Get step progress percentage for UI
     */
    fun getStepProgressPercent(expectedDistance: Double): Float = 
        distanceTracker.getStepProgressPercent(expectedDistance)
    
    /**
     * Reset all tracking when route starts
     */
    fun reset() {
        Log.d(TAG, "🔄 SmartStepProgressionManager reset")
        distanceTracker.reset()
    }
}

/**
 * Result of step progression analysis
 */
sealed class ProgressionResult {
    data class AdvanceToStep(val newStep: Int, val reason: String) : ProgressionResult()
    data class NoAdvance(val reason: String) : ProgressionResult()
}