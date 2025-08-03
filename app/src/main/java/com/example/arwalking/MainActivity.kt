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
// OpenCV imports entfernt für Stub-Implementation

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

        // OpenCV initialisieren (Stub für lokale Entwicklung)
        try {
            // Simuliere OpenCV Initialisierung
            Log.i("MainActivity", "OpenCV Stub loaded successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "OpenCV Stub initialization failed: ${e.message}")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
            return
        }




        // ViewModel erstellen
        routeViewModel = ViewModelProvider(this)[RouteViewModel::class.java]

        // Sofort Feature Mapping initialisieren
        Log.i("MainActivity", "Initialisiere Feature Mapping beim App-Start...")
        routeViewModel.initializeStorage(this)

        enableEdgeToEdge()
        // Entferne checkCameraAndLaunch() - wird über Navigation gehandhabt
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
            // Objekt ist bereit für weitere Verwendung
            routeViewModel.logNavigationRoute(navigationRoute)
            
            // Feature Mapping ist bereits initialisiert, aktiviere es sofort
            routeViewModel.enableStorageSystemImmediately(this)
            
            // System-Validierung durchführen (nur im Debug-Modus)
            if (BuildConfig.DEBUG) {
                val systemValidator = SystemValidator(this)
                systemValidator.validateSystem(routeViewModel)
                
                // Simuliere Feature-Matching für Testzwecke
                systemValidator.simulateFeatureMatching(routeViewModel, "prof_ludwig_office")
            }
            
        } else {
            Log.e("MainActivity", "Fehler beim Laden der Route")
        }
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



            // Hier können später weitere Screens hinzugefügt werden:
            // composable("ar_view") { ARScreen(navController = navController) }
            // composable("settings") { SettingsScreen(navController = navController) }
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