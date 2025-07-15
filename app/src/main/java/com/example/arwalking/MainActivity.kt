package com.example.arwalking

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.arwalking.ui.theme.ArWalkingTheme

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArWalkingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        loadRouteParts(this)
    }
    private fun loadRouteParts(context: Context) {
        val jsonString = loadJSONFromAsset(context, "route.json")
        val gson = Gson()
        val routeWrapper = gson.fromJson(jsonString, RouteWrapper::class.java)

        val routeParts = routeWrapper.route.path.firstOrNull()?.routeParts ?: emptyList()

        for ((index, part) in routeParts.withIndex()) {
            Log.d("RoutePart", "Schritt ${index + 1}: ${part.instruction}")
            for (landmark in part.landmarks) {
                Log.d("Landmark", " → ID: ${landmark.id}, Typ: ${landmark.type}, x: ${landmark.x}, y: ${landmark.y}")
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ArWalkingTheme {
        Greeting("Android")
    }
}