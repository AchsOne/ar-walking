package com.example.arwalking.vision

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import com.example.arwalking.data.ARNavigationConfig

/**
 * FeatureEngine - Handles ORB feature extraction and matching
 * Implements the core computer vision pipeline for landmark recognition
 */
class FeatureEngine(private val config: ARNavigationConfig) {

    companion object {
        private const val TAG = "FeatureEngine"
        private const val MIN_MATCHES_FOR_HOMOGRAPHY = 4
        private const val DEFAULT_RATIO_TEST_THRESHOLD = 0.75f
    }

    private val orb: ORB by lazy {
        ORB.create(
            config.orbMaxFeatures,
            1.2f, // scaleFactor
            8,    // nLevels
            31,   // edgeThreshold
            0,    // firstLevel
            2,    // WTA_K
            ORB.HARRIS_SCORE,
            31,   // patchSize
            20    // fastThreshold
        )
    }

    private val matcher: BFMatcher by lazy {
        BFMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING, true)
    }

    /**
     * Extract ORB features from an image
     */
    suspend fun extractFeatures(image: Mat): FeatureSet? = withContext(Dispatchers.Default) {
        try {
            val grayImage = if (image.channels() > 1) {
                val gray = Mat()
                Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)
                gray
            } else {
                image
            }

            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()

            orb.detectAndCompute(grayImage, Mat(), keypoints, descriptors)

            if (grayImage != image) {
                grayImage.release()
            }

            if (keypoints.total() > 0 && !descriptors.empty()) {
                FeatureSet(keypoints, descriptors)
            } else {
                Log.w(TAG, "No features extracted from image")
                keypoints.release()
                descriptors.release()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract features", e)
            null
        }
    }

    /**
     * Match features between query and reference images
     */
    suspend fun matchFeatures(
        queryFeatures: FeatureSet,
        referenceFeatures: FeatureSet
    ): MatchingResult? = withContext(Dispatchers.Default) {
        try {
            if (queryFeatures.descriptors.empty() || referenceFeatures.descriptors.empty()) {
                Log.w(TAG, "Empty descriptors provided for matching")
                return@withContext null
            }

            // Use knnMatch instead of match for ratio test
            val matches = mutableListOf<MatOfDMatch>()
            matcher.knnMatch(
                queryFeatures.descriptors,
                referenceFeatures.descriptors,
                matches,
                2 // k=2 for ratio test
            )

            // Apply ratio test (Lowe's ratio test)
            val goodMatches = applyRatioTest(matches)

            if (goodMatches.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
                Log.d(TAG, "Insufficient matches: ${goodMatches.size} < $MIN_MATCHES_FOR_HOMOGRAPHY")
                return@withContext null
            }

            // Extract matched points
            val queryPoints = mutableListOf<Point>()
            val refPoints = mutableListOf<Point>()

            val queryKeypoints = queryFeatures.keypoints.toArray()
            val refKeypoints = referenceFeatures.keypoints.toArray()

            goodMatches.forEach { match ->
                if (match.queryIdx < queryKeypoints.size && match.trainIdx < refKeypoints.size) {
                    queryPoints.add(queryKeypoints[match.queryIdx].pt)
                    refPoints.add(refKeypoints[match.trainIdx].pt)
                }
            }

            if (queryPoints.size < MIN_MATCHES_FOR_HOMOGRAPHY) {
                Log.d(TAG, "Insufficient valid point matches: ${queryPoints.size}")
                return@withContext null
            }

            // Find homography using RANSAC
            val homographyResult = findHomography(queryPoints, refPoints)

            if (homographyResult.homography.empty()) {
                Log.d(TAG, "Failed to compute homography")
                return@withContext null
            }

            MatchingResult(
                matches = goodMatches,
                homography = homographyResult.homography,
                inliers = homographyResult.inliers,
                confidence = calculateConfidence(goodMatches, homographyResult.inliers)
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to match features", e)
            null
        }
    }

    /**
     * Apply Lowe's ratio test to filter good matches
     */
    private fun applyRatioTest(knnMatches: List<MatOfDMatch>): List<DMatch> {
        val goodMatches = mutableListOf<DMatch>()
        val ratioThreshold = config.matcher?.ratio ?: DEFAULT_RATIO_TEST_THRESHOLD

        for (knnMatch in knnMatches) {
            val matches = knnMatch.toArray()

            // Need at least 2 matches for ratio test
            if (matches.size >= 2) {
                val bestMatch = matches[0]
                val secondBestMatch = matches[1]

                // Apply ratio test
                if (bestMatch.distance < ratioThreshold * secondBestMatch.distance) {
                    goodMatches.add(bestMatch)
                }
            } else if (matches.size == 1) {
                // If only one match, accept it if distance is reasonable
                val match = matches[0]
                if (match.distance < 50.0f) { // Reasonable threshold for ORB
                    goodMatches.add(match)
                }
            }
        }

        return goodMatches
    }

    /**
     * Find homography using RANSAC
     */
    private fun findHomography(
        queryPoints: List<Point>,
        refPoints: List<Point>
    ): HomographyResult {
        return try {
            val queryMat = MatOfPoint2f(*queryPoints.toTypedArray())
            val refMat = MatOfPoint2f(*refPoints.toTypedArray())
            val mask = Mat()

            val reprojThreshold = config.ransac?.reprojThreshold?.toDouble() ?: 3.0
            val maxIters = config.ransac?.maxIters ?: 2000
            val confidence = config.ransac?.confidence?.toDouble() ?: 0.995

            val homography = Calib3d.findHomography(
                queryMat,
                refMat,
                Calib3d.RANSAC,
                reprojThreshold,
                mask,
                maxIters,
                confidence
            )

            // Count inliers
            val inliers = if (!mask.empty()) {
                val maskArray = mask.toArray()
                maskArray.count { row -> row.isNotEmpty() && row[0] > 0 }
            } else {
                0
            }

            // Clean up
            mask.release()
            queryMat.release()
            refMat.release()

            HomographyResult(homography, inliers)

        } catch (e: Exception) {
            Log.w(TAG, "Failed to find homography", e)
            HomographyResult(Mat(), 0)
        }
    }

    /**
     * Calculate matching confidence based on matches and inliers
     */
    private fun calculateConfidence(matches: List<DMatch>, inliers: Int): Float {
        if (matches.isEmpty()) return 0f

        val inlierRatio = inliers.toFloat() / matches.size
        val matchQuality = if (matches.isNotEmpty()) {
            matches.take(10).map { 1f / (1f + it.distance) }.average().toFloat()
        } else {
            0f
        }

        // Combine inlier ratio and match quality
        val confidence = (inlierRatio * 0.7f + matchQuality * 0.3f).coerceIn(0f, 1f)

        // Apply minimum threshold based on number of inliers
        val minInlierThreshold = when {
            inliers >= 20 -> confidence
            inliers >= 10 -> confidence * 0.8f
            inliers >= 6 -> confidence * 0.6f
            else -> confidence * 0.4f
        }

        return minInlierThreshold
    }

    /**
     * Resize image for processing
     */
    suspend fun resizeImage(image: Mat): Mat = withContext(Dispatchers.Default) {
        val originalWidth = image.cols()
        val frameResizeWidth = config.frameResizeWidth ?: 640

        if (originalWidth <= frameResizeWidth) {
            return@withContext image.clone()
        }

        val originalHeight = image.rows()
        val aspectRatio = originalHeight.toFloat() / originalWidth.toFloat()
        val newHeight = (frameResizeWidth * aspectRatio).toInt()

        val resized = Mat()
        Imgproc.resize(
            image,
            resized,
            Size(frameResizeWidth.toDouble(), newHeight.toDouble()),
            0.0,
            0.0,
            Imgproc.INTER_LINEAR
        )

        resized
    }

    /**
     * Preprocess image for feature extraction
     */
    suspend fun preprocessImage(image: Mat): Mat = withContext(Dispatchers.Default) {
        try {
            // Convert to grayscale if needed
            val gray = if (image.channels() > 1) {
                val grayMat = Mat()
                Imgproc.cvtColor(image, grayMat, Imgproc.COLOR_BGR2GRAY)
                grayMat
            } else {
                image.clone()
            }

            // Apply histogram equalization to improve contrast
            val equalized = Mat()
            Imgproc.equalizeHist(gray, equalized)

            // Optional: Apply Gaussian blur to reduce noise
            val blurred = Mat()
            Imgproc.GaussianBlur(equalized, blurred, Size(3.0, 3.0), 0.0)

            // Clean up intermediate results
            if (gray != image) {
                gray.release()
            }
            equalized.release()

            blurred

        } catch (e: Exception) {
            Log.e(TAG, "Failed to preprocess image", e)
            image.clone()
        }
    }

    /**
     * Get feature extraction statistics
     */
    fun getFeatureStats(featureSet: FeatureSet): FeatureStats {
        val keypoints = featureSet.keypoints.toArray()

        val responses = keypoints.map { it.response }
        val sizes = keypoints.map { it.size }

        return FeatureStats(
            keypointCount = keypoints.size,
            descriptorDimensions = if (!featureSet.descriptors.empty()) featureSet.descriptors.cols() else 0,
            avgResponse = if (responses.isNotEmpty()) responses.average().toFloat() else 0f,
            maxResponse = responses.maxOrNull() ?: 0f,
            avgSize = if (sizes.isNotEmpty()) sizes.average().toFloat() else 0f,
            maxSize = sizes.maxOrNull() ?: 0f
        )
    }

    /**
     * Validate homography matrix
     */
    fun validateHomography(homography: Mat): Boolean {
        if (homography.empty() || homography.rows() != 3 || homography.cols() != 3) {
            return false
        }

        try {
            // Check determinant is not too small (avoid singular matrices)
            val det = Core.determinant(homography)
            if (Math.abs(det) < 1e-6) {
                return false
            }

            // Check if homography is reasonable (not too distorted)
            val data = DoubleArray(9)
            homography.get(0, 0, data)

            // Check for reasonable scaling
            val scaleX = Math.sqrt(data[0] * data[0] + data[3] * data[3])
            val scaleY = Math.sqrt(data[1] * data[1] + data[4] * data[4])

            return scaleX > 0.1 && scaleX < 10.0 && scaleY > 0.1 && scaleY < 10.0

        } catch (e: Exception) {
            Log.w(TAG, "Error validating homography", e)
            return false
        }
    }

    /**
     * Release resources
     */
    fun release() {
        try {
            orb.release()
            matcher.release()
            Log.d(TAG, "FeatureEngine resources released")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing FeatureEngine resources", e)
        }
    }
}

/**
 * Feature set containing keypoints and descriptors
 */
data class FeatureSet(
    val keypoints: MatOfKeyPoint,
    val descriptors: Mat
) {
    fun release() {
        try {
            keypoints.release()
            descriptors.release()
        } catch (e: Exception) {
            Log.w("FeatureSet", "Error releasing resources", e)
        }
    }
}

/**
 * Result of feature matching
 */
data class MatchingResult(
    val matches: List<DMatch>,
    val homography: Mat,
    val inliers: Int,
    val confidence: Float
) {
    fun release() {
        try {
            homography.release()
        } catch (e: Exception) {
            Log.w("MatchingResult", "Error releasing homography", e)
        }
    }
}

/**
 * Homography calculation result
 */
private data class HomographyResult(
    val homography: Mat,
    val inliers: Int
)

/**
 * Feature extraction statistics
 */
data class FeatureStats(
    val keypointCount: Int,
    val descriptorDimensions: Int,
    val avgResponse: Float,
    val maxResponse: Float,
    val avgSize: Float,
    val maxSize: Float
)