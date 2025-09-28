package com.example.arwalking

import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import com.example.arwalking.screens.CameraNavigation
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.screens.HomeScreen
import com.example.arwalking.screens.LocalNavController
import com.example.arwalking.ui.theme.ARWalkingTheme
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.example.arwalking.BuildConfig
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.*

class MainActivity : ComponentActivity() {

    private lateinit var routeViewModel: RouteViewModel
    private val landmarkFeatures = mutableMapOf<String, LandmarkSignature>()

    private val cameraPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCamera()
            }
        }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun checkCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("FeatureMatchTest", "=== SCHRITT 4: Feature-Matching ===")

        // SCHRITT 4: Erstelle Feature-Signaturen und teste Matching
        initializeFeatureMatching()

        // Rest bleibt unverändert
        routeViewModel = ViewModelProvider(this)[RouteViewModel::class.java]
        routeViewModel.initializeStorage(this)

        enableEdgeToEdge()

        setContent {
            ARWalkingTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ARWalkingApp()
                }
            }
        }

        val navigationRoute = routeViewModel.loadNavigationRoute(this)
        if (navigationRoute != null) {
            routeViewModel.logNavigationRoute(navigationRoute)
            routeViewModel.enableStorageSystemImmediately(this)

            if (BuildConfig.DEBUG) {
                try {
                    val systemValidator = SystemValidator(this)
                    systemValidator.validateSystem(routeViewModel)
                    systemValidator.simulateFeatureMatching(routeViewModel, "prof_ludwig_office")
                } catch (e: Exception) {
                    Log.w("FeatureMatchTest", "SystemValidator Fehler: ${e.message}")
                }
            }
        } else {
            Log.e("FeatureMatchTest", "Fehler beim Laden der Route")
        }
    }

    /**
     * SCHRITT 4: Initialisiert Feature-Matching System
     */
    private fun initializeFeatureMatching() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.i("FeatureMatchTest", "Initialisiere Feature-Matching System...")

                // 1. Lade alle Landmark-Bilder und erstelle Signaturen
                val signatures = createLandmarkSignatures()

                if (signatures.isEmpty()) {
                    Log.w("FeatureMatchTest", "Keine Landmark-Signaturen erstellt")
                    return@launch
                }

                Log.i("FeatureMatchTest", "✅ ${signatures.size} Landmark-Signaturen erstellt")

                // 2. Teste Feature-Matching zwischen den Landmarks
                testFeatureMatching(signatures)

                // 3. Simuliere echtes Kamera-Frame Matching
                simulateCameraFrameMatching(signatures)

                Log.i("FeatureMatchTest", "🎉 Feature-Matching Tests abgeschlossen!")
                Toast.makeText(this@MainActivity, "Feature-Matching bereit", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("FeatureMatchTest", "Fehler bei Feature-Matching Init: ${e.message}")
                Toast.makeText(this@MainActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Erstellt Feature-Signaturen für alle Landmark-Bilder
     */
    private suspend fun createLandmarkSignatures(): List<LandmarkSignature> {
        return withContext(Dispatchers.IO) {
            val signatures = mutableListOf<LandmarkSignature>()

            try {
                val landmarkFiles = assets.list("landmark_images") ?: emptyArray()
                val imageFiles = landmarkFiles.filter { filename ->
                    filename.lowercase().endsWith(".jpg") || filename.lowercase().endsWith(".png")
                }

                Log.i("FeatureMatchTest", "Erstelle Signaturen für ${imageFiles.size} Bilder...")

                for (filename in imageFiles) {
                    val signature = createLandmarkSignature(filename)
                    if (signature != null) {
                        signatures.add(signature)
                        landmarkFeatures[filename] = signature
                        Log.d("FeatureMatchTest", "✅ Signatur erstellt: $filename")
                    }
                }

            } catch (e: Exception) {
                Log.e("FeatureMatchTest", "Fehler beim Erstellen der Signaturen: ${e.message}")
            }

            signatures
        }
    }

    /**
     * Erstellt erweiterte Feature-Signatur für ein Landmark
     */
    private fun createLandmarkSignature(filename: String): LandmarkSignature? {
        return try {
            val bitmap = loadBitmapFromAssets(filename) ?: return null

            // Skaliere Bild für konsistente Verarbeitung
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)

            // Erstelle verschiedene Feature-Typen
            val colorHistogram = createColorHistogram(scaledBitmap)
            val textureFeatures = createTextureFeatures(scaledBitmap)
            val edgeFeatures = createEdgeFeatures(scaledBitmap)
            val spatialFeatures = createSpatialFeatures(scaledBitmap)

            LandmarkSignature(
                filename = filename,
                landmarkId = filename.substringBeforeLast('.'),
                colorHistogram = colorHistogram,
                textureFeatures = textureFeatures,
                edgeFeatures = edgeFeatures,
                spatialFeatures = spatialFeatures,
                width = bitmap.width,
                height = bitmap.height
            )

        } catch (e: Exception) {
            Log.e("FeatureMatchTest", "Fehler bei Signatur-Erstellung für $filename: ${e.message}")
            null
        }
    }

    /**
     * Erstellt Farb-Histogramm (RGB in 4x4x4 Blöcken)
     */
    private fun createColorHistogram(bitmap: Bitmap): IntArray {
        val histogram = IntArray(64) // 4x4x4 für RGB
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 64
            val g = ((pixel shr 8) and 0xFF) / 64
            val b = (pixel and 0xFF) / 64

            val index = r * 16 + g * 4 + b
            if (index < histogram.size) {
                histogram[index]++
            }
        }

        return histogram
    }

    /**
     * Erstellt Textur-Features (Local Binary Pattern-ähnlich)
     */
    private fun createTextureFeatures(bitmap: Bitmap): FloatArray {
        val features = FloatArray(16)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 1 until bitmap.height - 1) {
            for (x in 1 until bitmap.width - 1) {
                val center = getGrayValue(pixels[y * bitmap.width + x])

                var pattern = 0
                val neighbors = intArrayOf(
                    getGrayValue(pixels[(y-1) * bitmap.width + x-1]), // Oben links
                    getGrayValue(pixels[(y-1) * bitmap.width + x]),   // Oben
                    getGrayValue(pixels[(y-1) * bitmap.width + x+1]), // Oben rechts
                    getGrayValue(pixels[y * bitmap.width + x+1])      // Rechts
                )

                for (i in neighbors.indices) {
                    if (neighbors[i] > center) {
                        pattern = pattern or (1 shl i)
                    }
                }

                if (pattern < features.size) {
                    features[pattern]++
                }
            }
        }

        return features
    }

    /**
     * Erstellt Kanten-Features (Gradientenrichtungen)
     */
    private fun createEdgeFeatures(bitmap: Bitmap): FloatArray {
        val features = FloatArray(8) // 8 Richtungen
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 1 until bitmap.height - 1) {
            for (x in 1 until bitmap.width - 1) {
                val gx = getGradientX(pixels, x, y, bitmap.width)
                val gy = getGradientY(pixels, x, y, bitmap.width)

                val magnitude = sqrt(gx * gx + gy * gy)
                if (magnitude > 30) {
                    val angle = atan2(gy, gx)
                    val direction = ((angle + PI) / (PI / 4)).toInt() % 8
                    features[direction] += magnitude.toFloat()
                }
            }
        }

        return features
    }

    /**
     * Erstellt räumliche Features (Quadranten-Statistiken)
     */
    private fun createSpatialFeatures(bitmap: Bitmap): FloatArray {
        val features = FloatArray(16) // 4x4 Quadranten
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

    /**
     * Testet Feature-Matching zwischen Landmarks
     */
    private suspend fun testFeatureMatching(signatures: List<LandmarkSignature>) {
        withContext(Dispatchers.Default) {
            Log.i("FeatureMatchTest", "=== Feature-Matching Tests ===")

            // Teste jede Signatur gegen alle anderen
            for (i in signatures.indices) {
                for (j in i + 1 until signatures.size) {
                    val sig1 = signatures[i]
                    val sig2 = signatures[j]

                    val similarity = calculateSimilarity(sig1, sig2)

                    Log.d("FeatureMatchTest", "Similarity: ${sig1.landmarkId} vs ${sig2.landmarkId} = ${(similarity * 100).toInt()}%")
                }
            }
        }
    }

    /**
     * Simuliert Kamera-Frame Matching
     */
    private suspend fun simulateCameraFrameMatching(signatures: List<LandmarkSignature>) {
        withContext(Dispatchers.Default) {
            Log.i("FeatureMatchTest", "=== Simuliere Kamera-Frame Matching ===")

            // Simuliere: verwende PT-1-926 als "Kamera-Frame"
            val testFrame = signatures.find { it.landmarkId == "PT-1-926" }
            if (testFrame == null) {
                Log.w("FeatureMatchTest", "PT-1-926 nicht gefunden für Simulation")
                return@withContext
            }

            Log.i("FeatureMatchTest", "Simuliere Frame-Matching für: ${testFrame.landmarkId}")

            val matches = mutableListOf<MatchResult>()

            for (signature in signatures) {
                if (signature.landmarkId != testFrame.landmarkId) { // Nicht gegen sich selbst matchen
                    val similarity = calculateSimilarity(testFrame, signature)

                    if (similarity > 0.3f) { // Nur Matches über 30%
                        matches.add(
                            MatchResult(
                                landmarkId = signature.landmarkId,
                                filename = signature.filename,
                                similarity = similarity,
                                matchCount = (similarity * 100).toInt(),
                                distance = (1.0f - similarity) * 20.0f + 2.0f // 2-22 Meter
                            )
                        )
                    }
                }
            }

            // Sortiere nach Similarity
            matches.sortByDescending { it.similarity }

            Log.i("FeatureMatchTest", "🎯 Gefunden: ${matches.size} Matches für ${testFrame.landmarkId}")

            matches.take(3).forEach { match ->
                Log.i("FeatureMatchTest", "  📍 ${match.landmarkId}: ${(match.similarity * 100).toInt()}% (${String.format("%.1f", match.distance)}m)")
            }
        }
    }

    /**
     * Berechnet Ähnlichkeit zwischen zwei Signaturen
     */
    private fun calculateSimilarity(sig1: LandmarkSignature, sig2: LandmarkSignature): Float {
        // Farb-Histogramm Similarity
        val colorSim = calculateHistogramSimilarity(sig1.colorHistogram, sig2.colorHistogram)

        // Textur-Similarity
        val textureSim = calculateArraySimilarity(sig1.textureFeatures, sig2.textureFeatures)

        // Kanten-Similarity
        val edgeSim = calculateArraySimilarity(sig1.edgeFeatures, sig2.edgeFeatures)

        // Räumliche Similarity
        val spatialSim = calculateArraySimilarity(sig1.spatialFeatures, sig2.spatialFeatures)

        // Gewichtete Kombination
        return (colorSim * 0.3f + textureSim * 0.25f + edgeSim * 0.25f + spatialSim * 0.2f)
    }

    // Hilfsfunktionen
    private fun getGrayValue(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun getGradientX(pixels: IntArray, x: Int, y: Int, width: Int): Float {
        val left = getGrayValue(pixels[y * width + x - 1])
        val right = getGrayValue(pixels[y * width + x + 1])
        return (right - left).toFloat()
    }

    private fun getGradientY(pixels: IntArray, x: Int, y: Int, width: Int): Float {
        val top = getGrayValue(pixels[(y - 1) * width + x])
        val bottom = getGrayValue(pixels[(y + 1) * width + x])
        return (bottom - top).toFloat()
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
            val inputStream = assets.open("landmark_images/$filename")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: IOException) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("FeatureMatchTest", "App resumed - Feature-Matching bereit")
    }
}

/**
 * Erweiterte Landmark-Signatur mit verschiedenen Feature-Typen
 */
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

/**
 * Match-Ergebnis für Feature-Matching
 */
data class MatchResult(
    val landmarkId: String,
    val filename: String,
    val similarity: Float,
    val matchCount: Int,
    val distance: Float
)

@Composable
fun ARWalkingApp() {
    val navController = rememberNavController()

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(navController = navController)
            }
            composable("camera_navigation/{destination}/{startLocation}") { backStackEntry ->
                val encodedDestination = backStackEntry.arguments?.getString("destination") ?: "Unbekanntes Ziel"
                val encodedStartLocation = backStackEntry.arguments?.getString("startLocation") ?: "Unbekannter Start"
                val destination = URLDecoder.decode(encodedDestination, StandardCharsets.UTF_8.toString())
                val startLocation = URLDecoder.decode(encodedStartLocation, StandardCharsets.UTF_8.toString())
                CameraNavigation(
                    destination = destination,
                    startLocation = startLocation
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ARWalkingAppPreview() {
    ARWalkingTheme {
        ARWalkingApp()
    }
}