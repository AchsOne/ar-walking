package com.example.arwalking.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.*

/**
 * Kontextuelles Feature-Matching System
 * Nutzt Route-Kontext um falsche Matches zu vermeiden
 */
class ContextualFeatureMatcher(private val context: Context) {

    companion object {
        private const val TAG = "ContextMatcher"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f // Erhöht für bessere Genauigkeit
        private const val STRONG_MATCH_THRESHOLD = 0.8f  // Erhöht für sicherere Matches
        private const val EXCELLENT_MATCH_THRESHOLD = 0.9f // Neuer Threshold für exzellente Matches
    }

    // Cache für Landmark-Signaturen
    private val signatureCache = mutableMapOf<String, LandmarkSignature>()

    /**
     * Initialisiert Matcher - lädt alle Landmark-Signaturen
     */
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Initialisiere Contextual Feature Matcher...")

                val landmarkFiles = context.assets.list("landmark_images") ?: emptyArray()
                val imageFiles = landmarkFiles.filter {
                    it.lowercase().endsWith(".jpg") || it.lowercase().endsWith(".png")
                }

                for (filename in imageFiles) {
                    val signature = createLandmarkSignature(filename)
                    if (signature != null) {
                        signatureCache[signature.landmarkId] = signature
                        Log.d(TAG, "Signatur geladen: ${signature.landmarkId}")
                    }
                }

                Log.i(TAG, "Initialisierung abgeschlossen: ${signatureCache.size} Signaturen")
                signatureCache.isNotEmpty()

            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei Initialisierung: ${e.message}")
                false
            }
        }
    }

    /**
     * Findet den erwarteten Landmark im aktuellen Frame
     *
     * @param cameraFrame Das aktuelle Kamera-Bild
     * @param expectedLandmarkId Der erwartete Landmark aus der Route (z.B. "PT-1-926")
     * @param allowedAlternatives Alternative Landmarks die auch OK wären (z.B. Nachbar-Landmarks)
     * @return Match-Ergebnis mit Confidence
     */
    suspend fun matchExpectedLandmark(
        cameraFrame: Bitmap,
        expectedLandmarkId: String,
        allowedAlternatives: List<String> = emptyList()
    ): MatchResult {
        return withContext(Dispatchers.Default) {
            try {
                Log.i(TAG, "=== Kontextuelles Matching ===")
                Log.i(TAG, "Suche: $expectedLandmarkId")
                if (allowedAlternatives.isNotEmpty()) {
                    Log.i(TAG, "Alternativen: ${allowedAlternatives.joinToString()}")
                }

                // 1. Erstelle Signatur vom Kamera-Frame
                val frameSignature = createFrameSignature(cameraFrame)
                if (frameSignature == null) {
                    Log.e(TAG, "Konnte Frame-Signatur nicht erstellen")
                    return@withContext MatchResult.NoMatch("Frame-Signatur Fehler")
                }

                // 2. Hole erwartete Landmark-Signatur
                val expectedSignature = signatureCache[expectedLandmarkId]
                if (expectedSignature == null) {
                    Log.e(TAG, "Erwarteter Landmark $expectedLandmarkId nicht im Cache")
                    return@withContext MatchResult.NoMatch("Landmark nicht gefunden")
                }

                // 3. Berechne Similarity mit erwartetem Landmark
                val expectedSimilarity = calculateSimilarity(frameSignature, expectedSignature)
                Log.i(TAG, "Similarity mit $expectedLandmarkId: ${(expectedSimilarity * 100).toInt()}%")

                // 4. WICHTIG: Prüfe auch alle ANDEREN Landmarks (Verwechslungsgefahr)
                val confusionCheck = checkForConfusion(
                    frameSignature,
                    expectedLandmarkId,
                    allowedAlternatives
                )

                // 5. Entscheide basierend auf Similarity + Confusion
                return@withContext evaluateMatch(
                    expectedLandmarkId,
                    expectedSimilarity,
                    confusionCheck
                )

            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Matching: ${e.message}")
                MatchResult.NoMatch("Exception: ${e.message}")
            }
        }
    }

    /**
     * Prüft ob Frame mit einem FALSCHEN Landmark verwechselt werden könnte
     */
    private fun checkForConfusion(
        frameSignature: LandmarkSignature,
        expectedLandmarkId: String,
        allowedAlternatives: List<String>
    ): ConfusionCheck {
        val otherMatches = mutableListOf<Pair<String, Float>>()

        for ((landmarkId, signature) in signatureCache) {
            // Überspringe erwarteten Landmark und erlaubte Alternativen
            if (landmarkId == expectedLandmarkId || landmarkId in allowedAlternatives) {
                continue
            }

            val similarity = calculateSimilarity(frameSignature, signature)
            if (similarity > MIN_CONFIDENCE_THRESHOLD) {
                otherMatches.add(landmarkId to similarity)
                Log.w(TAG, "⚠️ Verwechslungsgefahr: $landmarkId hat ${(similarity * 100).toInt()}%")
            }
        }

        // Sortiere nach höchster Similarity
        otherMatches.sortByDescending { it.second }

        return ConfusionCheck(
            hasConfusion = otherMatches.isNotEmpty(),
            confusingLandmarks = otherMatches
        )
    }

    /**
     * Bewertet ob Match akzeptiert werden sollte
     */
    private fun evaluateMatch(
        expectedLandmarkId: String,
        expectedSimilarity: Float,
        confusionCheck: ConfusionCheck
    ): MatchResult {

        // Fall 1: Erwarteter Landmark hat gute Similarity und keine Verwechslungsgefahr
        if (expectedSimilarity >= STRONG_MATCH_THRESHOLD && !confusionCheck.hasConfusion) {
            Log.i(TAG, "✅ STARKES MATCH: $expectedLandmarkId (${(expectedSimilarity * 100).toInt()}%)")
            return MatchResult.StrongMatch(expectedLandmarkId, expectedSimilarity)
        }

        // Fall 2: Erwarteter Landmark hat OK Similarity, aber andere sind ähnlicher
        if (confusionCheck.hasConfusion) {
            val topConfusion = confusionCheck.confusingLandmarks.first()

            if (topConfusion.second > expectedSimilarity) {
                Log.w(TAG, "❌ VERWECHSLUNG: ${topConfusion.first} (${(topConfusion.second * 100).toInt()}%) ähnlicher als $expectedLandmarkId")
                return MatchResult.Ambiguous(
                    expectedLandmarkId = expectedLandmarkId,
                    expectedSimilarity = expectedSimilarity,
                    confusingLandmark = topConfusion.first,
                    confusingSimilarity = topConfusion.second,
                    message = "${topConfusion.first} ist ähnlicher"
                )
            }

            // Erwarteter ist ähnlicher, aber Unterschied ist klein
            val difference = expectedSimilarity - topConfusion.second
            if (difference < 0.1f) { // Weniger als 10% Unterschied
                Log.w(TAG, "⚠️ UNSICHER: Unterschied zu ${topConfusion.first} nur ${(difference * 100).toInt()}%")
                return MatchResult.WeakMatch(
                    expectedLandmarkId,
                    expectedSimilarity,
                    "Nur ${(difference * 100).toInt()}% besser als ${topConfusion.first}"
                )
            }
        }

        // Fall 3: Erwarteter Landmark passt OK
        if (expectedSimilarity >= MIN_CONFIDENCE_THRESHOLD) {
            Log.i(TAG, "✓ MATCH: $expectedLandmarkId (${(expectedSimilarity * 100).toInt()}%)")
            return MatchResult.WeakMatch(expectedLandmarkId, expectedSimilarity, "Akzeptabel")
        }

        // Fall 4: Kein guter Match
        Log.w(TAG, "❌ KEIN MATCH: $expectedLandmarkId nur ${(expectedSimilarity * 100).toInt()}%")
        return MatchResult.NoMatch("Similarity zu niedrig: ${(expectedSimilarity * 100).toInt()}%")
    }

    /**
     * Erstellt Frame-Signatur aus Kamera-Bild
     */
    private fun createFrameSignature(bitmap: Bitmap): LandmarkSignature? {
        return try {
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)

            LandmarkSignature(
                filename = "camera_frame",
                landmarkId = "frame",
                colorHistogram = createColorHistogram(scaledBitmap),
                textureFeatures = createTextureFeatures(scaledBitmap),
                edgeFeatures = createEdgeFeatures(scaledBitmap),
                spatialFeatures = createSpatialFeatures(scaledBitmap),
                width = bitmap.width,
                height = bitmap.height
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Frame-Signatur: ${e.message}")
            null
        }
    }

    /**
     * Erstellt Landmark-Signatur aus Datei
     */
    private fun createLandmarkSignature(filename: String): LandmarkSignature? {
        return try {
            val bitmap = loadBitmapFromAssets(filename) ?: return null
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)

            LandmarkSignature(
                filename = filename,
                landmarkId = filename.substringBeforeLast('.'),
                colorHistogram = createColorHistogram(scaledBitmap),
                textureFeatures = createTextureFeatures(scaledBitmap),
                edgeFeatures = createEdgeFeatures(scaledBitmap),
                spatialFeatures = createSpatialFeatures(scaledBitmap),
                width = bitmap.width,
                height = bitmap.height
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Signatur-Erstellung: ${e.message}")
            null
        }
    }

    // Feature-Extraktion Methoden (wie in Schritt 4)
    private fun createColorHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(64)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 64
            val g = ((pixel shr 8) and 0xFF) / 64
            val b = (pixel and 0xFF) / 64
            val index = r * 16 + g * 4 + b
            if (index < histogram.size) histogram[index]++
        }
        return histogram
    }

    private fun createTextureFeatures(bitmap: Bitmap): FloatArray {
        val features = FloatArray(16)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 1 until bitmap.height - 1) {
            for (x in 1 until bitmap.width - 1) {
                val center = getGrayValue(pixels[y * bitmap.width + x])
                var pattern = 0

                if (getGrayValue(pixels[(y-1) * bitmap.width + x-1]) > center) pattern = pattern or 1
                if (getGrayValue(pixels[(y-1) * bitmap.width + x]) > center) pattern = pattern or 2
                if (getGrayValue(pixels[(y-1) * bitmap.width + x+1]) > center) pattern = pattern or 4
                if (getGrayValue(pixels[y * bitmap.width + x+1]) > center) pattern = pattern or 8

                if (pattern < features.size) features[pattern]++
            }
        }
        return features
    }

    private fun createEdgeFeatures(bitmap: Bitmap): FloatArray {
        val features = FloatArray(8)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 1 until bitmap.height - 1) {
            for (x in 1 until bitmap.width - 1) {
                val gx = getGrayValue(pixels[y * bitmap.width + x + 1]) -
                        getGrayValue(pixels[y * bitmap.width + x - 1])
                val gy = getGrayValue(pixels[(y + 1) * bitmap.width + x]) -
                        getGrayValue(pixels[(y - 1) * bitmap.width + x])

                val magnitude = sqrt((gx * gx + gy * gy).toFloat())
                if (magnitude > 30) {
                    val angle = atan2(gy.toFloat(), gx.toFloat())
                    val direction = ((angle + PI) / (PI / 4)).toInt() % 8
                    features[direction] += magnitude
                }
            }
        }
        return features
    }

    private fun createSpatialFeatures(bitmap: Bitmap): FloatArray {
        val features = FloatArray(16)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val qWidth = bitmap.width / 4
        val qHeight = bitmap.height / 4

        for (qy in 0 until 4) {
            for (qx in 0 until 4) {
                var brightness = 0f
                var count = 0

                for (y in qy * qHeight until (qy + 1) * qHeight) {
                    for (x in qx * qWidth until (qx + 1) * qWidth) {
                        if (y < bitmap.height && x < bitmap.width) {
                            brightness += getGrayValue(pixels[y * bitmap.width + x])
                            count++
                        }
                    }
                }
                features[qy * 4 + qx] = if (count > 0) brightness / count else 0f
            }
        }
        return features
    }

    private fun calculateSimilarity(sig1: LandmarkSignature, sig2: LandmarkSignature): Float {
        val colorSim = calculateHistogramSimilarity(sig1.colorHistogram, sig2.colorHistogram)
        val textureSim = calculateArraySimilarity(sig1.textureFeatures, sig2.textureFeatures)
        val edgeSim = calculateArraySimilarity(sig1.edgeFeatures, sig2.edgeFeatures)
        val spatialSim = calculateArraySimilarity(sig1.spatialFeatures, sig2.spatialFeatures)

        return (colorSim * 0.3f + textureSim * 0.25f + edgeSim * 0.25f + spatialSim * 0.2f)
    }

    private fun getGrayValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun calculateHistogramSimilarity(hist1: IntArray, hist2: IntArray): Float {
        var intersection = 0
        var union = 0
        for (i in hist1.indices) {
            intersection += minOf(hist1[i], hist2[i])
            union += maxOf(hist1[i], hist2[i])
        }
        return if (union > 0) intersection.toFloat() / union else 0f
    }

    private fun calculateArraySimilarity(arr1: FloatArray, arr2: FloatArray): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in arr1.indices) {
            dotProduct += arr1[i] * arr2[i]
            norm1 += arr1[i] * arr1[i]
            norm2 += arr2[i] * arr2[i]
        }

        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        } else 0f
    }

    private fun loadBitmapFromAssets(filename: String): Bitmap? {
        return try {
            val inputStream = context.assets.open("landmark_images/$filename")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: IOException) {
            null
        }
    }
}

// Datenklassen
data class LandmarkSignature(
    val filename: String,
    val landmarkId: String,
    val colorHistogram: IntArray,
    val textureFeatures: FloatArray,
    val edgeFeatures: FloatArray,
    val spatialFeatures: FloatArray,
    val width: Int,
    val height: Int
)

data class ConfusionCheck(
    val hasConfusion: Boolean,
    val confusingLandmarks: List<Pair<String, Float>> // (LandmarkID, Similarity)
)

/**
 * Match-Ergebnis mit verschiedenen Confidence-Levels
 */
sealed class MatchResult {
    data class StrongMatch(val landmarkId: String, val confidence: Float) : MatchResult()
    data class WeakMatch(val landmarkId: String, val confidence: Float, val warning: String) : MatchResult()
    data class Ambiguous(
        val expectedLandmarkId: String,
        val expectedSimilarity: Float,
        val confusingLandmark: String,
        val confusingSimilarity: Float,
        val message: String
    ) : MatchResult()
    data class NoMatch(val reason: String) : MatchResult()
}