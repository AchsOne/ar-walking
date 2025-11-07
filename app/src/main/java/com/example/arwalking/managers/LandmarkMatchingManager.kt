package com.example.arwalking.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.arwalking.NavigationRoute
import com.example.arwalking.RouteLandmarkData
// import com.example.arwalking.storage.ArWalkingStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.AKAZE
import org.opencv.features2d.DescriptorMatcher
import org.opencv.imgproc.Imgproc

/**
 * Detects landmarks in camera frames
 */
class LandmarkMatchingManager : ViewModel() {
    private companion object {
        const val TAG = "LandmarkMatchingManager"
        private const val FRAME_INTERVAL = 500L
        private const val CONFIDENCE_THRESHOLD = 0.60f
        private const val MIN_MATCHES = 5
        private const val MIN_CONFIDENCE = 0.30f
        private const val RATIO_THRESHOLD = 0.75f
    }

    // Feature matching state
    private val _currentMatches = MutableStateFlow<List<LandmarkMatch>>(emptyList())
    val currentMatches: StateFlow<List<LandmarkMatch>> = _currentMatches.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _featureMappingEnabled = MutableStateFlow(false)
    val featureMappingEnabled: StateFlow<Boolean> = _featureMappingEnabled.asStateFlow()

    // Feature detection components
    private var detector: AKAZE? = null
    private var matcher: DescriptorMatcher? = null
    private val featuresCache = mutableMapOf<String, LandmarkFeatures>()
    private val detectionHistory = mutableMapOf<String, DetectionHistory>()

    // Also support landmark image variants that are not explicitly in the route JSON but
    // exist as asset companions of route landmarks, e.g. "<PrevLandmark>_L" (left) and
    // in future "<PrevLandmark>_R" (right). We DO NOT load arbitrary assets, only these
    // variants if present in assets and if the base landmark is used in the route.
    private var additionalAssetLandmarks: List<RouteLandmarkData> = emptyList()

    // Configuration
    private var autoAdvanceThreshold = 0.50f
    private var lastProcessedTime = 0L
    private var frameCounter = 0

    /**
     * Initializes the AKAZE feature detection.
     */
    fun initializeFeatureDetection() {
        try {
            // AKAZE with default parameters — works well for building features
            detector = AKAZE.create()
            // Brute-force matcher for highest accuracy
            matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
            Log.d(TAG, "AKAZE feature detection ready")
        } catch (e: Exception) {
            Log.e(TAG, "Feature detection could not be initialized", e)
        }
    }

    /**
     * Enable/disable feature mapping
     */
    fun setFeatureMappingEnabled(enabled: Boolean) {
        _featureMappingEnabled.value = enabled
        if (enabled) {
            initializeFeatureDetection()
        }
    }

    /**
     * Pre-load landmark features for the given route
     */
    suspend fun preloadLandmarkFeatures(route: NavigationRoute, context: Context) {
        try {
            // Gather route-declared landmarks
            val routeLandmarks = getLandmarksFromRoute(route)
            val baseIds = routeLandmarks.map { it.id }
            // Also gather only asset companions of route landmarks (e.g., id_L and id_R if present)
            additionalAssetLandmarks = getAssetVariantsForRoute(context, baseIds)
            val allLandmarks = (routeLandmarks + additionalAssetLandmarks).distinctBy { it.id }

            if (allLandmarks.isNotEmpty()) {
                Log.d(TAG, "Pre-loading features for ${allLandmarks.size} landmarks (route=${routeLandmarks.size}, asset-variants=${additionalAssetLandmarks.size})")
                loadLandmarkFeatures(allLandmarks, context)
                Log.d(TAG, "Pre-loading complete: ${featuresCache.size} landmarks ready")
            } else {
                Log.d(TAG, "No landmarks found (route + asset variants) for pre-loading")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pre-load landmark features", e)
        }
    }

    /**
     * Process camera frame for landmark detection
     */
    suspend fun processFrame(
        bitmap: Bitmap,
        currentRoute: NavigationRoute?,
        context: Context,
        onProgressUpdate: (List<LandmarkMatch>) -> Unit
    ) {
        if (!_featureMappingEnabled.value) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < FRAME_INTERVAL) {
            return
        }

        if (_isProcessing.value) {
            Log.v(TAG, "⏭️ Frame processing already in progress, skipping")
            return
        }

        frameCounter++
        Log.d(TAG, "🎥 processFrame called - enabled: ${_featureMappingEnabled.value}, processing: ${_isProcessing.value}")
        Log.d(TAG, "🔄 Processing frame #$frameCounter")

        _isProcessing.value = true
        lastProcessedTime = now

        try {
            withContext(Dispatchers.Default) {
                // Extract features from current frame
                val frameFeatures = extractFeatures(bitmap)
                if (frameFeatures == null) {
                    Log.w(TAG, "⚠️ Failed to extract frame features")
                    return@withContext
                }

                Log.d(TAG, "🔍 Frame features extracted: ${frameFeatures.keypoints.rows()} keypoints")

                // Debug: log current step landmarks
                val currentStepLandmarks = currentRoute?.steps?.getOrNull(0)?.landmarks ?: emptyList()
                Log.d(TAG, "🎯 Current step 0 expects landmarks: ${currentStepLandmarks.map { it.id }}")

                // Get landmarks for matching (route + asset-only)
                val landmarks = getLandmarks(currentRoute)
                if (landmarks.isEmpty()) {
                    Log.v(TAG, "📭 No landmarks available for matching")
                    return@withContext
                }

                // Check if we have cached features (should be pre-loaded)
                val availableFeatures = landmarks.filter { featuresCache.containsKey(it.id) }
                if (availableFeatures.isEmpty()) {
                    Log.w(TAG, "⚠️ No cached landmark features found - ensure preloadLandmarkFeatures was called")
                    return@withContext
                }

                Log.d(TAG, "📚 Using cached features for ${availableFeatures.size}/${landmarks.size} landmarks")
                Log.d(TAG, "🔑 Available landmark IDs: ${availableFeatures.map { it.id }}")

                // Perform landmark matching only with cached landmarks
                val matches = performLandmarkMatching(frameFeatures, availableFeatures)
                Log.d(TAG, "🎯 Landmark matching complete: ${matches.size} matches")

                if (matches.isNotEmpty()) {
                    val bestMatch = matches.first()
                    Log.d(TAG, "📊 Best match: ${bestMatch.landmark.id} (${(bestMatch.confidence * 100).toInt()}% confidence)")
                }

                // Update state
                _currentMatches.value = matches

                // Notify callback
                onProgressUpdate(matches)

                // Clean up frame features
                frameFeatures.keypoints.release()
                frameFeatures.descriptors.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Frame processing failed", e)
        } finally {
            _isProcessing.value = false
            Log.v(TAG, "🏁 Frame processing completed")
        }
    }

    /**
     * Extract features from bitmap using AKAZE detector
     */
    private fun extractFeatures(bitmap: Bitmap): LandmarkFeatures? {
        return try {
            Log.d(TAG, "🖼️ Processing bitmap: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            Log.d(TAG, "📊 Mat created: ${mat.cols()}x${mat.rows()}, channels=${mat.channels()}")

            // Convert to grayscale
            val gray = if (mat.channels() > 1) {
                val grayMat = Mat()
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
                grayMat
            } else {
                mat.clone()
            }

            // Detect keypoints and compute descriptors
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()

            detector?.detectAndCompute(gray, Mat(), keypoints, descriptors)

            mat.release()
            gray.release()

            if (keypoints.rows() == 0) {
                keypoints.release()
                descriptors.release()
                return null
            }

            LandmarkFeatures("frame", keypoints, descriptors)
        } catch (e: Exception) {
            Log.e(TAG, "Feature extraction failed", e)
            null
        }
    }

    /**
     * Load landmark features into cache from assets
     */
    private suspend fun loadLandmarkFeatures(landmarks: List<RouteLandmarkData>, context: Context) {
        withContext(Dispatchers.IO) {
            try {
                for (landmark in landmarks) {
                    if (landmark.id !in featuresCache) {
                        try {
                            val bitmap = loadBitmap(context, landmark.id)
                            if (bitmap != null) {
                                val features = extractLandmarkFeatures(bitmap, landmark.id)
                                if (features != null) {
                                    featuresCache[landmark.id] = features
                                    Log.d(TAG, "Loaded features for landmark ${landmark.id}: ${features.keypoints.rows()} keypoints")
                                } else {
                                    Log.w(TAG, "No features extracted for landmark ${landmark.id}")
                                }
                            } else {
                                Log.w(TAG, "Could not load bitmap for landmark ${landmark.id}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load landmark ${landmark.id}: ${e.message}")
                        }
                    }
                }
                Log.d(TAG, "Landmark feature loading complete: ${featuresCache.size}/${landmarks.size} loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load landmark features", e)
            }
        }
    }

    /**
     * Enumerate only asset variants for route landmarks: for each baseId, include baseId+"_L" and
     * (future) baseId+"_R" if a corresponding image exists in assets/landmark_images.
     */
    private fun getAssetVariantsForRoute(context: Context, baseIds: List<String>): List<RouteLandmarkData> {
        return try {
            val files = context.assets.list("landmark_images")?.toList() ?: emptyList()
            val available = files.filter { it.endsWith(".jpg", ignoreCase = true) }
                .map { it.removeSuffix(".jpg").removeSuffix(".JPG") }
                .toSet()
            val candidates = buildList {
                baseIds.forEach { id ->
                    add("${'$'}id_L")
                    // Future right-turn companion
                    add("${'$'}id_R")
                }
            }
            val picked = candidates.filter { it in available }.distinct()
            picked.map { RouteLandmarkData(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list asset variants: ${e.message}")
            emptyList()
        }
    }

    /**
     * Load bitmap from assets with fallback for synthetic landmarks
     */
    private fun loadBitmap(context: Context, landmarkId: String): Bitmap? {
        return try {
            // Try to load the specific landmark image first
            context.assets.open("landmark_images/$landmarkId.jpg").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load bitmap for $landmarkId: landmark_images/$landmarkId.jpg")

            // Fallback for synthetic landmarks (e.g., PT-1-566_L -> PT-1-566)
            if (landmarkId.endsWith("_L") || landmarkId.endsWith("_R")) {
                val baseLandmarkId = landmarkId.dropLast(2) // Remove _L or _R suffix
                Log.d(TAG, "Attempting fallback: $landmarkId -> $baseLandmarkId")

                try {
                    context.assets.open("landmark_images/$baseLandmarkId.jpg").use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        Log.d(TAG, "✅ Fallback successful: Using $baseLandmarkId.jpg for $landmarkId")
                        bitmap
                    }
                } catch (fallbackException: Exception) {
                    Log.w(TAG, "❌ Fallback also failed for $baseLandmarkId: ${fallbackException.message}")
                    null
                }
            } else {
                Log.w(TAG, "Could not load bitmap for landmark $landmarkId")
                null
            }
        }
    }

    /**
     * Extract features from landmark bitmap
     */
    private fun extractLandmarkFeatures(bitmap: Bitmap, id: String): LandmarkFeatures? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Convert to grayscale
            val gray = if (mat.channels() > 1) {
                val grayMat = Mat()
                Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
                grayMat
            } else {
                mat.clone()
            }

            // Detect keypoints and compute descriptors
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()

            detector?.detectAndCompute(gray, Mat(), keypoints, descriptors)

            mat.release()
            gray.release()

            if (keypoints.rows() > 0 && descriptors.rows() > 0) {
                LandmarkFeatures(id, keypoints, descriptors)
            } else {
                keypoints.release()
                descriptors.release()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feature extraction failed for $id", e)
            null
        }
    }

    /**
     * Perform landmark matching against frame features
     */
    private fun performLandmarkMatching(frameFeatures: LandmarkFeatures, landmarks: List<RouteLandmarkData>): List<LandmarkMatch> {
        return try {
            val matches = mutableListOf<LandmarkMatch>()

            Log.d(TAG, "🎯 Starting matching against ${landmarks.size} landmarks")

            for (landmark in landmarks) {
                val landmarkFeatures = featuresCache[landmark.id]
                if (landmarkFeatures != null) {
                    Log.d(TAG, "🔍 Matching ${landmark.id}: frame=${frameFeatures.keypoints.rows()} vs landmark=${landmarkFeatures.keypoints.rows()} keypoints")
                    val match = matchFeatures(frameFeatures, landmarkFeatures)
                    Log.d(TAG, "📊 Match result for ${landmark.id}: ${match.matchCount} matches, ${(match.confidence * 100).toInt()}% confidence")

                    if (match.confidence >= MIN_CONFIDENCE && match.matchCount >= 1) {
                        matches.add(match)
                        Log.d(TAG, "✅ Added good match: ${landmark.id} (${(match.confidence * 100).toInt()}%)")
                    } else {
                        Log.d(TAG, "❌ Rejected match: ${landmark.id} (${(match.confidence * 100).toInt()}%, ${match.matchCount} matches)")
                    }
                } else {
                    Log.w(TAG, "⚠️ No cached features for landmark ${landmark.id}")
                }
            }

            // Sort by confidence descending
            matches.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            Log.e(TAG, "Landmark matching failed", e)
            emptyList()
        }
    }

    /**
     * Match features between frame and landmark
     * (Core algorithm created with assistance from an LLM)
     */
    private fun matchFeatures(frame: LandmarkFeatures, landmark: LandmarkFeatures): LandmarkMatch {
        try {
            if (frame.descriptors.rows() == 0 || landmark.descriptors.rows() == 0) {
                return createEmptyMatch(landmark.id)
            }

            val knnMatches = mutableListOf<MatOfDMatch>()
            matcher?.knnMatch(frame.descriptors, landmark.descriptors, knnMatches, 2)

            var goodMatches = 0
            var totalDistance = 0f

            knnMatches.forEach { match ->
                val arr = match.toArray()
                if (arr.size >= 2) {
                    val m = arr[0]
                    val n = arr[1]
                    if (m.distance < RATIO_THRESHOLD * n.distance) {
                        goodMatches++
                        totalDistance += m.distance
                    }
                }
            }

            val avgDistance = if (goodMatches > 0) totalDistance / goodMatches else Float.MAX_VALUE
            val minKeypoints = minOf(frame.keypoints.rows(), landmark.keypoints.rows())

            // Complex confidence calculation (created with assistance from an LLM)
            val confidence = if (goodMatches > 0) {
                when {
                    minKeypoints <= 15 -> (goodMatches.toFloat() / 8f).coerceAtMost(1f)
                    else -> {
                        val matchRatio = goodMatches.toFloat() / minKeypoints
                        val qualityScore = (goodMatches.toFloat() / 15f).coerceAtMost(1f)
                        val distanceScore = if (avgDistance != Float.MAX_VALUE) {
                            (100f / (avgDistance + 1f)).coerceIn(0f, 1f)
                        } else 0f

                        (matchRatio * 0.4f + qualityScore * 0.4f + distanceScore * 0.2f).coerceIn(0f, 1f)
                    }
                }
            } else 0f

            knnMatches.forEach { it.release() }

            val landmarkData = RouteLandmarkData(landmark.id)
            return LandmarkMatch(landmarkData, goodMatches, confidence, avgDistance)
        } catch (e: Exception) {
            Log.e(TAG, "Feature matching failed", e)
            return createEmptyMatch(landmark.id)
        }
    }

    /**
     * Create empty match for landmark
     */
    private fun createEmptyMatch(landmarkId: String): LandmarkMatch {
        val landmark = RouteLandmarkData(landmarkId)
        return LandmarkMatch(landmark, 0, 0f, Float.MAX_VALUE)
    }

    /**
     * Get landmarks from current route plus any additional asset-only landmarks
     */
    private fun getLandmarks(currentRoute: NavigationRoute?): List<RouteLandmarkData> {
        val routeLm = currentRoute?.steps?.flatMap { it.landmarks }?.distinctBy { it.id } ?: emptyList()
        return (routeLm + additionalAssetLandmarks).distinctBy { it.id }
    }

    /**
     * Get landmarks from non-null route
     */
    private fun getLandmarksFromRoute(route: NavigationRoute): List<RouteLandmarkData> {
        return route.steps.flatMap { it.landmarks }.distinctBy { it.id }
    }

    /**
     * Clean up resources - made public for external cleanup
     */
    fun cleanup() {
        detector = null
        matcher = null

        featuresCache.values.forEach { features ->
            try {
                features.keypoints.release()
                features.descriptors.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        featuresCache.clear()
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    // Data classes
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
}