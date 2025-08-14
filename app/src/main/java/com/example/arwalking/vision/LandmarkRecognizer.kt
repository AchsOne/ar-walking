package com.example.arwalking.vision

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.core.Mat
import java.util.concurrent.atomic.AtomicBoolean
import com.example.arwalking.data.ARNavigationConfig
import com.example.arwalking.data.NavigationStep
import com.example.arwalking.data.MatchResult
import com.example.arwalking.data.TrackingQuality

/**
 * LandmarkRecognizer - Main recognition engine with frame processing loop
 * Handles temporal smoothing and confidence-based landmark detection
 */
class LandmarkRecognizer(
    private val config: ARNavigationConfig,
    private val featureEngine: FeatureEngine,
    private val candidateSelector: CandidateSelector,
    private val featureCache: FeatureCache
) {
    
    companion object {
        private const val TAG = "LandmarkRecognizer"
    }
    
    private val _recognitionState = MutableStateFlow(RecognitionState())
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()
    
    private val _currentMatch = MutableStateFlow<MatchResult?>(null)
    val currentMatch: StateFlow<MatchResult?> = _currentMatch.asStateFlow()
    
    private val isProcessing = AtomicBoolean(false)
    private val frameQueue = mutableListOf<ProcessingFrame>()
    private val maxQueueSize = 3
    
    // Temporal smoothing
    private val matchHistory = mutableListOf<MatchResult>()
    private val maxHistorySize = config.stabilization.minStableFrames * 2
    private var stableFrameCount = 0
    private var lastStableLandmark: String? = null
    
    // Performance tracking
    private val processingTimes = mutableListOf<Long>()
    private val maxProcessingTimes = 30
    
    private var processingJob: Job? = null
    
    /**
     * Start the recognition engine
     */
    fun start() {
        if (processingJob?.isActive == true) {
            Log.w(TAG, "Recognition already started")
            return
        }
        
        processingJob = CoroutineScope(Dispatchers.Default).launch {
            Log.d(TAG, "Started landmark recognition engine")
            processFrameLoop()
        }
    }
    
    /**
     * Stop the recognition engine
     */
    fun stop() {
        processingJob?.cancel()
        processingJob = null
        
        synchronized(frameQueue) {
            frameQueue.clear()
        }
        
        reset()
        Log.d(TAG, "Stopped landmark recognition engine")
    }
    
    /**
     * Process a new camera frame
     */
    suspend fun processFrame(
        frame: Mat,
        availableLandmarks: Set<String>,
        currentStep: NavigationStep?
    ) {
        if (isProcessing.get()) {
            // Skip frame if still processing previous one
            return
        }
        
        val processingFrame = ProcessingFrame(
            frame = frame.clone(),
            availableLandmarks = availableLandmarks,
            currentStep = currentStep,
            timestamp = System.currentTimeMillis()
        )
        
        synchronized(frameQueue) {
            // Add to queue, removing oldest if full
            if (frameQueue.size >= maxQueueSize) {
                val removed = frameQueue.removeAt(0)
                removed.frame.release()
            }
            frameQueue.add(processingFrame)
        }
    }
    
    /**
     * Main frame processing loop
     */
    private suspend fun processFrameLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                val frame = synchronized(frameQueue) {
                    if (frameQueue.isNotEmpty()) {
                        frameQueue.removeAt(0)
                    } else {
                        null
                    }
                }
                
                if (frame != null) {
                    processFrameInternal(frame)
                    frame.frame.release()
                } else {
                    delay(16) // ~60 FPS check rate
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in frame processing loop", e)
                delay(100) // Brief pause on error
            }
        }
    }
    
    /**
     * Internal frame processing
     */
    private suspend fun processFrameInternal(processingFrame: ProcessingFrame) {
        if (!isProcessing.compareAndSet(false, true)) {
            return
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            // Update recognition state
            _recognitionState.value = _recognitionState.value.copy(
                isProcessing = true,
                lastFrameTime = processingFrame.timestamp
            )
            
            // Preprocess frame
            val preprocessed = featureEngine.preprocessImage(processingFrame.frame)
            val resized = featureEngine.resizeImage(preprocessed)
            
            // Extract features from frame
            val queryFeatures = featureEngine.extractFeatures(resized)
            
            if (queryFeatures == null) {
                Log.w(TAG, "No features extracted from frame")
                updateRecognitionState(null, processingFrame)
                return
            }
            
            // Select candidates
            val candidates = candidateSelector.selectCandidates(
                processingFrame.availableLandmarks,
                processingFrame.currentStep
            )
            
            if (candidates.isEmpty()) {
                Log.w(TAG, "No landmark candidates available")
                updateRecognitionState(null, processingFrame)
                return
            }
            
            // Match against candidates
            val bestMatch = findBestMatch(queryFeatures, candidates)
            
            // Apply temporal smoothing
            val smoothedMatch = applyTemporalSmoothing(bestMatch)
            
            // Update state
            updateRecognitionState(smoothedMatch, processingFrame)
            
            // Update candidate priorities
            candidates.forEach { candidate ->
                val matchResult = if (bestMatch?.landmarkId == candidate.landmarkId) bestMatch else null
                candidateSelector.updateLandmarkPriority(candidate.landmarkId, matchResult)
            }
            
            // Clean up
            queryFeatures.keypoints.release()
            queryFeatures.descriptors.release()
            if (preprocessed != processingFrame.frame) preprocessed.release()
            if (resized != preprocessed && resized != processingFrame.frame) resized.release()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            updateRecognitionState(null, processingFrame)
        } finally {
            val processingTime = System.currentTimeMillis() - startTime
            updatePerformanceMetrics(processingTime)
            
            isProcessing.set(false)
            
            _recognitionState.value = _recognitionState.value.copy(
                isProcessing = false,
                lastProcessingTimeMs = processingTime
            )
        }
    }
    
    /**
     * Find best match among candidates
     */
    private suspend fun findBestMatch(
        queryFeatures: FeatureSet,
        candidates: List<LandmarkCandidate>
    ): MatchResult? = withContext(Dispatchers.Default) {
        
        var bestMatch: MatchResult? = null
        var bestConfidence = 0f
        
        for (candidate in candidates) {
            for (image in candidate.images) {
                try {
                    // Try to load cached features first
                    val cachedFeatures = featureCache.loadCachedFeatures(candidate.landmarkId, image.path)
                    
                    val referenceFeatures = if (cachedFeatures != null) {
                        FeatureSet(cachedFeatures.keypoints, cachedFeatures.descriptors)
                    } else {
                        // Extract features and cache them
                        val features = featureEngine.extractFeatures(image.mat)
                        if (features != null) {
                            // Cache for future use
                            featureCache.cacheFeatures(
                                candidate.landmarkId,
                                image.path,
                                features.keypoints,
                                features.descriptors
                            )
                            features
                        } else {
                            continue
                        }
                    }
                    
                    // Match features
                    val matchingResult = featureEngine.matchFeatures(queryFeatures, referenceFeatures)
                    
                    if (matchingResult != null && matchingResult.confidence > bestConfidence) {
                        bestConfidence = matchingResult.confidence
                        bestMatch = MatchResult(
                            landmarkId = candidate.landmarkId,
                            confidence = matchingResult.confidence,
                            inliers = matchingResult.inliers,
                            keypoints = referenceFeatures.keypoints.total().toInt(),
                            homography = if (!matchingResult.homography.empty()) {
                                val homographyArray = FloatArray(9)
                                matchingResult.homography.get(0, 0, homographyArray)
                                homographyArray
                            } else null
                        )
                    }
                    
                    // Clean up if not cached
                    if (cachedFeatures == null) {
                        referenceFeatures.keypoints.release()
                        referenceFeatures.descriptors.release()
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error matching against ${candidate.landmarkId}/${image.filename}", e)
                }
            }
        }
        
        bestMatch
    }
    
    /**
     * Apply temporal smoothing to reduce jitter
     */
    private fun applyTemporalSmoothing(newMatch: MatchResult?): MatchResult? {
        // Add to history
        if (newMatch != null) {
            matchHistory.add(newMatch)
        }
        
        // Trim history
        if (matchHistory.size > maxHistorySize) {
            matchHistory.removeAt(0)
        }
        
        // Check for stable detection
        val recentMatches = matchHistory.takeLast(config.stabilization.minStableFrames)
        
        if (recentMatches.size >= config.stabilization.minStableFrames) {
            val landmarkCounts = recentMatches.groupingBy { it.landmarkId }.eachCount()
            val dominantLandmark = landmarkCounts.maxByOrNull { it.value }
            
            if (dominantLandmark != null && 
                dominantLandmark.value >= config.stabilization.minStableFrames) {
                
                // Check confidence threshold
                val dominantMatches = recentMatches.filter { it.landmarkId == dominantLandmark.key }
                val avgConfidence = dominantMatches.map { it.confidence }.average().toFloat()
                
                if (avgConfidence >= config.thresholds.match) {
                    // Stable detection
                    if (lastStableLandmark != dominantLandmark.key) {
                        stableFrameCount = 1
                        lastStableLandmark = dominantLandmark.key
                    } else {
                        stableFrameCount++
                    }
                    
                    // Apply EMA smoothing to confidence
                    val currentMatch = dominantMatches.last()
                    val smoothedConfidence = if (stableFrameCount > 1) {
                        config.stabilization.emaAlpha * currentMatch.confidence + 
                        (1 - config.stabilization.emaAlpha) * avgConfidence
                    } else {
                        currentMatch.confidence
                    }
                    
                    return currentMatch.copy(confidence = smoothedConfidence)
                }
            }
        }
        
        // No stable detection
        if (newMatch != null && newMatch.confidence >= config.thresholds.promote) {
            return newMatch
        }
        
        return null
    }
    
    /**
     * Update recognition state
     */
    private fun updateRecognitionState(match: MatchResult?, processingFrame: ProcessingFrame) {
        _currentMatch.value = match
        
        _recognitionState.value = _recognitionState.value.copy(
            currentLandmarkId = match?.landmarkId,
            matchConfidence = match?.confidence ?: 0f,
            inlierCount = match?.inliers ?: 0,
            keypointCount = match?.keypoints ?: 0,
            frameCount = _recognitionState.value.frameCount + 1,
            matchCount = if (match != null) _recognitionState.value.matchCount + 1 else _recognitionState.value.matchCount
        )
    }
    
    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics(processingTime: Long) {
        processingTimes.add(processingTime)
        if (processingTimes.size > maxProcessingTimes) {
            processingTimes.removeAt(0)
        }
        
        val avgProcessingTime = processingTimes.average().toLong()
        val fps = if (avgProcessingTime > 0) 1000f / avgProcessingTime else 0f
        
        _recognitionState.value = _recognitionState.value.copy(
            avgProcessingTimeMs = avgProcessingTime,
            currentFps = fps
        )
    }
    
    /**
     * Reset recognition state
     */
    fun reset() {
        matchHistory.clear()
        stableFrameCount = 0
        lastStableLandmark = null
        processingTimes.clear()
        
        _recognitionState.value = RecognitionState()
        _currentMatch.value = null
        
        candidateSelector.reset()
    }
    
    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        val state = _recognitionState.value
        return PerformanceMetrics(
            avgMatchingTimeMs = state.avgProcessingTimeMs,
            hitRate = if (state.frameCount > 0) state.matchCount.toFloat() / state.frameCount else 0f,
            falsePositiveRate = 0f, // Would need ground truth to calculate
            memoryUsageMB = Runtime.getRuntime().let { 
                (it.totalMemory() - it.freeMemory()) / (1024f * 1024f) 
            }
        )
    }
}

/**
 * Frame to be processed
 */
private data class ProcessingFrame(
    val frame: Mat,
    val availableLandmarks: Set<String>,
    val currentStep: NavigationStep?,
    val timestamp: Long
)

/**
 * Current recognition state
 */
data class RecognitionState(
    val isProcessing: Boolean = false,
    val currentLandmarkId: String? = null,
    val matchConfidence: Float = 0f,
    val inlierCount: Int = 0,
    val keypointCount: Int = 0,
    val frameCount: Long = 0,
    val matchCount: Long = 0,
    val lastFrameTime: Long = 0,
    val lastProcessingTimeMs: Long = 0,
    val avgProcessingTimeMs: Long = 0,
    val currentFps: Float = 0f
)