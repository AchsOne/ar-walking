package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

/**
 * Schritt 2: Feature-Extraktion Test
 * Testet ob wir Features (Keypoints) aus Bildern extrahieren können
 */
class OpenCvFeatureTest(private val context: Context) {

    private val TAG = "OpenCvFeatureTest"

    // ORB Feature Detector (gut für mobile Geräte)
    private val detector = ORB.create(500) // 500 Features pro Bild

    /**
     * Test 1: Extrahiere Features aus einem Bild
     */
    fun testExtractFeatures(landmarkId: String = "PT-1-926"): Boolean {
        return try {
            Log.i(TAG, "=== TEST 1: Feature-Extraktion ===")
            Log.i(TAG, "Lade Bild: $landmarkId")

            // 1. Lade Bild
            val bitmap = loadBitmapFromAssets(landmarkId)
            if (bitmap == null) {
                Log.e(TAG, "❌ Konnte Bitmap nicht laden")
                return false
            }

            // 2. Konvertiere zu Mat
            val colorMat = bitmapToMat(bitmap)

            // 3. Konvertiere zu Graustufen (Features brauchen Graustufen)
            val grayMat = Mat()
            Imgproc.cvtColor(colorMat, grayMat, Imgproc.COLOR_RGB2GRAY)

            // 4. Extrahiere Features
            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()

            detector.detectAndCompute(grayMat, Mat(), keypoints, descriptors)

            // 5. Ergebnisse
            val keypointArray = keypoints.toArray()
            Log.i(TAG, "✅ Gefundene Keypoints: ${keypointArray.size}")
            Log.i(TAG, "✅ Descriptor Matrix: ${descriptors.rows()}x${descriptors.cols()}")

            // Zeige erste 3 Keypoints
            if (keypointArray.isNotEmpty()) {
                Log.i(TAG, "Erste Keypoints:")
                keypointArray.take(3).forEachIndexed { index, kp ->
                    Log.i(TAG, "  ${index + 1}. Position: (${kp.pt.x.toInt()}, ${kp.pt.y.toInt()}), " +
                            "Size: ${kp.size.toInt()}, Angle: ${kp.angle.toInt()}°")
                }
            }

            // Cleanup
            colorMat.release()
            grayMat.release()
            keypoints.release()
            descriptors.release()

            if (keypointArray.size < 50) {
                Log.w(TAG, "⚠️ Warnung: Nur ${keypointArray.size} Features gefunden (< 50)")
                Log.w(TAG, "Das könnte zu wenig für stabiles Matching sein")
            }

            Log.i(TAG, "✅ TEST 1 ERFOLGREICH")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ TEST 1 FEHLER: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Test 2: Extrahiere Features aus allen Landmark-Bildern
     */
    fun testExtractAllLandmarkFeatures(): Boolean {
        return try {
            Log.i(TAG, "=== TEST 2: Features aus allen Landmarks ===")

            val files = context.assets.list("landmark_images") ?: emptyArray()
            val imageFiles = files.filter {
                it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg")
            }

            if (imageFiles.isEmpty()) {
                Log.e(TAG, "❌ Keine Bild-Dateien gefunden")
                return false
            }

            val results = mutableListOf<Pair<String, Int>>()

            imageFiles.forEach { filename ->
                val landmarkId = filename.substringBeforeLast(".")
                val bitmap = loadBitmapFromAssets(landmarkId)

                if (bitmap != null) {
                    val mat = bitmapToMat(bitmap)
                    val grayMat = Mat()
                    Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

                    val keypoints = MatOfKeyPoint()
                    val descriptors = Mat()
                    detector.detectAndCompute(grayMat, Mat(), keypoints, descriptors)

                    val keypointCount = keypoints.toArray().size
                    results.add(landmarkId to keypointCount)

                    Log.i(TAG, "  $landmarkId: $keypointCount Features")

                    mat.release()
                    grayMat.release()
                    keypoints.release()
                    descriptors.release()
                }
            }

            // Statistiken
            val avgFeatures = results.map { it.second }.average()
            val minFeatures = results.minByOrNull { it.second }
            val maxFeatures = results.maxByOrNull { it.second }

            Log.i(TAG, "")
            Log.i(TAG, "Statistiken:")
            Log.i(TAG, "  ⌀ Durchschnitt: ${avgFeatures.toInt()} Features")
            Log.i(TAG, "  ↓ Minimum: ${minFeatures?.second} (${minFeatures?.first})")
            Log.i(TAG, "  ↑ Maximum: ${maxFeatures?.second} (${maxFeatures?.first})")

            if (avgFeatures < 100) {
                Log.w(TAG, "⚠️ Warnung: Durchschnitt < 100 Features")
                Log.w(TAG, "Empfehlung: Erhöhe ORB Feature-Anzahl auf 1000")
            }

            Log.i(TAG, "✅ TEST 2 ERFOLGREICH")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ TEST 2 FEHLER: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Test 3: Vergleiche Feature-Qualität zwischen zwei Bildern
     */
    fun testCompareFeatureQuality(
        landmarkId1: String = "PT-1-926",
        landmarkId2: String = "PT-1-686"
    ): Boolean {
        return try {
            Log.i(TAG, "=== TEST 3: Feature-Qualität Vergleich ===")
            Log.i(TAG, "Vergleiche: $landmarkId1 vs $landmarkId2")

            // Extrahiere Features von beiden Bildern
            val features1 = extractFeatures(landmarkId1)
            val features2 = extractFeatures(landmarkId2)

            if (features1 == null || features2 == null) {
                Log.e(TAG, "❌ Konnte Features nicht extrahieren")
                return false
            }

            Log.i(TAG, "")
            Log.i(TAG, "$landmarkId1:")
            Log.i(TAG, "  Features: ${features1.first.toArray().size}")
            Log.i(TAG, "  Descriptors: ${features1.second.rows()}x${features1.second.cols()}")

            Log.i(TAG, "")
            Log.i(TAG, "$landmarkId2:")
            Log.i(TAG, "  Features: ${features2.first.toArray().size}")
            Log.i(TAG, "  Descriptors: ${features2.second.rows()}x${features2.second.cols()}")

            // Cleanup
            features1.first.release()
            features1.second.release()
            features2.first.release()
            features2.second.release()

            Log.i(TAG, "✅ TEST 3 ERFOLGREICH")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ TEST 3 FEHLER: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // ============= Hilfsmethoden =============

    private fun extractFeatures(landmarkId: String): Pair<MatOfKeyPoint, Mat>? {
        return try {
            val bitmap = loadBitmapFromAssets(landmarkId) ?: return null
            val mat = bitmapToMat(bitmap)
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

            val keypoints = MatOfKeyPoint()
            val descriptors = Mat()
            detector.detectAndCompute(grayMat, Mat(), keypoints, descriptors)

            mat.release()
            grayMat.release()

            Pair(keypoints, descriptors)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Extrahieren von $landmarkId: ${e.message}")
            null
        }
    }

    private fun loadBitmapFromAssets(landmarkId: String): Bitmap? {
        return try {
            val filename = "$landmarkId.jpg"
            val inputStream = context.assets.open("landmark_images/$filename")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden von $landmarkId: ${e.message}")
            null
        }
    }

    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * Führt alle Tests nacheinander aus
     */
    fun runAllTests(): Boolean {
        Log.i(TAG, "\n╔════════════════════════════════════════╗")
        Log.i(TAG, "║   OpenCV Feature-Extraktion Tests     ║")
        Log.i(TAG, "╚════════════════════════════════════════╝\n")

        val test1 = testExtractFeatures()
        Thread.sleep(500)

        val test2 = testExtractAllLandmarkFeatures()
        Thread.sleep(500)

        val test3 = testCompareFeatureQuality()

        Log.i(TAG, "\n╔════════════════════════════════════════╗")
        Log.i(TAG, "║   Test-Ergebnisse:                     ║")
        Log.i(TAG, "║   Test 1 (Extraktion):    ${if (test1) "✅ PASS" else "❌ FAIL"}   ║")
        Log.i(TAG, "║   Test 2 (Alle Bilder):   ${if (test2) "✅ PASS" else "❌ FAIL"}   ║")
        Log.i(TAG, "║   Test 3 (Vergleich):     ${if (test3) "✅ PASS" else "❌ FAIL"}   ║")
        Log.i(TAG, "╚════════════════════════════════════════╝\n")

        return test1 && test2 && test3
    }
}