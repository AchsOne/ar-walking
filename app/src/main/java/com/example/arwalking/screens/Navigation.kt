package com.example.app.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.R

// Define navigation routes
sealed class Screen(val route: String) {
    object Camera : Screen("camera")
}

@Composable
fun CameraNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route
    ) {
        composable(Screen.Camera.route) {
            CameraScreen()
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
            ) {
                showRationaleDialog = true
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (hasPermission) {
            // Live-Kameravorschau
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                lifecycleOwner = lifecycleOwner
            )

            // Gradient Overlay at Bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .requiredWidth(412.dp)
                    .requiredHeight(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            // Top gradient overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .requiredHeight(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Top Logo Section
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "AR Walking Logo",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 120.dp)
                    .requiredWidth(280.dp)
                    .requiredHeight(80.dp)
            )
        } else {
            // Klick-Box zum Anfordern der Berechtigung
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { launcher.launch(Manifest.permission.CAMERA) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Kamera erlauben",
                    color = Color.White
                )
            }
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Kamera-Berechtigung") },
            text = { Text("Wir benötigen Zugriff auf die Kamera, um eine Vorschau anzuzeigen.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    launcher.launch(Manifest.permission.CAMERA)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    var cameraError by remember { mutableStateOf<String?>(null) }

    if (cameraError != null) {
        Box(
            modifier = modifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Kamera-Fehler: $cameraError",
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                try {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()

                            // Prüfe ob eine Kamera verfügbar ist
                            if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                Log.e("CameraPreview", "Keine Rückkamera verfügbar")
                                cameraError = "Keine Rückkamera verfügbar"
                                return@addListener
                            }

                            val preview = Preview.Builder()
                                .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                                .build()
                                .also { p ->
                                    p.setSurfaceProvider(surfaceProvider)
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            // Alle vorherigen Bindungen aufheben
                            cameraProvider.unbindAll()

                            // Kamera an Lifecycle binden
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )

                            Log.d("CameraPreview", "Kamera erfolgreich initialisiert")

                        } catch (exc: Exception) {
                            Log.e("CameraPreview", "Kamera-Bindung fehlgeschlagen", exc)
                            cameraError = "Kamera konnte nicht gestartet werden"
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Fehler beim Kamera-Setup", exc)
                    cameraError = "Kamera-Initialisierung fehlgeschlagen"
                }
            }
        }
    )
}