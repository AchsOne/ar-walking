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

class RouteViewModel : ViewModel() {

    // Route state
    private val _currentRoute = MutableStateFlow<NavigationRoute?>(null)
    val currentRoute: StateFlow<NavigationRoute?> = _currentRoute.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _completedSteps = MutableStateFlow<Set<Int>>(emptySet())
    val completedSteps: StateFlow<Set<Int>> = _completedSteps.asStateFlow()

    private val _deletedSteps = MutableStateFlow<Set<Int>>(emptySet())
    val deletedSteps: StateFlow<Set<Int>> = _deletedSteps.asStateFlow()

    private val _navigationStatus = MutableStateFlow(NavigationStatus.WAITING)
    val navigationStatus: StateFlow<NavigationStatus> = _navigationStatus.asStateFlow()

    // Feature matching state
    private val _currentMatches = MutableStateFlow<List<LandmarkMatch>>(emptyList())
    val currentMatches: StateFlow<List<LandmarkMatch>> = _currentMatches.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _featureMappingEnabled = MutableStateFlow(false)
    val featureMappingEnabled: StateFlow<Boolean> = _featureMappingEnabled.asStateFlow()

    // Feature detection
    private var detector: AKAZE? = null
    private var matcher: DescriptorMatcher? = null
    private val featuresCache = mutableMapOf<String, LandmarkFeatures>()
    private val detectionHistory = mutableMapOf<String, DetectionHistory>()

    // Config
    private var autoAdvanceThreshold = 0.50f
    private var lastProcessedTime = 0L
    private var frameCounter = 0


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
                NavigationStep(
                    stepNumber = index,
                    instruction = part.instructionDe,
                    building = pathItem.xmlName,
                    floor = pathItem.levelInfo?.storey?.toIntOrNull() ?: 0,
                    landmarks = part.landmarks,
                    distance = part.distance ?: 0.0,
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
            Log.d(TAG, "Route loaded: ${steps.size} steps, ${route.totalLength}m")
            route
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load route", e)
            null
        }
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

    fun logRoute(route: NavigationRoute) {
        Log.d(TAG, "=== Route Details ===")
        Log.d(TAG, "From: ${route.startPoint}")
        Log.d(TAG, "To: ${route.endPoint}")
        Log.d(TAG, "Distance: ${route.totalDistance}m")
        Log.d(TAG, "Steps: ${route.steps.size}")

        route.steps.forEachIndexed { index, step ->
            Log.d(TAG, "Step ${index + 1}: ${step.instruction}")
            if (step.landmarks.isNotEmpty()) {
                Log.d(TAG, "  Landmarks: ${step.landmarks.size}")
            }
        }
    }

    fun skipStep() {
        val route = _currentRoute.value ?: return
        val current = _currentStep.value
        val lastIndex = route.steps.lastIndex

        _deletedSteps.value = _deletedSteps.value + current
        Log.d(TAG, "Step $current skipped")

        if (current < lastIndex) {
            _currentStep.value = current + 1
            _navigationStatus.value = NavigationStatus.STEP_COMPLETED
        } else {
            _navigationStatus.value = NavigationStatus.ROUTE_COMPLETED
        }

        detectionHistory.clear()
    }

    fun initFeatureMapping(context: Context) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                detector = AKAZE.create()
                matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

                loadLandmarkFeatures(context)

                _featureMappingEnabled.value = true
                Log.d(TAG, "Feature mapping initialized (${featuresCache.size} landmarks)")
            } catch (e: Exception) {
                Log.e(TAG, "Feature mapping init failed", e)
                _featureMappingEnabled.value = false
            }
        }
    }

    private suspend fun loadLandmarkFeatures(context: Context) = withContext(Dispatchers.IO) {
        try {
            val landmarks = getLandmarks()

            landmarks.forEach { landmark ->
                try {
                    val bitmap = loadBitmap(context, landmark.id)
                    if (bitmap != null) {
                        val features = extractFeatures(bitmap, landmark.id)
                        if (features != null) {
                            featuresCache[landmark.id] = features
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load landmark ${landmark.id}")
                }
            }

            Log.d(TAG, "Loaded ${featuresCache.size}/${landmarks.size} landmark features")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load landmark features", e)
        }
    }

    private fun loadBitmap(context: Context, landmarkId: String): Bitmap? {
        return try {
            context.assets.open("landmark_images/$landmarkId.jpg").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFeatures(bitmap: Bitmap, id: String): LandmarkFeatures? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            detector?.detectAndCompute(gray, Mat(), keypoints, descriptors)

            mat.release()
            gray.release()

            if (keypoints.rows() > 0) {
                LandmarkFeatures(id, keypoints, descriptors)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feature extraction failed for $id", e)
            null
        }
    }

    fun processFrame(bitmap: Bitmap) {
        if (!_featureMappingEnabled.value || _isProcessing.value) return

        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < FRAME_INTERVAL) return

        frameCounter++
        lastProcessedTime = now

        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true

            try {
                val frameFeatures = extractFrameFeatures(bitmap)

                if (frameFeatures != null) {
                    val matches = matchLandmarks(frameFeatures)
                    _currentMatches.value = matches

                    analyzeProgression(matches)

                    frameFeatures.keypoints.release()
                    frameFeatures.descriptors.release()
                } else {
                    _currentMatches.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame processing failed", e)
                _currentMatches.value = emptyList()
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private fun extractFrameFeatures(bitmap: Bitmap): LandmarkFeatures? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            detector?.detectAndCompute(gray, Mat(), keypoints, descriptors)

            mat.release()
            gray.release()

            if (keypoints.rows() > 0) {
                LandmarkFeatures("frame", keypoints, descriptors)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame feature extraction failed", e)
            null
        }
    }

    private fun matchLandmarks(frameFeatures: LandmarkFeatures): List<LandmarkMatch> {
        val matches = mutableListOf<LandmarkMatch>()

        try {
            val allLandmarks = getLandmarks()

            allLandmarks.forEach { landmark ->
                val landmarkFeatures = featuresCache[landmark.id]
                if (landmarkFeatures != null) {
                    val result = matchFeatures(frameFeatures, landmarkFeatures)

                    if (result.matchCount >= MIN_MATCHES && result.confidence >= MIN_CONFIDENCE) {
                        matches.add(result)
                    }
                }
            }

            return matches.sortedByDescending { it.confidence }
        } catch (e: Exception) {
            Log.e(TAG, "Landmark matching failed", e)
            return emptyList()
        }
    }

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

            val landmarkData = getLandmarks().find { it.id == landmark.id }
                ?: RouteLandmarkData(landmark.id, landmark.id)

            return LandmarkMatch(landmarkData, goodMatches, confidence, avgDistance)
        } catch (e: Exception) {
            Log.e(TAG, "Feature matching failed", e)
            return createEmptyMatch(landmark.id)
        }
    }

    private fun analyzeProgression(matches: List<LandmarkMatch>) {
        if (matches.isEmpty()) {
            handleNoDetection()
            return
        }

        val current = _currentStep.value
        val expectedIds = getExpectedLandmarks(current).map { it.id }.toSet()
        val strongMatch = matches.firstOrNull {
            it.landmark.id in expectedIds && it.confidence >= autoAdvanceThreshold
        }

        val routeSize = _currentRoute.value?.steps?.size ?: 0
        if (strongMatch != null && current < routeSize - 1) {
            transitionStep(current, current + 1, "auto_${strongMatch.landmark.id}", strongMatch.confidence)
            return
        }

        val now = System.currentTimeMillis()
        val best = matches.first()
        val targetStep = findStepForLandmark(best.landmark.id)

        if (targetStep == -1) return

        updateHistory(best.landmark.id, targetStep, best.confidence, now)

        val history = detectionHistory[best.landmark.id]
        if (history?.isStable == true && best.confidence >= CONFIDENCE_THRESHOLD) {
            handleTransition(targetStep, best.confidence, "landmark_${best.landmark.id}")
        }
    }

    private fun findStepForLandmark(landmarkId: String): Int {
        val route = _currentRoute.value ?: return -1
        route.steps.forEachIndexed { index, step ->
            if (step.landmarks.any { it.id == landmarkId }) return index
        }
        return -1
    }

    private fun updateHistory(id: String, step: Int, confidence: Float, time: Long) {
        val existing = detectionHistory[id]

        if (existing == null) {
            detectionHistory[id] = DetectionHistory(id, step, time, time, confidence, false)
        } else {
            existing.lastDetectionTime = time
            existing.highestConfidence = maxOf(existing.highestConfidence, confidence)

            val duration = time - existing.firstDetectionTime
            if (duration >= STABILITY_DURATION && confidence >= CONFIDENCE_THRESHOLD) {
                existing.isStable = true
            }
        }
    }

    private fun handleTransition(target: Int, confidence: Float, trigger: String) {
        val current = _currentStep.value
        if (target == current) return

        val distance = kotlin.math.abs(target - current)
        val proximityScore = when {
            distance == 1 -> 1.0f
            distance <= 2 -> 0.7f
            else -> 0.3f
        }

        val historyScore = if (target > current) 0.9f else 0.6f
        val confidenceBonus = if (confidence >= 0.70f) 0.1f else 0f
        val score = (confidence * 0.6f) + (proximityScore * 0.3f) + (historyScore * 0.1f) + confidenceBonus

        val required = when {
            distance == 1 -> 0.65f
            distance <= 3 -> 0.70f
            distance <= 5 -> 0.72f
            else -> 0.75f
        }

        if (score >= required) {
            transitionStep(current, target, trigger, confidence)
        }
    }

    private fun transitionStep(from: Int, to: Int, trigger: String, confidence: Float) {
        if (to > from) {
            val completed = _completedSteps.value.toMutableSet()
            for (step in from until to) {
                completed.add(step)
            }
            _completedSteps.value = completed
        }

        _currentStep.value = to
        _navigationStatus.value = NavigationStatus.STEP_COMPLETED
        detectionHistory.clear()

        Log.d(TAG, "Step transition: $from -> $to ($trigger, ${(confidence * 100).toInt()}%)")
    }

    private fun handleNoDetection() {
        val now = System.currentTimeMillis()
        val lastDetection = detectionHistory.values.maxByOrNull { it.lastDetectionTime }

        if (lastDetection != null) {
            val timeSince = now - lastDetection.lastDetectionTime
            if (timeSince > LOST_TRACKING_DURATION) {
                if (_navigationStatus.value != NavigationStatus.LOST_TRACKING) {
                    _navigationStatus.value = NavigationStatus.LOST_TRACKING
                }
            }
        }
    }

    fun startProcessing() {
        Log.d(TAG, "Frame processing started")
    }

    private fun getLandmarks(): List<RouteLandmarkData> {
        val route = _currentRoute.value ?: return emptyList()
        return route.steps.flatMap { it.landmarks }.distinctBy { it.id }
    }

    private fun getExpectedLandmarks(stepNumber: Int): List<RouteLandmarkData> {
        val route = _currentRoute.value ?: return emptyList()
        if (stepNumber !in route.steps.indices) return emptyList()
        return route.steps[stepNumber].landmarks
    }

    private fun createEmptyMatch(landmarkId: String): LandmarkMatch {
        val landmark = getLandmarks().find { it.id == landmarkId }
            ?: RouteLandmarkData(landmarkId, landmarkId)
        return LandmarkMatch(landmark, 0, 0f, Float.MAX_VALUE)
    }

    override fun onCleared() {
        super.onCleared()

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
        WAITING,
        TRACKING,
        STEP_COMPLETED,
        LOST_TRACKING,
        ROUTE_COMPLETED
    }

    companion object {
        private const val TAG = "RouteViewModel"
        private const val FRAME_INTERVAL = 500L
        private const val CONFIDENCE_THRESHOLD = 0.66f
        private const val STABILITY_DURATION = 3000L
        private const val LOST_TRACKING_DURATION = 10000L
        private const val MIN_MATCHES = 3
        private const val MIN_CONFIDENCE = 0.15f
        private const val RATIO_THRESHOLD = 0.7f
    }
}