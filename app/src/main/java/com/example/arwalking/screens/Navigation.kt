package com.example.arwalking.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.example.arwalking.ui.theme.GradientUtils
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arwalking.R
import com.example.arwalking.RouteViewModel
import com.example.arwalking.components.Animated3DArrowOverlay
import com.example.arwalking.components.ExpandedARInfoIsland
import com.example.arwalking.components.FeatureMappingStatusIndicator
import com.example.arwalking.components.FeatureMatchOverlay
import com.example.arwalking.components.rememberARScanStatus
import com.example.arwalking.data.FavoritesRepository
import components.NavigationDrawer
import components.NavigationStepData
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer


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
    val routeViewModel: RouteViewModel = viewModel()

    LaunchedEffect(Unit) {
        routeViewModel.loadNavigationRoute(context)
        routeViewModel.enableStorageSystemImmediately(context)
        routeViewModel.initializeFeatureMapping(context)
        routeViewModel.startFrameProcessing()
    }

    // Nutzt Route-Daten oder Fallbacks
    val currentRoute by routeViewModel.currentRoute.collectAsState()
    val actualStartLocation = currentRoute?.let { 
        routeViewModel.getCurrentStartPoint() 
    } ?: startLocation
    val actualDestination = currentRoute?.let { 
        routeViewModel.getCurrentEndPoint() 
    } ?: destination

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
                },
                onFrameProcessed = { bitmap ->
                    // Frame für Feature Mapping verarbeiten
                    try {
                        routeViewModel.processFrameForFeatureMatching(bitmap)  // ✅ Direkt Bitmap übergeben!
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error processing frame for feature matching", e)
                    }
                }
            )

            // AR Walking UI Overlay
            ARWalkingUIOverlay(
                mainNavController = mainNavController,
                destination = actualDestination,
                startLocation = actualStartLocation,
                availableZoomRatios = availableZoomRatios,
                currentZoomRatio = currentZoomRatio,
                onZoomChange = { newZoomRatio ->
                    currentZoomRatio = newZoomRatio
                },
                routeViewModel = routeViewModel
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
    onZoomChange: (Float) -> Unit = {},
    routeViewModel: RouteViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Get current route from ViewModel
    val currentRoute by routeViewModel.currentRoute.collectAsState()
    
    // Compute actual destination from route or use fallback
    val actualDestination = currentRoute?.let { 
        routeViewModel.getCurrentEndPoint() 
    } ?: destination
    
    // Check if current route is a favorite (reactive)
    val favorites by FavoritesRepository.favorites.collectAsState()
    val isFavorite = favorites.any {
        it.startLocation == startLocation && it.destination == destination
    }

    // Feature Mapping State
    val featureMatches by routeViewModel.currentMatches.collectAsState()
    val isFeatureMappingEnabled by routeViewModel.isFeatureMappingEnabled.collectAsState()
    val availableLandmarks = routeViewModel.getAvailableLandmarks()




    // Sofortige Aktivierung des Feature Mappings beim Laden der UI
    LaunchedEffect(Unit) {
        // Stelle sicher, dass Feature Mapping sofort aktiv ist
        routeViewModel.enableStorageSystemImmediately(context)
        routeViewModel.startFrameProcessing()
    }

    // Frame Processing wird jetzt direkt in der CameraPreviewView gehandhabt

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Semi-transparent black gradient at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(300.dp)
                .background(
                    brush = GradientUtils.safeVerticalGradient(
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
                    .padding(4.dp)
            )

            // Destination text
            Text(
                text = destination,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.7f)
                    .drawBehind {
                        drawIntoCanvas { canvas ->
                            val shadowPaint = Paint().apply {
                                color = Color.Black.copy(alpha = 0.5f)
                                isAntiAlias = true
                            }
                        }
                    }
            )

            // Right side buttons
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {


                // Favorite button
                Icon(
                    painter = painterResource(
                        id = if (isFavorite) R.drawable.star_filled else R.drawable.star_outline
                    ),
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(35.dp)
                        .clickable {
                            if (isFavorite) {
                                val favorites = FavoritesRepository.favorites.value
                                val favoriteToRemove = favorites.find {
                                    it.startLocation == startLocation && it.destination == destination
                                }
                                favoriteToRemove?.let {
                                    FavoritesRepository.removeFavorite(it)
                                }
                            } else {
                                FavoritesRepository.addFavorite(startLocation, destination)
                            }
                        }
                        .padding(4.dp)
                )
            }
        }

        // Load navigation steps from JSON route
        val navigationSteps = if (currentRoute != null) {
            routeViewModel.getCurrentNavigationSteps().map { step ->
                val iconRes = when {
                    step.instruction.contains("Tür", ignoreCase = true) -> R.drawable.navigation21
                    step.instruction.contains("links", ignoreCase = true) -> R.drawable.left
                    step.instruction.contains("rechts", ignoreCase = true) -> R.drawable.corner_up_right_1
                    step.instruction.contains("gerade", ignoreCase = true) -> R.drawable.arrow_up_1
                    step.instruction.contains("verlassen", ignoreCase = true) -> R.drawable.navigation21
                    step.instruction.contains("biegen", ignoreCase = true) -> R.drawable.corner_up_right_1
                    else -> R.drawable.arrow_up_1
                }
                NavigationStepData(
                    text = step.instruction.replace("<b>", "").replace("</b>", "").replace("<\\/b>", ""),
                    icon = iconRes
                )
            }
        } else {
            listOf(
                NavigationStepData(
                    text = "Route wird aus JSON geladen...",
                    icon = R.drawable.arrow_up_1
                )
            )
        }

        // 3D Arrow Overlay (main AR feature)
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
        val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
        
        // Berechne aktuellen Schritt und Gesamtschritte aus der Route
        val currentStepNumber by routeViewModel.currentNavigationStep.collectAsState()
        val totalStepsCount = navigationSteps.size
        /*
        Animated3DArrowOverlay(
            matches = featureMatches,
            isFeatureMappingEnabled = isFeatureMappingEnabled,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            currentStep = currentStepNumber,
            totalSteps = totalStepsCount,
            modifier = Modifier.fillMaxSize()
        )

        // Feature Mapping Overlays
        FeatureMatchOverlay(
            matches = featureMatches,
            isFeatureMappingEnabled = isFeatureMappingEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 80.dp)
        )
        */
        // Feature Mapping Status Indicator
        FeatureMappingStatusIndicator(
            isEnabled = isFeatureMappingEnabled,
            isProcessing = featureMatches.isNotEmpty(), // Verwende aktuelle Matches als Processing-Indikator
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp, end = 16.dp)
        )

        // 🎯 AR Info Island - GARANTIERT SICHTBAR für Debug
        ARInfoIslandOverlay(
            routeViewModel = routeViewModel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .offset(y = 140.dp)
        )







        // 🎯 INTELLIGENTE Navigation Drawer mit Status-Visualisierung
        val currentStepIndex by routeViewModel.currentNavigationStep.collectAsState()
        val completedSteps by routeViewModel.completedSteps.collectAsState()
        val deletedSteps by routeViewModel.deletedSteps.collectAsState()
        
        NavigationDrawer(
            navigationSteps = navigationSteps,
            destinationLabel = actualDestination,
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
    onAvailableZoomRatiosChanged: (List<Float>) -> Unit = {},
    onFrameProcessed: ((Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
    var cameraError by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

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

                            val imageAnalysis = if (onFrameProcessed != null) {
                                ImageAnalysis.Builder()
                                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                            try {
                                                val bitmap = imageProxyToBitmap(imageProxy)
                                                if (bitmap != null) {
                                                    onFrameProcessed(bitmap)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("CameraPreview", "Frame processing error: ${e.message}")
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                    }
                            } else null

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.unbindAll()

                            val useCases = if (imageAnalysis != null) {
                                arrayOf(preview, imageAnalysis)
                            } else {
                                arrayOf(preview)
                            }

                            val boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                *useCases
                            )

                            camera = boundCamera
                            setupZoomCapabilities(boundCamera, onAvailableZoomRatiosChanged)
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

// Hilfsfunktionen für Bildkonvertierung
private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
    val yPlane = imageProxy.planes[0]
    val uPlane = imageProxy.planes[1]
    val vPlane = imageProxy.planes[2]
    
    val ySize = yPlane.buffer.remaining()
    val uSize = uPlane.buffer.remaining()
    val vSize = vPlane.buffer.remaining()
    
    val nv21 = ByteArray(ySize + uSize + vSize)
    
    // Y Plane
    yPlane.buffer.get(nv21, 0, ySize)
    
    val uvPixelStride = uPlane.pixelStride
    if (uvPixelStride == 1) {
        // UV Planes are packed together
        uPlane.buffer.get(nv21, ySize, uSize)
        vPlane.buffer.get(nv21, ySize + uSize, vSize)
    } else {
        // UV Planes need interleaving for NV21
        val uvBuffer = ByteArray(uSize + vSize)
        uPlane.buffer.get(uvBuffer, 0, uSize)
        vPlane.buffer.get(uvBuffer, uSize, vSize)
        
        // Interleave V and U for NV21 format (VUVU...)
        var uvIndex = 0
        for (i in 0 until uSize) {
            nv21[ySize + uvIndex] = uvBuffer[uSize + i] // V
            nv21[ySize + uvIndex + 1] = uvBuffer[i] // U
            uvIndex += 2
        }
    }
    
    return nv21
}

private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    
    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }
    
    return try {
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        // Ursprüngliches Bitmap freigeben wenn es ein neues erstellt wurde
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        rotatedBitmap
    } catch (e: OutOfMemoryError) {
        Log.e("CameraPreview", "Out of memory rotating bitmap", e)
        bitmap // Fallback: Original zurückgeben
    }
}

// Improved bitmap conversion function
private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> {
                val nv21 = yuv420ToNv21(imageProxy)
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
                val bytes = out.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                rotateBitmapIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
            }
            ImageFormat.JPEG -> {
                val buffer: ByteBuffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                rotateBitmapIfNeeded(bitmap, imageProxy.imageInfo.rotationDegrees)
            }
            else -> {
                Log.w("CameraPreview", "Unsupported image format: ${imageProxy.format}")
                null
            }
        }
    } catch (e: Exception) {
        Log.e("CameraPreview", "Error converting ImageProxy to Bitmap: ${e.message}", e)
        null
    }
}

// Helper function to setup zoom capabilities
private fun setupZoomCapabilities(camera: Camera, onAvailableZoomRatiosChanged: (List<Float>) -> Unit) {
    val cameraInfo: CameraInfo = camera.cameraInfo
    val zoomState = cameraInfo.zoomState.value

    if (zoomState != null) {
        val minZoom = zoomState.minZoomRatio
        val maxZoom = zoomState.maxZoomRatio

        Log.d("CameraZoom", "Zoom range: $minZoom - $maxZoom")

        val availableZoomRatios = mutableListOf<Float>()

        if (minZoom < 1.0f) {
            val ultraWideZoom = maxOf(minZoom, 0.5f)
            availableZoomRatios.add(ultraWideZoom)
        }

        availableZoomRatios.add(1.0f)

        if (maxZoom >= 2.0f) {
            availableZoomRatios.add(2.0f)
        }

        if (minZoom >= 1.0f && availableZoomRatios.size < 3) {
            availableZoomRatios.add(0, 0.7f)
        }

        Log.d("CameraZoom", "Available zoom ratios: $availableZoomRatios")
        onAvailableZoomRatiosChanged(availableZoomRatios)
    } else {
        Log.w("CameraZoom", "Zoom state not available, using default ratios")
        onAvailableZoomRatiosChanged(listOf(0.7f, 1.0f, 2.0f))
    }
}



/**
 * AR Info Island Overlay für den NavigationsScreen
 */
@Composable
private fun ARInfoIslandOverlay(
    routeViewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val matches by routeViewModel.currentMatches.collectAsState()
    val isFeatureMappingEnabled by routeViewModel.isFeatureMappingEnabled.collectAsState()
    
    val landmarkCount = matches.size
    val bestConfidence = matches.maxOfOrNull { match -> match.confidence } ?: 0f
    val isTracking = matches.isNotEmpty()
    
    // 🔍 DEBUG: Logge UI-Werte für bessere Diagnose
    LaunchedEffect(matches, isFeatureMappingEnabled) {
        Log.d("ARInfoIsland", "📱 UI Update: enabled=$isFeatureMappingEnabled, matches=${matches.size}, bestConf=${(bestConfidence * 100).toInt()}%")
        matches.forEachIndexed { index, match ->
            Log.d("ARInfoIsland", "  🎯 Match $index: ${match.landmark.name ?: "unnamed"} - ${(match.confidence * 100).toInt()}%")
        }
    }
    
    // Automatischer AR-Status basierend auf aktuellen Bedingungen
    val arStatus = rememberARScanStatus(
        isInitialized = isFeatureMappingEnabled,
        landmarkCount = landmarkCount,
        bestConfidence = bestConfidence,
        isTracking = isTracking
    )
    
    // Verwende die erweiterte ARInfoIsland mit mehr Informationen
    ExpandedARInfoIsland(
        scanStatus = arStatus,
        landmarkCount = landmarkCount,
        confidence = bestConfidence,
        modifier = modifier,
        isVisible = isFeatureMappingEnabled
    )
}
