package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.ORB
import org.opencv.features2d.BFMatcher
import org.opencv.imgproc.Imgproc
import com.example.arwalking.ar.SimpleARRenderer

/**
 * Vereinfachte Feature-Matching Engine mit OpenCV ORB Features
 */
class FeatureMatchingEngine(private val context: Context) {

    private val TAG = "FeatureMatchingEngine"
    
    // OpenCV ORB Detector
    private val orb = ORB.create(1000, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20)
    
    // BF Matcher
    private val matcher = BFMatcher.create(Core.NORM_HAMMING, true)
    
    // Cache für Landmark-Features
    private val landmarkFeatures = mutableMapOf<String, LandmarkFeatures>()
    
    // AR-Renderer für Snapchat-Style Tracking
    private val arRenderer = SimpleARRenderer()
    
    // Status
    private var isInitialized = false
    private var frameCounter = 0
    private var lastFrameHash = 0

    data class LandmarkFeatures(
        val landmarkId: String,
        val keypoints: MatOfKeyPoint,
        val descriptors: Mat,
        val originalSize: Size
    )

    init {
        Log.i(TAG, "🚀 FeatureMatchingEngine initialisiert")
    }

    /**
     * Initialisiert die Engine
     */
    suspend fun initialize(): Boolean {
        return try {
            Log.i(TAG, "🔄 Starte Engine-Initialisierung...")
            
            val availableLandmarks = getAvailableLandmarkIds()
            Log.i(TAG, "📋 Gefundene Landmark-IDs: ${availableLandmarks.joinToString(", ")}")
            
            if (availableLandmarks.isEmpty()) {
                Log.e(TAG, "❌ Keine Landmark-Bilder gefunden!")
                return false
            }
            
            var successCount = 0
            for (landmarkId in availableLandmarks) {
                if (loadLandmarkFeaturesSync(landmarkId)) {
                    successCount++
                }
            }
            
            isInitialized = successCount > 0
            Log.i(TAG, "✅ Engine initialisiert: $successCount/${availableLandmarks.size} Landmarks geladen")
            
            isInitialized
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler bei Engine-Initialisierung: ${e.message}", e)
            false
        }
    }

    /**
     * Findet verfügbare Landmark-IDs
     */
    private fun getAvailableLandmarkIds(): List<String> {
        val landmarkIds = mutableListOf<String>()
        
        try {
            val assetFiles = context.assets.list("landmarken_pictures") ?: emptyArray()
            Log.i(TAG, "📁 Scanne landmarken_pictures Ordner...")
            Log.i(TAG, "📁 Gefundene Dateien (${assetFiles.size}): ${assetFiles.joinToString(", ")}")
            
            for (filename in assetFiles) {
                if (filename.endsWith(".jpg") || filename.endsWith(".png")) {
                    val landmarkId = filename.substringBeforeLast(".")
                    landmarkIds.add(landmarkId)
                    Log.d(TAG, "🏷️ Landmark-ID: $landmarkId")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Lesen der Assets: ${e.message}")
        }
        
        return landmarkIds.sorted()
    }

    /**
     * Lädt Features für eine Landmark (synchron)
     */
    private fun loadLandmarkFeaturesSync(landmarkId: String): Boolean {
        return try {
            Log.i(TAG, "🔄 Lade Features für: $landmarkId")
            
            val bitmap = loadLandmarkBitmap(landmarkId)
            if (bitmap == null) {
                Log.w(TAG, "❌ Bitmap nicht gefunden: $landmarkId")
                return false
            }
            
            Log.d(TAG, "✅ Bitmap geladen: ${bitmap.width}x${bitmap.height}")
            
            // Konvertiere zu Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            
            // Zu Graustufen konvertieren - vereinfacht
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            
            // Features extrahieren
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            
            orb.detectAndCompute(grayMat, Mat(), keypoints, descriptors)
            
            val keypointArray = keypoints.toArray()
            Log.d(TAG, "🎯 Features: ${keypointArray.size} Keypoints, ${descriptors.rows()} Descriptors")
            
            if (descriptors.rows() > 0 && keypointArray.isNotEmpty()) {
                val landmarkFeature = LandmarkFeatures(
                    landmarkId = landmarkId,
                    keypoints = keypoints,
                    descriptors = descriptors.clone(),
                    originalSize = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
                )
                
                landmarkFeatures[landmarkId] = landmarkFeature
                Log.i(TAG, "✅ Features gespeichert für: $landmarkId")
                
                // Cleanup
                mat.release()
                grayMat.release()
                descriptors.release()
                
                true
            } else {
                Log.w(TAG, "❌ Keine Features extrahiert für: $landmarkId")
                keypoints.release()
                descriptors.release()
                mat.release()
                grayMat.release()
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Laden von $landmarkId: ${e.message}", e)
            false
        }
    }

    /**
     * Lädt Landmark-Bitmap
     */
    private fun loadLandmarkBitmap(landmarkId: String): Bitmap? {
        val paths = listOf(
            "landmarken_pictures/$landmarkId.jpg",
            "landmarken_pictures/$landmarkId.png"
        )
        
        for (path in paths) {
            try {
                context.assets.open(path).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        Log.d(TAG, "✅ Bitmap geladen: $path")
                        return bitmap
                    }
                }
            } catch (e: Exception) {
                Log.v(TAG, "❌ Pfad nicht gefunden: $path")
            }
        }
        
        return null
    }

    /**
     * Verarbeitet Kamera-Frame
     */
    fun processFrame(frame: Mat): List<FeatureMatchResult> {
        if (!isInitialized) {
            Log.w(TAG, "⚠️ Engine nicht initialisiert")
            return emptyList()
        }
        
        if (landmarkFeatures.isEmpty()) {
            Log.w(TAG, "⚠️ Keine Landmark-Features geladen (${landmarkFeatures.size} Features)")
            return emptyList()
        }
        
        // Frame-Tracking für Debug
        frameCounter++
        val currentFrameHash = frame.hashCode()
        val isNewFrame = currentFrameHash != lastFrameHash
        lastFrameHash = currentFrameHash
        
        Log.d(TAG, "🎥 Frame #$frameCounter (${if (isNewFrame) "NEU" else "GLEICH"}): ${frame.cols()}x${frame.rows()}")
        Log.d(TAG, "🎥 Verarbeite Frame mit ${landmarkFeatures.size} geladenen Landmarks: ${landmarkFeatures.keys.joinToString(", ")}")
        
        if (!isNewFrame) {
            Log.w(TAG, "⚠️ Gleicher Frame wie vorher - möglicherweise Problem mit Kamera-Feed!")
        }
        
        return try {
            
            // Frame-Informationen für AR-Rendering
            Log.v(TAG, "🎥 Frame-Info: ${frame.cols()}x${frame.rows()}")
            
            // Frame zu Graustufen - vereinfacht
            val grayFrame = Mat()
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)
            
            // Frame Features extrahieren
            val frameKeypoints = MatOfKeyPoint()
            val frameDescriptors = Mat()
            
            orb.detectAndCompute(grayFrame, Mat(), frameKeypoints, frameDescriptors)
            
            val frameKeypointArray = frameKeypoints.toArray()
            Log.v(TAG, "🎯 Frame Features: ${frameKeypointArray.size} Keypoints")
            
            if (frameDescriptors.rows() == 0) {
                grayFrame.release()
                frameKeypoints.release()
                frameDescriptors.release()
                return emptyList()
            }
            
            // Matche gegen Landmarks
            val matches = mutableListOf<FeatureMatchResult>()
            
            for ((landmarkId, landmarkFeature) in landmarkFeatures) {
                val matchResult = matchWithLandmark(
                    frameKeypoints, frameDescriptors, landmarkFeature, frame
                )
                
                if (matchResult != null && matchResult.confidence > 0.3f) {
                    matches.add(matchResult)
                    Log.d(TAG, "🎯 Match: $landmarkId (${(matchResult.confidence * 100).toInt()}%)")
                }
            }
            
            // Cleanup
            grayFrame.release()
            frameKeypoints.release()
            frameDescriptors.release()
            
            matches.sortedByDescending { it.confidence }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Frame-Processing Fehler: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Matcht Frame mit Landmark
     */
    private fun matchWithLandmark(
        frameKeypoints: MatOfKeyPoint,
        frameDescriptors: Mat,
        landmarkFeature: LandmarkFeatures,
        frame: Mat
    ): FeatureMatchResult? {
        
        return try {
            val matches = MatOfDMatch()
            matcher.match(frameDescriptors, landmarkFeature.descriptors, matches)
            
            val matchArray = matches.toArray()
            if (matchArray.isEmpty()) {
                matches.release()
                return null
            }
            
            // Filtere gute Matches
            val goodMatches = matchArray.filter { it.distance < 50.0f }
            if (goodMatches.size < 10) {
                matches.release()
                return null
            }
            
            // Berechne Confidence
            val avgDistance = goodMatches.map { it.distance.toDouble() }.average()
            val confidence = (1.0 - (avgDistance / 100.0)).coerceIn(0.0, 1.0).toFloat()
            
            // Berechne Position und sammle Feature-Punkte für AR-Tracking
            val frameKeypointArray = frameKeypoints.toArray()
            val positions = mutableListOf<PointF>()
            
            for (match in goodMatches) {
                if (match.queryIdx < frameKeypointArray.size) {
                    val kp = frameKeypointArray[match.queryIdx]
                    positions.add(PointF(kp.pt.x.toFloat(), kp.pt.y.toFloat()))
                }
            }
            
            val avgPosition = if (positions.isNotEmpty()) {
                PointF(
                    positions.map { it.x }.average().toFloat(),
                    positions.map { it.y }.average().toFloat()
                )
            } else {
                PointF(frame.cols().toFloat() / 2, frame.rows().toFloat() / 2)
            }
            
            // AR-Pose-Estimation für Snapchat-Style Tracking
            var arPose: SimpleARRenderer.SimpleARPose? = null
            var arObject: SimpleARRenderer.SimpleAR3DObject? = null
            
            if (positions.size >= 4 && confidence > 0.3f) {
                try {
                    // Schätze vereinfachte 3D-Pose des Landmarks
                    arPose = arRenderer.estimateSimplePose(positions.take(4), confidence)
                    
                    if (arPose != null && arPose.confidence > 0.3f) {
                        // Berechne AR-Pfeil-Position (zeigt nach rechts als Beispiel)
                        arObject = arRenderer.calculateARObjectPosition(
                            avgPosition, 
                            arPose,
                            navigationDirection = 90f,  // 90° = rechts
                            offsetDistance = 100f
                        )
                        
                        Log.d(TAG, "🎯 AR-Tracking: ${landmarkFeature.landmarkId} - Confidence: ${arPose.confidence}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ AR-Tracking Fehler für ${landmarkFeature.landmarkId}: ${e.message}")
                }
            }
            
            matches.release()
            
            FeatureMatchResult(
                landmarkId = landmarkFeature.landmarkId,
                confidence = confidence,
                matchCount = goodMatches.size,
                position = avgPosition,
                arPose = arPose,
                arObject = arObject
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Matching Fehler mit ${landmarkFeature.landmarkId}: ${e.message}")
            null
        }
    }

    /**
     * Debug-Info
     */
    fun getDebugInfo(): String {
        return "FeatureMatchingEngine: Initialisiert=$isInitialized, Landmarks=${landmarkFeatures.size}, IDs=${landmarkFeatures.keys.sorted().joinToString(", ")}"
    }

    /**
     * Storage-Stats
     */
    fun getStorageStats(): StorageStats {
        return StorageStats(landmarkFeatures.size, 0L)
    }

    /**
     * Legacy-Methoden
     */
    fun importLandmarksFromAssets(): Int = landmarkFeatures.size
    
    fun loadLandmarkFeatures(landmarks: List<ProcessedLandmark>) {
        Log.i(TAG, "Legacy loadLandmarkFeatures aufgerufen")
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        landmarkFeatures.values.forEach { feature ->
            feature.keypoints.release()
            feature.descriptors.release()
        }
        landmarkFeatures.clear()
        // Vereinfachter AR-Renderer braucht kein Cleanup
        isInitialized = false
    }
}

/**
 * Storage-Statistiken Datenklasse
 */
data class StorageStats(
    val landmarkCount: Int,
    val totalSizeBytes: Long
) {
    fun getTotalSizeMB(): Double = totalSizeBytes / (1024.0 * 1024.0)
}