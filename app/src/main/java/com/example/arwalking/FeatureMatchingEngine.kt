package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Size
import org.opencv.features2d.ORB
import org.opencv.features2d.BFMatcher
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import kotlin.math.sqrt

/**
 * Echte Feature-Matching Engine mit OpenCV ORB Features
 */
class FeatureMatchingEngine(private val context: Context) {

    private val TAG = "FeatureMatchingEngine"
    private val orb = ORB.create(500) // Maximal 500 Features pro Bild
    private val matcher = BFMatcher.create(2, false) // NORM_HAMMING = 2, crossCheck = false

    // Cache für Landmark-Features
    private val landmarkFeatures = mutableMapOf<String, LandmarkFeatures>()
    
    // Callback für erfolgreiche Landmarken-Erkennung
    private var onLandmarkRecognizedCallback: ((FeatureMatchResult) -> Unit)? = null
    
    // Tracking für bereits erkannte Landmarks um Duplikate zu vermeiden
    private val recognizedLandmarks = mutableSetOf<String>()
    private var lastRecognitionTime = 0L
    private val recognitionCooldownMs = 2000L // 2 Sekunden Cooldown zwischen Erkennungen

    data class LandmarkFeatures(
        val keypoints: MatOfKeyPoint,
        val descriptors: Mat,
        val landmark: ProcessedLandmark
    )

    data class MatchedKeypoints(
        val frameKeypoints: List<KeyPoint>,
        val landmarkKeypoints: List<KeyPoint>,
        val matches: List<DMatch>
    )

    init {
        Log.i(TAG, "FeatureMatchingEngine initialized with ORB detector")
    }
    
    /**
     * Setzt den Callback für erfolgreiche Landmarken-Erkennung
     */
    fun setOnLandmarkRecognizedCallback(callback: (FeatureMatchResult) -> Unit) {
        onLandmarkRecognizedCallback = callback
        Log.d(TAG, "Landmark recognition callback set")
    }
    
    /**
     * Entfernt den Callback für Landmarken-Erkennung
     */
    fun clearLandmarkRecognizedCallback() {
        onLandmarkRecognizedCallback = null
        Log.d(TAG, "Landmark recognition callback cleared")
    }
    
    /**
     * Setzt das Tracking für erkannte Landmarks zurück
     */
    fun resetLandmarkTracking() {
        recognizedLandmarks.clear()
        lastRecognitionTime = 0L
        Log.d(TAG, "Landmark tracking reset")
    }

    /**
     * Verarbeitet einen Kamera-Frame und findet Matches
     */
    fun processFrame(frame: Mat): List<FeatureMatchResult> {
        var frameKeypoints: MatOfKeyPoint? = null
        var frameDescriptors: Mat? = null
        var mask: Mat? = null

        return try {
            if (landmarkFeatures.isEmpty()) {
                Log.d(TAG, "Keine Landmark-Features geladen")
                return emptyList()
            }

            // Extrahiere Features aus dem aktuellen Frame
            frameKeypoints = MatOfKeyPoint()
            frameDescriptors = Mat()
            mask = Mat()

            orb.detectAndCompute(frame, mask, frameKeypoints, frameDescriptors)

            if (frameDescriptors.rows() == 0) {
                Log.d(TAG, "Keine Features im Frame gefunden")
                return emptyList()
            }

            val matches = mutableListOf<FeatureMatchResult>()

            // Vergleiche mit allen geladenen Landmarks
            for ((landmarkId, landmarkFeature) in landmarkFeatures) {
                val matchResult = matchWithLandmarkImproved(frameKeypoints, frameDescriptors, landmarkFeature, Size(frame.cols().toDouble(), frame.rows().toDouble()))

                if (matchResult != null && matchResult.confidence > 0.2f) { // Reduzierte Mindest-Confidence
                    matches.add(matchResult)
                    
                    // Prüfe ob dies eine erfolgreiche Erkennung ist (hohe Confidence)
                    if (matchResult.confidence >= 0.7f) {
                        handleSuccessfulLandmarkRecognition(matchResult)
                    }
                }
            }

            // Sortiere nach Confidence (beste zuerst) und gib zurück
            matches.sortedByDescending { it.confidence }

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Frame-Processing: ${e.message}")
            emptyList()
        } finally {
            // Memory-Cleanup
            frameDescriptors?.release()
            mask?.release()
            // frameKeypoints wird automatisch von OpenCV verwaltet
        }
    }
    
    /**
     * Behandelt eine erfolgreiche Landmarken-Erkennung
     * Löst AR-Positionierung und Navigationsupdate aus
     */
    private fun handleSuccessfulLandmarkRecognition(matchResult: FeatureMatchResult) {
        val currentTime = System.currentTimeMillis()
        val landmarkId = matchResult.landmarkId
        
        // Prüfe Cooldown um Spam zu vermeiden
        if (currentTime - lastRecognitionTime < recognitionCooldownMs) {
            Log.v(TAG, "Landmark recognition in cooldown period, skipping: $landmarkId")
            return
        }
        
        // Prüfe ob diese Landmarke bereits kürzlich erkannt wurde
        if (recognizedLandmarks.contains(landmarkId)) {
            Log.v(TAG, "Landmark already recognized recently, skipping: $landmarkId")
            return
        }
        
        Log.i(TAG, "=== SUCCESSFUL LANDMARK RECOGNITION ===")
        Log.i(TAG, "Landmark: $landmarkId")
        Log.i(TAG, "Confidence: ${matchResult.confidence}")
        Log.i(TAG, "Screen Position: ${matchResult.screenPosition}")
        Log.i(TAG, "=======================================")
        
        // Markiere Landmarke als erkannt
        recognizedLandmarks.add(landmarkId)
        lastRecognitionTime = currentTime
        
        // 1. AR-Positionierung wird automatisch durch AR3DArrowOverlay gehandhabt
        //    (Die Komponente reagiert auf die FeatureMatchResult Liste)
        
        // 2. Navigationsschritte aktualisieren über Callback
        onLandmarkRecognizedCallback?.invoke(matchResult)
        
        Log.d(TAG, "Landmark recognition handled successfully")
    }

    /**
     * Verbessertes Feature-Matching mit geometrischer Validierung
     */
    private fun matchWithLandmarkImproved(frameKeypoints: MatOfKeyPoint, frameDescriptors: Mat, landmarkFeature: LandmarkFeatures, frameSize: Size): FeatureMatchResult? {
        var matchesMatOfDMatch: MatOfDMatch? = null

        return try {
            if (frameDescriptors.rows() == 0 || landmarkFeature.descriptors.rows() == 0) {
                return null
            }

            // 1. Brute-Force Matching
            matchesMatOfDMatch = MatOfDMatch()
            matcher.match(frameDescriptors, landmarkFeature.descriptors, matchesMatOfDMatch)

            val allMatches = matchesMatOfDMatch.toArray().toList()
            if (allMatches.isEmpty()) return null

            // 2. Filtere gute Matches mit adaptivem Threshold
            val sortedMatches = allMatches.sortedBy { it.distance }
            val medianDistance = if (sortedMatches.isNotEmpty()) {
                sortedMatches[sortedMatches.size / 2].distance
            } else {
                100f
            }
            val threshold = (medianDistance * 0.7f).coerceAtMost(50f) // Adaptiver Threshold

            val goodMatches = sortedMatches.filter { it.distance < threshold }

            if (goodMatches.size < 4) { // Mindestens 4 Matches für Homographie
                Log.v(TAG, "Landmark ${landmarkFeature.landmark.id}: Zu wenige gute Matches (${goodMatches.size})")
                return null
            }

            // 3. Geometrische Validierung mit Homographie
            val matchedKeypoints = extractMatchedKeypoints(frameKeypoints, landmarkFeature.keypoints, goodMatches)
            val (confidence, screenPosition) = validateGeometry(matchedKeypoints, frameSize)

            if (confidence > 0.1f) {
                Log.v(TAG, "Landmark ${landmarkFeature.landmark.id}: ${goodMatches.size} gute Matches, Confidence: $confidence")

                FeatureMatchResult(
                    landmarkId = landmarkFeature.landmark.id,
                    confidence = confidence,
                    landmark = landmarkFeature.landmark,
                    matchCount = goodMatches.size,
                    screenPosition = screenPosition
                )
            } else {
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim verbesserten Landmark-Matching: ${e.message}")
            null
        } finally {
            // Memory-Cleanup
            matchesMatOfDMatch?.release()
        }
    }

    /**
     * Extrahiert gematchte Keypoint-Paare
     */
    private fun extractMatchedKeypoints(frameKeypoints: MatOfKeyPoint, landmarkKeypoints: MatOfKeyPoint, matches: List<DMatch>): MatchedKeypoints {
        val frameKpts = frameKeypoints.toArray()
        val landmarkKpts = landmarkKeypoints.toArray()

        val matchedFrameKpts = mutableListOf<KeyPoint>()
        val matchedLandmarkKpts = mutableListOf<KeyPoint>()
        val validMatches = mutableListOf<DMatch>()

        matches.forEach { match ->
            // Sichere Index-Überprüfung
            if (match.queryIdx >= 0 && match.queryIdx < frameKpts.size &&
                match.trainIdx >= 0 && match.trainIdx < landmarkKpts.size) {
                matchedFrameKpts.add(frameKpts[match.queryIdx])
                matchedLandmarkKpts.add(landmarkKpts[match.trainIdx])
                validMatches.add(match)
            } else {
                Log.w(TAG, "Invalid match indices: queryIdx=${match.queryIdx}, trainIdx=${match.trainIdx}, frameSize=${frameKpts.size}, landmarkSize=${landmarkKpts.size}")
            }
        }

        return MatchedKeypoints(matchedFrameKpts, matchedLandmarkKpts, validMatches)
    }

    /**
     * Validiert Matches geometrisch mit Homographie
     */
    private fun validateGeometry(matchedKeypoints: MatchedKeypoints, frameSize: Size): Pair<Float, PointF?> {
        var framePoints: MatOfPoint2f? = null
        var landmarkPoints: MatOfPoint2f? = null
        var mask: Mat? = null
        var homography: Mat? = null

        return try {
            if (matchedKeypoints.frameKeypoints.size < 4) {
                return Pair(0f, null)
            }

            // Konvertiere Keypoints zu OpenCV Points
            framePoints = MatOfPoint2f()
            landmarkPoints = MatOfPoint2f()

            val framePointsArray = matchedKeypoints.frameKeypoints.map { Point(it.pt.x, it.pt.y) }.toTypedArray()
            val landmarkPointsArray = matchedKeypoints.landmarkKeypoints.map { Point(it.pt.x, it.pt.y) }.toTypedArray()

            // FIXED: Use fromList instead of fromArray
            framePoints.fromList(framePointsArray.toList())
            landmarkPoints.fromList(landmarkPointsArray.toList())

            // Berechne Homographie mit RANSAC
            mask = Mat()
            homography = Calib3d.findHomography(landmarkPoints, framePoints, Calib3d.RANSAC, 3.0, mask)

            if (homography.empty()) {
                return Pair(0f, null)
            }

            // FIXED: Correct way to handle mask data
            val maskSize = (mask.total() * mask.channels).toInt()
            val maskData = ByteArray(maskSize)
            mask.get(0, 0, maskData)

            if (maskData.isEmpty()) {
                return Pair(0f, null)
            }

            val inlierCount = maskData.count { it > 0 }
            val inlierRatio = if (matchedKeypoints.frameKeypoints.isNotEmpty()) {
                inlierCount.toFloat() / matchedKeypoints.frameKeypoints.size
            } else {
                0f
            }

            // Berechne Confidence basierend auf Inlier-Ratio und Match-Qualität
            val matchQuality = if (matchedKeypoints.matches.isNotEmpty()) {
                1f - (matchedKeypoints.matches.map { it.distance }.average().toFloat() / 100f).coerceIn(0f, 1f)
            } else {
                0f
            }
            val confidence = (inlierRatio * 0.7f + matchQuality * 0.3f).coerceIn(0f, 1f)

            // Berechne Schwerpunkt der Inlier-Keypoints (sichere Indizierung)
            val inlierFramePoints = matchedKeypoints.frameKeypoints.filterIndexed { index, _ ->
                index < maskData.size && maskData[index] > 0
            }

            val screenPosition = if (inlierFramePoints.isNotEmpty()) {
                val avgX = inlierFramePoints.map { it.pt.x }.average().toFloat()
                val avgY = inlierFramePoints.map { it.pt.y }.average().toFloat()
                PointF(avgX, avgY)
            } else {
                null
            }

            Pair(confidence, screenPosition)

        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei geometrischer Validierung: ${e.message}")
            Pair(0f, null)
        } finally {
            // Memory-Cleanup
            framePoints?.release()
            landmarkPoints?.release()
            mask?.release()
            homography?.release()
        }
    }

    /**
     * Lädt Landmark-Features aus Assets
     */
    fun loadLandmarkFeatures(landmarks: List<ProcessedLandmark>) {
        Log.i(TAG, "Lade Features für ${landmarks.size} Landmarks...")

        landmarks.forEach { landmark ->
            try {
                val bitmap = loadLandmarkImage(landmark.id)
                if (bitmap != null) {
                    val features = extractFeaturesFromBitmap(bitmap, landmark)
                    if (features != null) {
                        landmarkFeatures[landmark.id] = features
                        Log.d(TAG, "Features geladen für Landmark: ${landmark.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden von Landmark ${landmark.id}: ${e.message}")
            }
        }

        Log.i(TAG, "Features geladen für ${landmarkFeatures.size} von ${landmarks.size} Landmarks")
    }

    /**
     * Lädt ein Landmark-Bild aus den Assets basierend auf der Landmark-ID
     */
    private fun loadLandmarkImage(landmarkId: String): Bitmap? {
        return try {
            // Versuche verschiedene Bildformate für die neue ID-Struktur (z.B. "PT-1-566")
            val possiblePaths = listOf(
                "landmark_images/$landmarkId.jpg",
                "landmark_images/$landmarkId.png",
                "landmark_images/${landmarkId.replace("-", "_")}.jpg", // PT_1_566.jpg
                "landmark_images/${landmarkId.replace("-", "_")}.png",
                "landmarks/$landmarkId.jpg",
                "landmarks/$landmarkId.png",
                "images/$landmarkId.jpg",
                "images/$landmarkId.png"
            )

            for (path in possiblePaths) {
                try {
                    context.assets.open(path).use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            Log.d(TAG, "Landmark-Bild geladen: $path")
                            return bitmap
                        }
                    }
                } catch (e: Exception) {
                    // Nächsten Pfad versuchen
                }
            }

            Log.w(TAG, "Kein Bild gefunden für Landmark: $landmarkId")
            null

        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Landmark-Bildes $landmarkId: ${e.message}")
            null
        }
    }

    /**
     * Extrahiert Features aus einem Bitmap
     */
    private fun extractFeaturesFromBitmap(bitmap: Bitmap, landmark: ProcessedLandmark): LandmarkFeatures? {
        var mat: Mat? = null
        var grayMat: Mat? = null
        var mask: Mat? = null

        return try {
            // Konvertiere Bitmap zu OpenCV Mat
            mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Konvertiere zu Graustufen falls nötig
            grayMat = if (mat.channels > 1) {
                val gray = Mat()
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)
                gray
            } else {
                mat.clone()
            }

            // Extrahiere ORB Features
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            mask = Mat()

            orb.detectAndCompute(grayMat, mask, keypoints, descriptors)

            if (descriptors.rows() > 0) {
                Log.d(TAG, "Extrahiert ${keypoints.toArray().size} Features für ${landmark.id}")
                LandmarkFeatures(keypoints, descriptors, landmark)
            } else {
                Log.w(TAG, "Keine Features extrahiert für ${landmark.id}")
                // Cleanup wenn keine Features gefunden
                keypoints.release()
                descriptors.release()
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Feature-Extraktion für ${landmark.id}: ${e.message}")
            null
        } finally {
            // Memory-Cleanup (nur temporäre Mats)
            mat?.release()
            if (grayMat != mat) { // Nur freigeben wenn es ein neues Mat ist
                grayMat?.release()
            }
            mask?.release()
        }
    }

    /**
     * Öffentliche API für Feature-Extraktion
     */
    fun extractFeatures(bitmap: Bitmap): LandmarkFeatures? {
        val dummyLandmark = ProcessedLandmark("temp", "temp")
        return extractFeaturesFromBitmap(bitmap, dummyLandmark)
    }

    /**
     * Hauptmethode für Feature-Matching (verwendet processFrame intern)
     */
    fun matchFeatures(frame: Mat, landmarks: List<ProcessedLandmark>): List<FeatureMatchResult> {
        return processFrame(frame)
    }

    /**
     * Cleanup-Methode für Memory-Management
     */
    fun cleanup() {
        try {
            Log.i(TAG, "Cleaning up FeatureMatchingEngine resources...")

            // Cleanup aller gespeicherten Landmark-Features
            landmarkFeatures.values.forEach { landmarkFeature ->
                try {
                    landmarkFeature.keypoints.release()
                    landmarkFeature.descriptors.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error cleaning up landmark feature: ${e.message}")
                }
            }
            landmarkFeatures.clear()

            Log.i(TAG, "FeatureMatchingEngine cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    /**
     * Gibt die Anzahl der geladenen Landmarks zurück
     */
    fun getLoadedLandmarkCount(): Int = landmarkFeatures.size
}

class LandmarkFeatureStorage(private val context: android.content.Context) {

    private val TAG = "LandmarkFeatureStorage"

    init {
        Log.i(TAG, "LandmarkFeatureStorage initialized")
    }

    fun importLandmarksFromAssets(): Int {
        Log.d(TAG, "importLandmarksFromAssets called")
        // Hier könnten wir Assets scannen, aber das macht die FeatureMatchingEngine bereits
        return 0
    }

    /**
     * Lädt route-spezifische Landmarks basierend auf den IDs in der Route
     */
    fun loadRouteSpecificLandmarks(route: com.example.arwalking.data.Route): List<ProcessedLandmark> {
        Log.d(TAG, "loadRouteSpecificLandmarks called")
        val landmarkIds = mutableSetOf<String>()

        // Extrahiere alle Landmark-IDs aus der Route
        route.path.forEach { pathItem ->
            pathItem.routeParts.forEach { routePart ->
                // Hauptlandmark aus landmarkFromInstruction
                routePart.landmarkFromInstruction?.let { landmarkIds.add(it) }

                // Zusätzliche Landmarks aus landmarks-Array
                routePart.landmarks?.forEach { landmark ->
                    landmark.id?.let { landmarkIds.add(it) }
                }
            }
        }

        Log.i(TAG, "Gefunden ${landmarkIds.size} Landmark-IDs in Route: ${landmarkIds.take(3)}")

        return landmarkIds.map { ProcessedLandmark(it, it) }
    }

    fun loadAllLandmarks(): List<ProcessedLandmark> {
        Log.d(TAG, "loadAllLandmarks called - returning empty list (use route-specific loading)")
        return emptyList()
    }

    fun getStorageStats(): StorageStats {
        return StorageStats()
    }

    fun cleanup() {
        Log.d(TAG, "cleanup called")
    }

    fun saveLandmarkFeatures(landmarkId: String, landmark: Any, features: Any, bitmap: android.graphics.Bitmap): Boolean {
        Log.d(TAG, "saveLandmarkFeatures called: $landmarkId")
        return true
    }
}

class ARTrackingSystem {

    private val TAG = "ARTrackingSystem"

    init {
        Log.i(TAG, "ARTrackingSystem initialized (stub)")
    }

    fun resetTracking() {
        Log.d(TAG, "resetTracking called (stub)")
    }

    fun updateTracking(matches: List<FeatureMatchResult>): List<Any> {
        Log.d(TAG, "updateTracking called (stub)")
        return emptyList()
    }
}

data class ProcessedLandmark(
    val id: String,
    val name: String
)

data class FeatureMatchResult(
    val landmarkId: String,
    val confidence: Float,
    val landmark: ProcessedLandmark? = null,
    val matchCount: Int = 0,
    val distance: Float? = null,
    val angle: Float? = null,
    val screenPosition: android.graphics.PointF? = null
)

class StorageStats {
    val landmarkCount: Int = 0

    fun getTotalSizeMB(): Double = 0.0
}