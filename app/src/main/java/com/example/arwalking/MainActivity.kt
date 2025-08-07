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
import androidx.activity.viewModels
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
import androidx.lifecycle.lifecycleScope
import com.example.arwalking.BuildConfig
import kotlinx.coroutines.launch

// OpenCV imports entfernt für Stub-Implementation

class MainActivity : ComponentActivity() {

    private val routeViewModel: RouteViewModel by viewModels()


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

        try {
            Log.i("MainActivity", "Starting MainActivity...")

            enableEdgeToEdge()
            
            // UI zuerst setzen - ohne ViewModel Initialisierung
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

            // ViewModel und andere Initialisierung später und sicher
            lifecycleScope.launch {
                try {
                    routeViewModel.initializeStorage(this@MainActivity)
                    routeViewModel.loadNavigationRoute(this@MainActivity)
                    Log.i("MainActivity", "Initialization completed")
                } catch (e: Exception) {
                    Log.e("MainActivity", "Initialization failed: ${'$'}{e.message}", e)
                    Toast.makeText(
                        this@MainActivity,
                        "App initialization failed: ${'$'}{e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "App initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
            // App trotzdem weiter laufen lassen
        }
    }

}

@Composable
fun ARWalkingApp() {
    val navController = rememberNavController()

    CompositionLocalProvider(LocalNavController provides navController) {
        NavHost(
            navController = navController,
            startDestination = "home" // Zurück zu normal
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
