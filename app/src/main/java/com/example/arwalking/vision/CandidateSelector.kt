package com.example.arwalking.vision

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.arwalking.data.ARNavigationConfig
import com.example.arwalking.data.NavigationStep
import com.example.arwalking.data.MatchResult

/**
 * CandidateSelector - Optimizes performance by pre-selecting top-K landmark candidates
 * Reduces the number of expensive feature matching operations
 */
class CandidateSelector(
    private val config: ARNavigationConfig,
    private val landmarkStore: LandmarkStore
) {
    
    companion object {
        private const val TAG = "CandidateSelector"
    }
    
    private val landmarkPriorities = mutableMapOf<String, Float>()
    private val recentMatches = mutableMapOf<String, Long>()
    
    /**
     * Select top-K landmark candidates for matching
     */
    suspend fun selectCandidates(
        availableLandmarks: Set<String>,
        currentStep: NavigationStep?
    ): List<LandmarkCandidate> = withContext(Dispatchers.Default) {
        
        val candidates = mutableListOf<LandmarkCandidate>()
        
        availableLandmarks.forEach { landmarkId ->
            val images = landmarkStore.getLandmarkImages(landmarkId)
            if (images.isNotEmpty()) {
                val priority = calculatePriority(landmarkId, currentStep)
                candidates.add(
                    LandmarkCandidate(
                        landmarkId = landmarkId,
                        images = images,
                        priority = priority,
                        imageCount = images.size
                    )
                )
            }
        }
        
        // Sort by priority and take top-K
        val topCandidates = candidates
            .sortedByDescending { it.priority }
            .take(config.topK)
        
        Log.d(TAG, "Selected ${topCandidates.size} candidates from ${candidates.size} available landmarks")
        
        topCandidates
    }
    
    /**
     * Calculate priority for a landmark based on various factors
     */
    private fun calculatePriority(landmarkId: String, currentStep: NavigationStep?): Float {
        var priority = 1.0f
        
        // Boost priority if this landmark is expected for current step
        if (currentStep?.landmarkId == landmarkId) {
            priority += 2.0f
        }
        
        // Boost priority based on recent successful matches
        val lastMatch = recentMatches[landmarkId]
        if (lastMatch != null) {
            val timeSinceMatch = System.currentTimeMillis() - lastMatch
            val recencyBoost = when {
                timeSinceMatch < 5000 -> 1.5f  // Last 5 seconds
                timeSinceMatch < 15000 -> 1.0f // Last 15 seconds
                timeSinceMatch < 30000 -> 0.5f // Last 30 seconds
                else -> 0.0f
            }
            priority += recencyBoost
        }
        
        // Apply stored priority adjustments
        priority += landmarkPriorities[landmarkId] ?: 0f
        
        // Small random factor to prevent always selecting same order
        priority += (Math.random() * 0.1).toFloat()
        
        return priority.coerceAtLeast(0.1f)
    }
    
    /**
     * Update landmark priority based on matching results
     */
    fun updateLandmarkPriority(landmarkId: String, matchResult: MatchResult?) {
        if (matchResult != null && matchResult.confidence > config.thresholds.match) {
            // Successful match - boost priority
            val currentPriority = landmarkPriorities[landmarkId] ?: 0f
            landmarkPriorities[landmarkId] = (currentPriority + 0.1f).coerceAtMost(2.0f)
            recentMatches[landmarkId] = System.currentTimeMillis()
            
            Log.d(TAG, "Boosted priority for $landmarkId to ${landmarkPriorities[landmarkId]}")
        } else {
            // Failed match - slightly reduce priority
            val currentPriority = landmarkPriorities[landmarkId] ?: 0f
            landmarkPriorities[landmarkId] = (currentPriority - 0.05f).coerceAtLeast(-1.0f)
        }
    }
    
    /**
     * Expand candidate selection when no good matches are found
     */
    suspend fun expandCandidates(
        availableLandmarks: Set<String>,
        currentCandidates: List<LandmarkCandidate>,
        expansionFactor: Float = 1.5f
    ): List<LandmarkCandidate> = withContext(Dispatchers.Default) {
        
        val currentLandmarkIds = currentCandidates.map { it.landmarkId }.toSet()
        val remainingLandmarks = availableLandmarks - currentLandmarkIds
        
        if (remainingLandmarks.isEmpty()) {
            return@withContext currentCandidates
        }
        
        val additionalCount = (config.topK * (expansionFactor - 1.0f)).toInt().coerceAtLeast(1)
        
        val additionalCandidates = remainingLandmarks.map { landmarkId ->
            val images = landmarkStore.getLandmarkImages(landmarkId)
            LandmarkCandidate(
                landmarkId = landmarkId,
                images = images,
                priority = calculatePriority(landmarkId, null) * 0.5f, // Lower priority for expanded
                imageCount = images.size
            )
        }.sortedByDescending { it.priority }
         .take(additionalCount)
        
        val expandedCandidates = currentCandidates + additionalCandidates
        
        Log.d(TAG, "Expanded candidates from ${currentCandidates.size} to ${expandedCandidates.size}")
        
        expandedCandidates
    }
    
    /**
     * Adapt selection strategy based on performance metrics
     */
    fun adaptStrategy(performanceMetrics: PerformanceMetrics) {
        // If matching is too slow, reduce topK
        if (performanceMetrics.avgMatchingTimeMs > 100) {
            val newTopK = (config.topK * 0.8f).toInt().coerceAtLeast(3)
            Log.d(TAG, "Reducing topK from ${config.topK} to $newTopK due to slow matching")
            // Note: In a real implementation, you'd want to update the config
        }
        
        // If hit rate is low, increase topK
        if (performanceMetrics.hitRate < 0.3f && config.topK < 20) {
            val newTopK = (config.topK * 1.2f).toInt().coerceAtMost(20)
            Log.d(TAG, "Increasing topK from ${config.topK} to $newTopK due to low hit rate")
        }
    }
    
    /**
     * Get candidate selection statistics
     */
    fun getSelectionStats(): SelectionStats {
        val totalLandmarks = landmarkStore.getLoadedLandmarkIds().size
        val prioritizedLandmarks = landmarkPriorities.size
        val recentlyMatched = recentMatches.values.count { 
            System.currentTimeMillis() - it < 30000 
        }
        
        return SelectionStats(
            totalLandmarks = totalLandmarks,
            prioritizedLandmarks = prioritizedLandmarks,
            recentlyMatchedLandmarks = recentlyMatched,
            avgPriority = landmarkPriorities.values.average().toFloat(),
            maxPriority = landmarkPriorities.values.maxOrNull() ?: 0f,
            minPriority = landmarkPriorities.values.minOrNull() ?: 0f
        )
    }
    
    /**
     * Reset selection state
     */
    fun reset() {
        landmarkPriorities.clear()
        recentMatches.clear()
        Log.d(TAG, "Reset candidate selection state")
    }
    
    /**
     * Clean up old entries
     */
    fun cleanup() {
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - 300000 // 5 minutes
        
        val oldMatches = recentMatches.filter { it.value < cutoffTime }
        oldMatches.keys.forEach { recentMatches.remove(it) }
        
        if (oldMatches.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${oldMatches.size} old match records")
        }
    }
}

/**
 * Landmark candidate for matching
 */
data class LandmarkCandidate(
    val landmarkId: String,
    val images: List<LandmarkImage>,
    val priority: Float,
    val imageCount: Int
)

/**
 * Performance metrics for adaptation
 */
data class PerformanceMetrics(
    val avgMatchingTimeMs: Long,
    val hitRate: Float,
    val falsePositiveRate: Float,
    val memoryUsageMB: Float
)

/**
 * Candidate selection statistics
 */
data class SelectionStats(
    val totalLandmarks: Int,
    val prioritizedLandmarks: Int,
    val recentlyMatchedLandmarks: Int,
    val avgPriority: Float,
    val maxPriority: Float,
    val minPriority: Float
)