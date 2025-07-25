package com.example.arwalking.screens


import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import android.graphics.RenderEffect as AndroidRenderEffect

// CompositionLocal for the main NavController
val LocalNavController = staticCompositionLocalOf<NavController> { 
    error("No NavController provided") 
}

// Datenklasse für Navigation Steps
data class NavigationStepData(val text: String, val icon: Int)

// Define navigation routes
sealed class Screen(val route: String) {
    object Camera : Screen("camera")
}

@Composable
fun CameraNavigation(destination: String = "Unbekanntes Ziel") {
    val navController = rememberNavController()
    val mainNavController = LocalNavController.current
    
    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route
    ) {
        composable(Screen.Camera.route) {
            CameraScreen(mainNavController = mainNavController, destination = destination)
        }
    }
}

@Composable
fun CameraScreen(mainNavController: NavController, destination: String = "Unbekanntes Ziel") {
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

            // AR Walking UI Overlay
            ARWalkingUIOverlay(mainNavController = mainNavController, destination = destination)

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
fun ARWalkingUIOverlay(mainNavController: NavController, destination: String = "Unbekanntes Ziel") {
    Box(
        modifier = Modifier
            .requiredWidth(width = 412.dp)
            .requiredHeight(height = 917.dp)
    ) {
        // Property1Variant2 (Top left corner element)
        Property1Variant2(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 8.dp, y = 50.dp)
        )

        // AR Logo Section
        Box(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 106.dp, y = 56.dp)
                .requiredWidth(width = 201.dp)
                .requiredHeight(height = 75.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "image 1",
                modifier = Modifier
                    .requiredWidth(width = 201.dp)
                    .requiredHeight(height = 75.dp)
            )
            Text(
                text = "AR",
                color = Color(0xff94ad0b),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 60.dp, y = 6.dp)
            )
        }

        // Neues Navigation Drawer Panel
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
        // Navigation Drawer am unteren Bildschirmrand
        NavigationDrawer(
            navigationSteps = navigationSteps,
            destinationLabel = destination,
            onClose = {
                // Navigate back to home screen
                mainNavController.navigate("home") {
                    popUpTo("home") { inclusive = true }
                }
            },
            modifier = Modifier
                .align(alignment = Alignment.BottomCenter)
        )
    }
}

@Composable
fun NavigationDrawer(
    navigationSteps: List<NavigationStepData>,
    destinationLabel: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMaximized by remember { mutableStateOf(false) }
    var offsetY by remember { mutableStateOf(0f) }
    val dragSensitivity = 0.5f // Lower value = more sensitive

    // Apple Glass-Effekt mit Material behind blur
    val containerHeight by animateDpAsState(
        targetValue = if (isMaximized) 650.dp else 150.dp, // Increased height to ensure last field is visible
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        )
    )

    // Gesamter Container - jetzt am unteren Bildschirmrand und ziehbar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .requiredHeight(containerHeight)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Snap to either expanded or collapsed state based on drag distance
                        if (offsetY < -50) {
                            isMaximized = true
                        } else if (offsetY > 50) {
                            isMaximized = false
                        }
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount.y * dragSensitivity
                        
                        // Toggle maximized state based on drag direction
                        if (offsetY < -100) {
                            isMaximized = true
                            offsetY = 0f
                        } else if (offsetY > 100) {
                            isMaximized = false
                            offsetY = 0f
                        }
                    }
                )
            }
    ) {
        // Hintergrund mit Glaseffekt
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Apple Glass-Effekt
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.10f) // Repeat last color to ensure it extends to bottom
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY // Make sure gradient extends all the way
                    )
                )
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = AndroidRenderEffect.createBlurEffect(
                            30f, 30f, Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                }
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .shadow(
                    elevation = 20.dp, 
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.05f),
                    spotColor = Color.Black.copy(alpha = 0.1f)
                )
        )
        
        // Content Container - alle UI-Elemente

        // Top handle bar (Apple-Style) with drag indicator
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .requiredWidth(40.dp)
                    .requiredHeight(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )

        }

        // Close (X) Button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = 20.dp)
                .requiredSize(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            // Apple-Style X Icon
            Icon(
                painter = painterResource(id = R.drawable.x),
                contentDescription = "Close",
                tint = Color(0xFF000000),
                modifier = Modifier.requiredSize(16.dp)
            )
        }

        // Maximize/Minimize Button - Apple Style (oben rechts)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-16).dp, y = 20.dp)
                .requiredSize(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
                .clickable { isMaximized = !isMaximized },
            contentAlignment = Alignment.Center
        ) {
            // Expand/Collapse Icon
            Icon(
                painter = painterResource(
                    id = if (isMaximized) R.drawable.maximize_2_1 else R.drawable.minimize_2
                ),
                contentDescription = if (isMaximized) "Minimize" else "Maximize",
                tint = Color(0xFF000000),
                modifier = Modifier
                    .requiredSize(16.dp)
                    .rotate(if (isMaximized) 0f else 180f)
            )
        }

        // Content Area - unterschiedlich je nach isMaximized
        if (isMaximized) {
            // Maximized View - alle Steps
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 60.dp) // Add offset to make room for handle and buttons
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 80.dp) // Increased bottom padding to ensure destination is fully visible with space
            ) {
                // Hauptstep (grüne Box) - non-transparent für Sichtbarkeit
                if (navigationSteps.isNotEmpty()) {
                    val firstStep = navigationSteps.first()
                    AppleGlassStepCard(
                        step = firstStep,
                        isActive = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Weitere Steps als transparente Boxen
                navigationSteps.drop(1).forEach { step ->
                    AppleGlassStepCard(
                        step = step,
                        isActive = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Ziel-Label
                AppleGlassDestinationCard(
                    destinationLabel = destinationLabel,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Minimized View - nur aktueller Step
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 60.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (navigationSteps.isNotEmpty()) {
                    val currentStep = navigationSteps.first()
                    AppleGlassStepCard(
                        step = currentStep,
                        isActive = true,
                        isCompact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun AppleGlassStepCard(
    step: NavigationStepData,
    isActive: Boolean,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .requiredHeight(if (isCompact) 60.dp else 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) {
                    // Aktiver Step - nicht transparent für bessere Sichtbarkeit
                    Color(0xff94ac0b)
                } else {
                    // Inaktive Steps - Apple Glass-Effekt
                    Color.White.copy(alpha = 0.15f)
                }
            )
            .border(
                width = 0.5.dp,
                color = if (isActive) Color.Transparent else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .shadow(
                elevation = if (isActive) 4.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon with white tint
            Image(
                painter = painterResource(id = step.icon),
                contentDescription = step.text,
                modifier = Modifier.requiredSize(if (isCompact) 24.dp else 32.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
            
            // Text
            Text(
                text = step.text,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
                style = TextStyle(
                    fontSize = if (isCompact) 16.sp else 18.sp,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AppleGlassDestinationCard(
    destinationLabel: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .requiredHeight(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFFFFF)) // Green color for destination (same as active steps)
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Map Pin Icon with white tint
            Image(
                painter = painterResource(id = R.drawable.mappin1),
                contentDescription = "Destination",
                modifier = Modifier.requiredSize(32.dp),
               // colorFilter = ColorFilter.tint(Color.White)
            )
            
            // Destination Text
            Text(
                text = destinationLabel,
                color = Color.Black,
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
        }
    }
}


@Composable
fun NavigationStep(
    text: String,
    icon: Int,
    iconRotation: Float = 0f
) {
    Box(
        modifier = Modifier
            .requiredWidth(width = 312.dp)
            .requiredHeight(height = 47.dp)
    ) {
        Box(
            modifier = Modifier
                .requiredWidth(width = 312.dp)
                .requiredHeight(height = 47.dp)
                .clip(shape = RoundedCornerShape(8.dp))
                .background(color = Color.White)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
        )

        if (icon == R.drawable.door) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 8.dp, y = 9.dp)
                    .requiredSize(size = 29.dp)
            )
            Text(
                text = text,
                color = Color.Black,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                ),
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 45.dp, y = 13.dp)
            )
        } else {
            Image(
                painter = painterResource(id = icon),
                contentDescription = text,
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 11.24.dp, y = 12.dp)
                    .requiredWidth(width = 22.dp)
                    .requiredHeight(height = 24.dp)
                    .rotate(degrees = iconRotation)
            )
            Text(
                text = text,
                color = Color.Black,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light
                ),
                modifier = Modifier
                    .align(alignment = Alignment.TopStart)
                    .offset(x = 40.dp, y = 11.dp)
            )
        }
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
                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
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