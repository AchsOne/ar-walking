package com.example.arwalking

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.screens.CameraNavigation
import com.example.arwalking.screens.HomeScreen
import com.example.arwalking.screens.LocalNavController
import com.example.arwalking.ui.theme.ARWalkingTheme
import org.opencv.android.OpenCVLoader
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Das Herz der AR-Walking App.
 * 
 * Hier wird alles initialisiert: OpenCV für Computer Vision,
 * das RouteViewModel für Navigation und die gesamte UI.
 * 
 * Denk an MainActivity als den "Dirigenten" der App - koordiniert alle Komponenten.
 */
class MainActivity : ComponentActivity() {

    private lateinit var routeViewModel: RouteViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Schritt 1: Computer Vision vorbereiten - ohne OpenCV läuft nichts
        initOpenCV()
        // Schritt 2: Das Gehirn der Navigation aktivieren
        setupViewModel()
        // Schritt 3: Die schöne UI aufbauen
        setupUI()
        // Schritt 4: Route laden und Feature-Mapping starten
        loadRoute()
    }

    /**
     * OpenCV ist unser Computer-Vision-Motor.
     * Ohne OpenCV können wir keine Features in Bildern erkennen - also absolut kritisch!
     */
    private fun initOpenCV() {
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV konnte nicht geladen werden - AR-Navigation nicht möglich!")
            return
        }
        Log.d(TAG, "OpenCV erfolgreich geladen - Computer Vision bereit")
    }

    /**
     * Das RouteViewModel ist das Gehirn der Navigation.
     * Es weiß wo wir sind, wo wir hinwollen und was um uns herum ist.
     */
    private fun setupViewModel() {
        routeViewModel = ViewModelProvider(this)[RouteViewModel::class.java]
    }

    private fun setupUI() {
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
    }

    /**
     * Lädt die Navigationsroute und startet das Feature-Mapping.
     * 
     * Das ist der Moment wo die App "lernt" wie die Landmarks aussehen,
     * damit sie später in der Kamera erkannt werden können.
     */
    private fun loadRoute() {
        val route = routeViewModel.loadRoute(this) ?: run {
            Log.e(TAG, "Route konnte nicht geladen werden - Navigation nicht möglich")
            return
        }

        // Debug-Info für Entwickler
        routeViewModel.logRoute(route)
        // Jetzt die Landmarks "lernen" - Feature-Extraktion starten
        routeViewModel.initFeatureMapping(this)
    }


    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun ARWalkingApp() {
    val navController = rememberNavController()

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(navController = navController, startDestination = "home") {
            composable("home") {
                HomeScreen(navController = navController)
            }
            composable("camera_navigation/{destination}/{startLocation}") { backStackEntry ->
                val destination = backStackEntry.arguments?.getString("destination")
                    ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
                    ?: "Unknown destination"

                val startLocation = backStackEntry.arguments?.getString("startLocation")
                    ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
                    ?: "Unknown location"

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