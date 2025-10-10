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
import androidx.lifecycle.lifecycleScope
import com.example.arwalking.BuildConfig
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    private lateinit var routeViewModel: RouteViewModel

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


        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV loaded successfully")
        }


        if (OpenCVLoader.initLocal()) {
            Log.d("OpenCV", "OpenCV loaded successfully")

            // SCHRITT 1: Basis-Test
            val opencvTest = OpenCvTest(this)
            opencvTest.runAllTests()

            // SCHRITT 2: Feature-Extraktion Test (NEU)
            val featureTest = OpenCvFeatureTest(this)
            val featuresPassed = featureTest.runAllTests()

            if (featuresPassed) {
                Toast.makeText(this, "✅ Feature-Extraktion funktioniert!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "❌ Feature-Extraktion fehlgeschlagen", Toast.LENGTH_LONG).show()
            }

            //  SCHRITT 3: Feature-Matching Test
            val matchingTest = OpenCvMatchingTest(this)
            val matchingPassed = matchingTest.runAllTests()

            if (matchingPassed) {
                Toast.makeText(this, "✅ Feature-Matching erfolgreich!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "❌ Feature-Matching fehlgeschlagen!", Toast.LENGTH_LONG).show()
            }
            
            // SCHRITT 4: Erweiterte Tests (temporär deaktiviert)
            // TODO: Erweiterte Tests aktivieren nach Bugfix
            Log.i("MainActivity", "🚀 Erweiterte Features verfügbar (Tests deaktiviert)")
        }

        // ViewModel erstellen
        routeViewModel = ViewModelProvider(this)[RouteViewModel::class.java]

        // Storage initialisieren
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

        // Route laden und Feature-Matching testen
        val navigationRoute = routeViewModel.loadNavigationRoute(this)
        if (navigationRoute != null) {
            routeViewModel.logNavigationRoute(navigationRoute)
            routeViewModel.enableStorageSystemImmediately(this)

            // Feature-Matching initialisieren
            Log.i("FeatureTest", "Initialisiere Feature-Matching...")
            routeViewModel.initializeFeatureMapping(this)

        } else {
            Log.e("FeatureTest", "Fehler beim Laden der Route")
        }
    }

    /**
     * Beobachtet Feature-Matching Ergebnisse
     */
    private fun observeFeatureMatching() {
        lifecycleScope.launch {
            // Beobachte ob Feature-Mapping aktiviert wurde
            routeViewModel.isFeatureMappingEnabled.collect { isEnabled ->
                Log.i("FeatureTest", "Feature-Mapping Status: ${if (isEnabled) "AKTIVIERT" else "DEAKTIVIERT"}")
            }
        }

        lifecycleScope.launch {
            // Beobachte Match-Ergebnisse
            routeViewModel.currentMatches.collect { matches ->
                if (matches.isNotEmpty()) {
                    Log.i("FeatureTest", "=== MATCH ERGEBNISSE ===")
                    matches.forEach { match ->
                        Log.i("FeatureTest", "  ${match.landmark.name}: ${(match.confidence * 100).toInt()}% (${String.format("%.1f", match.distance)}m)")
                    }
                }
            }
        }
    }

    /**
     * Führt automatisierten Test durch
     */
    private fun runAutomatedTest() {
        lifecycleScope.launch {
            try {
                Log.i("FeatureTest", "=== STARTE AUTOMATISIERTEN TEST ===")

                // Test 1: Lade ein Landmark-Bild als "Kamera-Frame"
                val testLandmarkId = "PT-1-926"
                Log.i("FeatureTest", "Test 1: Simuliere Frame mit $testLandmarkId")

                val testBitmap = loadTestBitmap(testLandmarkId)
                if (testBitmap == null) {
                    Log.e("FeatureTest", "Konnte Test-Bitmap nicht laden")
                    return@launch
                }

                Log.i("FeatureTest", "Test-Bitmap geladen: ${testBitmap.width}x${testBitmap.height}")

                // Setze Navigationsschritt der diesen Landmark erwartet
                // (Angenommen der Landmark ist in Schritt 1)
                routeViewModel.setCurrentNavigationStep(1)

                delay(500)

                // Teste Feature-Matching
                Log.i("FeatureTest", "Führe Feature-Matching durch...")
                routeViewModel.processFrameForFeatureMatching(testBitmap)

                delay(2000)

                // Test 2: Teste mit anderem Landmark (sollte Verwechslungswarnung geben)
                Log.i("FeatureTest", "Test 2: Teste mit falschem Landmark")
                val wrongBitmap = loadTestBitmap("PT-1-686")

                if (wrongBitmap != null) {
                    delay(1000)
                    routeViewModel.processFrameForFeatureMatching(wrongBitmap)
                }

                delay(2000)

                Log.i("FeatureTest", "=== TEST ABGESCHLOSSEN ===")
                Toast.makeText(this@MainActivity, "Feature-Matching Test abgeschlossen", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Log.e("FeatureTest", "Fehler beim automatisierten Test: ${e.message}")
            }
        }
    }

    /**
     * Lädt ein Landmark-Bild zum Testen
     */
    private fun loadTestBitmap(landmarkId: String): Bitmap? {
        return try {
            val filename = "$landmarkId.jpg"
            val inputStream = assets.open("landmark_images/$filename")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap
        } catch (e: Exception) {
            Log.e("FeatureTest", "Fehler beim Laden von $landmarkId: ${e.message}")
            null
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("FeatureTest", "App resumed")
    }
}

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