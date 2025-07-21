package com.example.arwalking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.screens.HomeScreen
import com.example.arwalking.ui.theme.ARWalkingTheme
import android.util.Log
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var routeViewModel: RouteViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewModel erstellen
        routeViewModel = ViewModelProvider(this)[RouteViewModel::class.java]

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
            // Objekt ist bereit für weitere Verwendung
            routeViewModel.logNavigationRoute(navigationRoute)
            // weitere verwendung von navigationRoute....


        } else {
            Log.e("MainActivity", "Fehler beim Laden der Route")
        }
    }
}

@Composable
fun ARWalkingApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(navController = navController)
        }
        // Hier können später weitere Screens hinzugefügt werden:
        // composable("ar_view") { ARScreen(navController = navController) }
        // composable("settings") { SettingsScreen(navController = navController) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ARWalkingAppPreview() {
    ARWalkingTheme {
        ARWalkingApp()
    }
}