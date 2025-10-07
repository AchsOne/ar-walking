package com.example.arwalking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

/**
 * Schritt 3: Feature-Matching Test
 * Testet ob wir zwei Bilder matchen können
 */
class OpenCvMatchingTest(private val context: Context) {

    private val TAG = "OpenCvMatchingTest"

    // Feature Detector & Matcher
    private val detector = ORB.create(500)
    private val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

    /**
     * Test 1: Matche das gleiche Bild gegen sich selbst (perfektes Match)
     */
    fun testSelfMatching(landmarkId: String = "PT-1-926"): Boolean {
        return try {
            Log.i(TAG, "=== TEST 1: Self-Matching ===")
            Log.i(TAG, "Matche $landmarkId gegen sich selbst")

            val features = extractFeatures(landmarkId)
            if (features == null) {
                Log.e(TAG, "❌ Konnte Features nicht extrahieren")
                return false
            }

            val (keypoints, descriptors) = features

            // Matche gegen sich selbst
            val matches = mutableListOf<MatOfDMatch>()
            matcher.knnMatch(descriptors, descriptors, matches, 2)

            // Filtere gute Matches (Lowe's Ratio Test)
            val goodMatches = mutableListOf<DMatch>()
            matches.forEach { matchPair ->
                val pairArray = matchPair.toArray()
                if (pairArray.size >= 2) {
                    val bestMatch = pairArray[0]
                    val secondBest = pairArray[1]

                    if (bestMatch.distance < 0.75f * secondBest.distance) {
                        goodMatches.add(bestMatch)
                    }
                }
            }


            Log.i(TAG, "Matches gefunden:")
            Log.i(TAG, "  Total Matches: ${matches.size}")
            Log.i(TAG, "  Gute Matches (nach Ratio-Test): ${goodMatches.size}")

            val matchPercentage = (goodMatches.size.toFloat() / keypoints.toArray().size * 100).toInt()
            Log.i(TAG, "  Match-Rate: $matchPercentage%")

            // Cleanup
            keypoints.release()
            descriptors.release()

            if (goodMatches.size < 100) {
                Log.w(TAG, "⚠️ Warnung: Nur ${goodMatches.size} gute Matches bei Self-Matching")
                Log.w(TAG, "Erwartet: > 100 Matches")
                return false
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
     * Test 2: Matche zwei unterschiedliche Landmarks (sollte wenige Matches geben)
     */
    fun testDifferentLandmarks(
        landmarkId1: String = "PT-1-926",
        landmarkId2: String = "PT-1-686"
    ): Boolean {
        return try {
            Log.i(TAG, "=== TEST 2: Unterschiedliche Landmarks ===")
            Log.i(TAG, "Matche $landmarkId1 gegen $landmarkId2")

            val features1 = extractFeatures(landmarkId1)
            val features2 = extractFeatures(landmarkId2)

            if (features1 == null || features2 == null) {
                Log.e(TAG, "❌ Konnte Features nicht extrahieren")
                return false
            }

            // Matche Features
            val matches = mutableListOf<MatOfDMatch>()
            matcher.knnMatch(features1.second, features2.second, matches, 2)

// Filtere gute Matches
            val goodMatches = mutableListOf<DMatch>()
            matches.forEach { matchPair ->
                val pairArray = matchPair.toArray()
                if (pairArray.size >= 2) {
                    val bestMatch = pairArray[0]
                    val secondBest = pairArray[1]
                    if (bestMatch.distance < 0.75f * secondBest.distance) {
                        goodMatches.add(bestMatch)
                    }
                }
            }






            Log.i(TAG, "Matches gefunden:")
            Log.i(TAG, "  Total Matches: ${matches.size}")
            Log.i(TAG, "  Gute Matches: ${goodMatches.size}")

            val matchPercentage = (goodMatches.size.toFloat() / features1.first.toArray().size * 100).toInt()
            Log.i(TAG, "  Match-Rate: $matchPercentage%")

            // Cleanup
            features1.first.release()
            features1.second.release()
            features2.first.release()
            features2.second.release()

            if (goodMatches.size > 50) {
                Log.w(TAG, "⚠️ Warnung: ${goodMatches.size} Matches zwischen unterschiedlichen Landmarks")
                Log.w(TAG, "Das ist zu viel - könnte zu Verwechslungen führen")
            } else {
                Log.i(TAG, "✅ Gute Diskriminierung: Nur ${goodMatches.size} Matches")
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
     * Test 3: Matche alle Landmarks gegen alle (Confusion Matrix)
     */
    fun testMatchingMatrix(): Boolean {
        return try {
            Log.i(TAG, "=== TEST 3: Matching Matrix ===")

            val files = context.assets.list("landmark_images") ?: emptyArray()
            val imageFiles = files.filter {
                it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg")
            }.take(5) // Nur erste 5 für schnelleren Test

            val landmarkIds = imageFiles.map { it.substringBeforeLast(".") }

            Log.i(TAG, "Teste ${landmarkIds.size} Landmarks")
            Log.i(TAG, "")

            // Extrahiere alle Features
            val allFeatures = mutableMapOf<String, Pair<MatOfKeyPoint, Mat>>()
            landmarkIds.forEach { id ->
                val features = extractFeatures(id)
                if (features != null) {
                    allFeatures[id] = features
                }
            }

            // Matching Matrix
            Log.i(TAG, "Matching Matrix (Gute Matches):")
            Log.i(TAG, "━".repeat(60))

            val results = mutableListOf<Triple<String, String, Int>>()

            allFeatures.keys.forEach { id1 ->
                val row = StringBuilder()
                row.append("$id1: ")

                allFeatures.keys.forEach { id2 ->
                    val features1 = allFeatures[id1]!!
                    val features2 = allFeatures[id2]!!

                    val matches = mutableListOf<MatOfDMatch>()
                    matcher.knnMatch(features1.second, features2.second, matches, 2)

                    val goodMatches = matches.count { matchPair ->
                        val pairArray = matchPair.toArray()
                        pairArray.size >= 2 &&
                                pairArray[0].distance < 0.75f * pairArray[1].distance
                    }

                    row.append("$goodMatches ".padStart(5))
                    results.add(Triple(id1, id2, goodMatches))
                }

                Log.i(TAG, row.toString())
            }

            Log.i(TAG, "━".repeat(60))


            // Analyse
            val selfMatches = results.filter { it.first == it.second }.map { it.third }
            val crossMatches = results.filter { it.first != it.second }.map { it.third }

            val avgSelfMatch = selfMatches.average()
            val avgCrossMatch = crossMatches.average()

            Log.i(TAG, "")
            Log.i(TAG, "Analyse:")
            Log.i(TAG, "  ⌀ Self-Matches: ${avgSelfMatch.toInt()}")
            Log.i(TAG, "  ⌀ Cross-Matches: ${avgCrossMatch.toInt()}")
            Log.i(TAG, "  Ratio: ${(avgSelfMatch / avgCrossMatch.coerceAtLeast(1.0)).toInt()}x")

            // Cleanup
            allFeatures.values.forEach { (keypoints, descriptors) ->
                keypoints.release()
                descriptors.release()
            }

            if (avgSelfMatch < avgCrossMatch * 3) {
                Log.w(TAG, "⚠️ Warnung: Self-Matches nicht deutlich höher als Cross-Matches")
                Log.w(TAG, "Diskriminierung könnte verbessert werden")
            }

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
        Log.i(TAG, "║   OpenCV Feature-Matching Tests       ║")
        Log.i(TAG, "╚════════════════════════════════════════╝\n")

        val test1 = testSelfMatching()
        Thread.sleep(500)

        val test2 = testDifferentLandmarks()
        Thread.sleep(500)

        val test3 = testMatchingMatrix()

        Log.i(TAG, "\n╔════════════════════════════════════════╗")
        Log.i(TAG, "║   Test-Ergebnisse:                     ║")
        Log.i(TAG, "║   Test 1 (Self-Match):    ${if (test1) "✅ PASS" else "❌ FAIL"}   ║")
        Log.i(TAG, "║   Test 2 (Different):     ${if (test2) "✅ PASS" else "❌ FAIL"}   ║")
        Log.i(TAG, "║   Test 3 (Matrix):        ${if (test3) "✅ PASS" else "❌ FAIL"}   ║")
        Log.i(TAG, "╚════════════════════════════════════════╝\n")

        return test1 && test2 && test3
    }
}