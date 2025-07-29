package com.example.arwalking.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.R
import com.example.arwalking.data.FavoritesRepository
import components.NavigationDrawer
import components.NavigationStepData


val LocalNavController = staticCompositionLocalOf<NavController> {
    error("No NavController provided")
}

// Define navigation routes
sealed class Screen(val route: String) {
    object Camera : Screen("camera")
}

@Composable
fun CameraNavigation(
    destination: String = "Unbekanntes Ziel",
    startLocation: String = "Unbekannter Start"
) {
    val navController = rememberNavController()
    val mainNavController = LocalNavController.current

    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route
    ) {
        composable(Screen.Camera.route) {
            CameraScreen(
                mainNavController = mainNavController, 
                destination = destination,
                startLocation = startLocation
            )
        }
    }
}

@Composable
fun CameraScreen(
    mainNavController: NavController, 
    destination: String = "Unbekanntes Ziel",
    startLocation: String = "Unbekannter Start"
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showRationaleDialog by remember { mutableStateOf(false) }
    
    // Zoom state management
    var currentZoomRatio by remember { mutableStateOf(1.0f) }
    var availableZoomRatios by remember { mutableStateOf(listOf(0.7f, 1.0f, 2.0f)) }

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
                lifecycleOwner = lifecycleOwner,
                zoomRatio = currentZoomRatio,
                onAvailableZoomRatiosChanged = { ratios ->
                    availableZoomRatios = ratios
                }
            )

            // AR Walking UI Overlay
            ARWalkingUIOverlay(
                mainNavController = mainNavController, 
                destination = destination,
                startLocation = startLocation,
                availableZoomRatios = availableZoomRatios,
                currentZoomRatio = currentZoomRatio,
                onZoomChange = { newZoomRatio ->
                    currentZoomRatio = newZoomRatio
                }
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
fun ARWalkingUIOverlay(
    mainNavController: NavController, 
    destination: String = "Unbekanntes Ziel",
    startLocation: String = "Unbekannter Start",
    availableZoomRatios: List<Float> = listOf(0.7f, 1.0f, 2.0f),
    currentZoomRatio: Float = 1.0f,
    onZoomChange: (Float) -> Unit = {}
) {
    // Check if current route is a favorite (reactive)
    val favorites by FavoritesRepository.favorites.collectAsState()
    val isFavorite = favorites.any { 
        it.startLocation == startLocation && it.destination == destination 
    }
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Semi-transparent black gradient at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 800f
                    )
                )
        )

        // Top bar with back button and destination text
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offset(y = 70.dp)
                .padding(horizontal = 20.dp)
        ) {
            // Back button
            Icon(
                painter = painterResource(id = R.drawable.chevron_left_1),
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(35.dp)
                    .clickable {
                        mainNavController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                    .padding(4.dp) // Padding for better touch target
            )

            // Destination text
            Text(
                text = destination,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3, // Erlaube bis zu 3 Zeilen
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp, // Zeilenhöhe für bessere Lesbarkeit
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.7f)
                    .drawBehind {
                        drawIntoCanvas { canvas ->
                            val shadowPaint = Paint().apply {
                                color = Color.Black.copy(alpha = 0.5f)
                                isAntiAlias = true
                            }
                            // Simple shadow effect by drawing the text slightly offset
                        }
                    }
            )

            Icon(
                painter = painterResource(
                    id = if (isFavorite) R.drawable.star_filled else R.drawable.star_outline
                ),
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = Color.Unspecified, // Use the colors from the drawable
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(35.dp)
                    .clickable {
                        if (isFavorite) {
                            // Find and remove the favorite
                            val favorites = FavoritesRepository.favorites.value
                            val favoriteToRemove = favorites.find { 
                                it.startLocation == startLocation && it.destination == destination 
                            }
                            favoriteToRemove?.let {
                                FavoritesRepository.removeFavorite(it)
                            }
                        } else {
                            // Add to favorites
                            FavoritesRepository.addFavorite(startLocation, destination)
                        }
                    }
                    .padding(4.dp)
            )
        }

        // Drawer Panel
        // TODO: Hier JSON Route übergeben
        val navigationSteps = listOf(
            NavigationStepData("Durch die Tür", R.drawable.door),
            NavigationStepData("Gerade am Fahrstuhl vorbei", R.drawable.arrow_up_1),
            NavigationStepData("Biegen Sie rechts ab", R.drawable.corner_up_right_1),
            NavigationStepData("Gerade am Fahrstuhl vorbei", R.drawable.arrow_up_1),
            NavigationStepData("Biegen Sie rechts ab", R.drawable.corner_up_right_1),
            NavigationStepData("Gerade am Fahrstuhl vorbei", R.drawable.arrow_up_1),
            NavigationStepData("Biegen Sie rechts ab", R.drawable.corner_up_right_1),
            NavigationStepData("Durch die Tür", R.drawable.arrow_up_1)
        )

        // Navigation Drawer
        NavigationDrawer(
            navigationSteps = navigationSteps,
            destinationLabel = destination,
            onClose = { /* Close functionality moved to back button */ },
            availableZoomRatios = availableZoomRatios,
            currentZoomRatio = currentZoomRatio,
            onZoomChange = onZoomChange,
            modifier = Modifier
                .align(alignment = Alignment.BottomCenter)
        )
    }
}

@Composable
fun Property1Variant2(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .requiredWidth(width = 290.dp)
            .requiredHeight(height = 248.dp)
    ) {
        Property1Default()
    }
}

@Composable
fun Property1Default(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = 12.dp,
                end = 244.dp,
                top = 18.dp,
                bottom = 196.dp
            )
    )
}

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    zoomRatio: Float = 1.0f,
    onAvailableZoomRatiosChanged: (List<Float>) -> Unit = {}
) {
    val context = LocalContext.current
    var cameraError by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    
    // Apply zoom changes to camera when zoomRatio changes
    LaunchedEffect(zoomRatio) {
        camera?.let { cam ->
            try {
                cam.cameraControl.setZoomRatio(zoomRatio)
                Log.d("CameraZoom", "Zoom applied: ${zoomRatio}x")
            } catch (e: Exception) {
                Log.e("CameraZoom", "Failed to apply zoom: ${zoomRatio}x", e)
            }
        }
    }

    if (cameraError != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Kamera-Fehler: $cameraError",
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
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
                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                .build()
                                .also { p ->
                                    p.setSurfaceProvider(surfaceProvider)
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            // Alle vorherigen Bindungen aufheben
                            cameraProvider.unbindAll()

                            // Kamera an Lifecycle binden
                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )

                            // Store camera reference for zoom control
                            camera = boundCamera

                            // Setup zoom capabilities
                            setupZoomCapabilities(boundCamera, onAvailableZoomRatiosChanged)
                            
                            // Apply initial zoom
                            boundCamera.cameraControl.setZoomRatio(zoomRatio)

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

// Helper function to setup zoom capabilities
private fun setupZoomCapabilities(camera: Camera, onAvailableZoomRatiosChanged: (List<Float>) -> Unit) {
    val cameraInfo: CameraInfo = camera.cameraInfo
    val zoomState = cameraInfo.zoomState.value
    
    if (zoomState != null) {
        val minZoom = zoomState.minZoomRatio
        val maxZoom = zoomState.maxZoomRatio
        
        Log.d("CameraZoom", "Zoom range: $minZoom - $maxZoom")
        
        // Define zoom ratios based on device capabilities
        val availableZoomRatios = mutableListOf<Float>()
        
        // Add ultra-wide/panorama if available (typically < 1.0)
        if (minZoom < 1.0f) {
            val ultraWideZoom = maxOf(minZoom, 0.5f) // Use minimum or 0.5x, whichever is higher
            availableZoomRatios.add(ultraWideZoom)
        }
        
        // Always add 1x (normal)
        availableZoomRatios.add(1.0f)
        
        // Add 2x zoom if available
        if (maxZoom >= 2.0f) {
            availableZoomRatios.add(2.0f)
        }
        
        // If no ultra-wide but maxZoom allows, add a wider angle option
        if (minZoom >= 1.0f && availableZoomRatios.size < 3) {
            // Use 0.7x as a digital wide-angle approximation
            availableZoomRatios.add(0, 0.7f)
        }
        
        Log.d("CameraZoom", "Available zoom ratios: $availableZoomRatios")
        onAvailableZoomRatiosChanged(availableZoomRatios)
    } else {
        // Fallback if zoom state is not available
        Log.w("CameraZoom", "Zoom state not available, using default ratios")
        onAvailableZoomRatiosChanged(listOf(0.7f, 1.0f, 2.0f))
    }
}