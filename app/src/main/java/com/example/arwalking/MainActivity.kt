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

class MainActivity : ComponentActivity() {

    private lateinit var routeViewModel: RouteViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize favorites persistence early
        com.example.arwalking.data.FavoritesRepository.initialize(applicationContext)
        initOpenCV()
        setupViewModel()
        setupUI()
        loadRoute()
    }


    private fun initOpenCV() {
        if (!OpenCVLoader.initLocal()) {
            Log.e(TAG, "OpenCV konnte nicht geladen werden - AR-Navigation nicht möglich!")
            return
        }
        Log.d(TAG, "OpenCV erfolgreich geladen - Computer Vision bereit")
    }


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
     * Loads the navigation route and starts feature mapping.
     */
    private fun loadRoute() {
        val route = routeViewModel.loadRoute(this) ?: run {
            Log.e(TAG, "Route konnte nicht geladen werden - Navigation nicht möglich")
            return
        }

        // Debug-Info
        routeViewModel.logRoute(route)
        // "learn" landmarks- start feature extraction
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