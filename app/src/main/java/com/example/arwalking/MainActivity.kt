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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            initializeAppAsync()

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "App initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
            // App trotzdem weiter laufen lassen
        }
    }

    private fun initializeAppAsync() {
        // Führe nur minimale Initialisierung durch, um Crashes zu vermeiden
        Thread {
            try {
                Log.i("MainActivity", "Starting minimal async initialization...")
                
                // ViewModel sicher erstellen
                runOnUiThread {
                    try {
                        routeViewModel = ViewModelProvider(this@MainActivity)[RouteViewModel::class.java]
                        Log.i("MainActivity", "RouteViewModel created successfully")
                        
                        // Initialisierung direkt nach ViewModel-Erstellung
                        try {
                            routeViewModel.initializeStorage(this@MainActivity)
                            Log.i("MainActivity", "Storage initialized")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Storage initialization failed: ${e.message}", e)
                        }
                        
                        try {
                            val navigationRoute = routeViewModel.loadNavigationRoute(this@MainActivity)
                            if (navigationRoute != null) {
                                Log.i("MainActivity", "Navigation route loaded successfully")
                            } else {
                                Log.w("MainActivity", "Navigation route could not be loaded")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Route loading failed: ${e.message}", e)
                        }
                        
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error creating RouteViewModel: ${e.message}", e)
                    }
                }
                
                Log.i("MainActivity", "Minimal initialization completed")
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in async initialization: ${e.message}", e)
                // App sollte trotzdem funktionieren, auch wenn Initialisierung fehlschlägt
            }
        }.start()
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