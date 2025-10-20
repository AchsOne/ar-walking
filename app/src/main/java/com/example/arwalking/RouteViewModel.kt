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
import org.opencv.calib3d.Calib3d
import org.opencv.core.Point
import org.opencv.core.MatOfPoint2f
import org.opencv.core.DMatch
import org.opencv.core.Core

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
    // RANSAC Debug-Informationen
    private val _ransacStats = MutableStateFlow<RANSACStats?>(null)
    val ransacStats: StateFlow<RANSACStats?> = _ransacStats.asStateFlow()
    val featureMappingEnabled: StateFlow<Boolean> = _featureMappingEnabled.asStateFlow()

    // Feature detection
    private var detector: AKAZE? = null
    private var matcher: DescriptorMatcher? = null
    private val featuresCache = mutableMapOf<String, LandmarkFeatures>()
    private val detectionHistory = mutableMapOf<String, DetectionHistory>()

    // Config
    private var autoAdvanceThreshold = 0.65f
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

            // Schritt 1: KNN Matching mit Lowe's Ratio Test
            val knnMatches = mutableListOf<MatOfDMatch>()
            matcher?.knnMatch(frame.descriptors, landmark.descriptors, knnMatches, 2)

            val goodDMatches = mutableListOf<DMatch>()
            var totalDistance = 0f

            knnMatches.forEach { match ->
                val arr = match.toArray()
                if (arr.size >= 2) {
                    val m = arr[0]
                    val n = arr[1]
                    if (m.distance < RATIO_THRESHOLD * n.distance) {
                        goodDMatches.add(m)
                        totalDistance += m.distance
                    }
                }
            }

            // Cleanup KNN matches
            knnMatches.forEach { it.release() }

            // Schritt 2: Prüfe ob RANSAC möglich ist
            if (goodDMatches.size < MIN_MATCHES_FOR_RANSAC) {
                // Zu wenige Matches für RANSAC → Fallback auf einfache Zählung
                Log.d(TAG, "Landmark ${landmark.id}: ${goodDMatches.size} matches (zu wenig für RANSAC)")
                return createMatchFromBasicCount(landmark.id, goodDMatches.size, totalDistance)
            }

            // Schritt 3: RANSAC Homographie-Schätzung
            val ransacResult = estimateHomographyRANSAC(
                frame.keypoints,
                landmark.keypoints,
                goodDMatches
            )

            if (ransacResult == null) {
                // RANSAC fehlgeschlagen → Fallback
                Log.d(TAG, "Landmark ${landmark.id}: RANSAC failed, using basic count")
                return createMatchFromBasicCount(landmark.id, goodDMatches.size, totalDistance)
            }

            // Schritt 4: Berechne geometrisch validierte Confidence
            val geometricConfidence = calculateGeometricConfidence(ransacResult)

            // Cleanup
            ransacResult.homography.release()

            val landmarkData = getLandmarks().find { it.id == landmark.id }
                ?: RouteLandmarkData(landmark.id, landmark.id)

            // Publiziere RANSAC Stats für UI
            val inlierRatio = ransacResult.inliers.size.toFloat() / goodDMatches.size
            _ransacStats.value = RANSACStats(
                landmarkId = landmark.id,
                landmarkName = landmarkData.name,
                totalMatches = goodDMatches.size,
                inliers = ransacResult.inliers.size,
                outliers = ransacResult.outliers.size,
                inlierRatio = inlierRatio,
                reprojectionError = ransacResult.reprojectionError,
                confidence = geometricConfidence,
                usedRANSAC = true
            )

            Log.d(TAG, "Landmark ${landmark.id}: ${ransacResult.inliers.size} inliers (${goodDMatches.size} total), " +
                    "confidence=${(geometricConfidence * 100).toInt()}%, reproj_error=${"%.2f".format(ransacResult.reprojectionError)}")

            return LandmarkMatch(
                landmark = landmarkData,
                matchCount = ransacResult.inliers.size,
                confidence = geometricConfidence,
                distance = ransacResult.avgDistance
            )

        } catch (e: Exception) {
            Log.e(TAG, "Feature matching failed for ${landmark.id}", e)
            return createEmptyMatch(landmark.id)
        }
    }

    private fun estimateHomographyRANSAC(
        frameKeypoints: MatOfKeyPoint,
        landmarkKeypoints: MatOfKeyPoint,
        matches: List<DMatch>
    ): RANSACResult? {
        try {
            if (matches.size < MIN_MATCHES_FOR_RANSAC) return null

            // Extrahiere Punkt-Koordinaten aus Keypoints
            val framePoints = mutableListOf<Point>()
            val landmarkPoints = mutableListOf<Point>()

            val frameKpArray = frameKeypoints.toArray()
            val landmarkKpArray = landmarkKeypoints.toArray()

            matches.forEach { match ->
                if (match.queryIdx < frameKpArray.size && match.trainIdx < landmarkKpArray.size) {
                    framePoints.add(frameKpArray[match.queryIdx].pt)
                    landmarkPoints.add(landmarkKpArray[match.trainIdx].pt)
                }
            }

            if (framePoints.size < MIN_MATCHES_FOR_RANSAC) return null

            val srcPoints = MatOfPoint2f(*framePoints.toTypedArray())
            val dstPoints = MatOfPoint2f(*landmarkPoints.toTypedArray())

            // RANSAC Homographie-Schätzung
            val mask = Mat()
            val homography = Calib3d.findHomography(
                srcPoints,
                dstPoints,
                Calib3d.RANSAC,
                RANSAC_THRESHOLD,
                mask,
                RANSAC_MAX_ITERATIONS,
                RANSAC_CONFIDENCE
            )

            if (homography.empty()) {
                srcPoints.release()
                dstPoints.release()
                mask.release()
                return null
            }

            // Separiere Inliers und Outliers basierend auf RANSAC Mask
            val inliers = mutableListOf<DMatch>()
            val outliers = mutableListOf<DMatch>()
            var totalDistance = 0f

            val maskArray = ByteArray(mask.rows())
            mask.get(0, 0, maskArray)

            matches.forEachIndexed { index, match ->
                if (index < maskArray.size) {
                    if (maskArray[index].toInt() == 1) {
                        inliers.add(match)
                        totalDistance += match.distance
                    } else {
                        outliers.add(match)
                    }
                }
            }

            val avgDistance = if (inliers.isNotEmpty()) {
                totalDistance / inliers.size
            } else Float.MAX_VALUE

            // Berechne Reprojection Error
            val reprojError = calculateReprojectionError(
                srcPoints, dstPoints, homography, maskArray
            )

            // Cleanup
            srcPoints.release()
            dstPoints.release()
            mask.release()

            return RANSACResult(
                homography = homography,
                inliers = inliers,
                outliers = outliers,
                avgDistance = avgDistance,
                reprojectionError = reprojError
            )

        } catch (e: Exception) {
            Log.e(TAG, "RANSAC estimation failed", e)
            return null
        }
    }

    private fun calculateReprojectionError(
        srcPoints: MatOfPoint2f,
        dstPoints: MatOfPoint2f,
        homography: Mat,
        mask: ByteArray
    ): Double {
        return try {
            val projectedPoints = MatOfPoint2f()
            Core.perspectiveTransform(srcPoints, projectedPoints, homography)

            val src = srcPoints.toArray()
            val dst = dstPoints.toArray()
            val proj = projectedPoints.toArray()

            var totalError = 0.0
            var inlierCount = 0

            mask.forEachIndexed { index, isInlier ->
                if (index < src.size && index < dst.size && index < proj.size) {
                    if (isInlier.toInt() == 1) {
                        val dx = dst[index].x - proj[index].x
                        val dy = dst[index].y - proj[index].y
                        totalError += kotlin.math.sqrt(dx * dx + dy * dy)
                        inlierCount++
                    }
                }
            }

            projectedPoints.release()

            if (inlierCount > 0) totalError / inlierCount else Double.MAX_VALUE

        } catch (e: Exception) {
            Log.e(TAG, "Reprojection error calculation failed", e)
            Double.MAX_VALUE
        }
    }

    private fun calculateGeometricConfidence(result: RANSACResult): Float {
        val totalMatches = result.inliers.size + result.outliers.size
        if (totalMatches == 0) return 0f

        // Komponente 1: Inlier Ratio (wie viele Matches sind geometrisch konsistent?)
        val inlierRatio = result.inliers.size.toFloat() / totalMatches
        val inlierScore = inlierRatio.coerceIn(0f, 1f)

        // Komponente 2: Absolute Anzahl Inliers (mindestens 15 für volle Punktzahl)
        val countScore = (result.inliers.size.toFloat() / 15f).coerceAtMost(1f)

        // Komponente 3: Geometrische Qualität (niedriger Reprojection Error ist besser)
        val errorScore = if (result.reprojectionError != Double.MAX_VALUE) {
            // Guter Error: < 2 Pixel → Score 1.0
            // Schlechter Error: > 10 Pixel → Score 0.0
            val normalizedError = (result.reprojectionError / 10.0).coerceIn(0.0, 1.0)
            (1.0 - normalizedError).toFloat()
        } else 0f

        // Komponente 4: Feature Distance Quality
        val distanceScore = if (result.avgDistance != Float.MAX_VALUE) {
            // Niedrige Distance = bessere Matches
            (100f / (result.avgDistance + 1f)).coerceIn(0f, 1f)
        } else 0f

        // Gewichtete Kombination
        val confidence = (
                inlierScore * 0.40f +      // 40% Inlier-Ratio (wichtigster Faktor)
                        countScore * 0.30f +       // 30% Absolute Anzahl
                        errorScore * 0.20f +       // 20% Geometrische Qualität
                        distanceScore * 0.10f      // 10% Feature Distance
                ).coerceIn(0f, 1f)

        return confidence
    }

    private fun createMatchFromBasicCount(
        landmarkId: String,
        matchCount: Int,
        totalDistance: Float
    ): LandmarkMatch {
        val confidence = (matchCount.toFloat() / 15f).coerceAtMost(1f) * 0.5f
        val avgDistance = if (matchCount > 0) totalDistance / matchCount else Float.MAX_VALUE

        val landmark = getLandmarks().find { it.id == landmarkId }
            ?: RouteLandmarkData(landmarkId, landmarkId)

        // Publiziere Stats für Nicht-RANSAC Fall
        _ransacStats.value = RANSACStats(
            landmarkId = landmarkId,
            landmarkName = landmark.name,
            totalMatches = matchCount,
            inliers = 0,
            outliers = 0,
            inlierRatio = 0f,
            reprojectionError = Double.MAX_VALUE,
            confidence = confidence,
            usedRANSAC = false
        )

        return LandmarkMatch(landmark, matchCount, confidence, avgDistance)
    }

    private fun analyzeProgression(matches: List<LandmarkMatch>) {
        if (matches.isEmpty()) {
            handleNoDetection()
            return
        }

        val current = _currentStep.value
        val expectedIds = getExpectedLandmarks(current).map { it.id }.toSet()
        val strongMatch = matches.firstOrNull {
            it.landmark.id in expectedIds && it.confidence >= autoAdvanceThreshold &&
                    it.matchCount >= 6
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

    data class RANSACResult(
        val homography: Mat,
        val inliers: List<DMatch>,
        val outliers: List<DMatch>,
        val avgDistance: Float,
        val reprojectionError: Double
    )

    data class RANSACStats(
        val landmarkId: String,
        val landmarkName: String,
        val totalMatches: Int,
        val inliers: Int,
        val outliers: Int,
        val inlierRatio: Float,
        val reprojectionError: Double,
        val confidence: Float,
        val usedRANSAC: Boolean,
        val timestamp: Long = System.currentTimeMillis()
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

        // RANSAC Konfiguration
        private const val MIN_MATCHES_FOR_RANSAC = 4
        private const val RANSAC_THRESHOLD = 3.0  // Pixel Reprojection Error
        private const val RANSAC_MAX_ITERATIONS = 2000
        private const val RANSAC_CONFIDENCE = 0.995
    }
}