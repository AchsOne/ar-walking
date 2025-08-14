package com.example.arwalking.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.arwalking.ui.ARNavigationViewModel
import com.example.arwalking.data.ARNavigationConfig
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Enhanced AR Camera Screen with integrated landmark recognition and navigation
 */
@Composable
fun ARCameraScreen(
    mainNavController: NavController,
    destination: String = "Unbekanntes Ziel",
    startLocation: String = "Unbekannter Start",
    viewModel: ARNavigationViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    
    // Permission handling
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
    
    // Initialize AR Navigation System
    LaunchedEffect(Unit) {
        if (hasPermission) {
            viewModel.initialize(context, ARNavigationConfig())
            // Load the default route
            viewModel.loadRoute("final-route.json")
        }
    }
    
    // Handle permission request
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
    
    // Observe UI state
    val uiState by viewModel.uiState.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val arrowPose by viewModel.arrowPose.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            if (uiState.isInitialized) {
                // AR Camera View
                ARCameraView(
                    modifier = Modifier.fillMaxSize(),
                    lifecycleOwner = lifecycleOwner,
                    viewModel = viewModel,
                    onFrameProcessed = { frame ->
                        // Process frame for landmark recognition
                        viewModel.processFrame(frame)
                    }
                )
                
                // AR Navigation UI Overlay
                ARNavigationOverlay(
                    mainNavController = mainNavController,
                    destination = destination,
                    startLocation = startLocation,
                    uiState = uiState,
                    currentStep = currentStep,
                    onNextStep = { viewModel.nextStep() },
                    onPreviousStep = { viewModel.previousStep() },
                    onResetNavigation = { viewModel.resetNavigation() },
                    onToggleDebug = { viewModel.toggleDebugOverlay() }
                )
                
                // Debug Overlay
                if (debugInfo.isVisible) {
                    DebugOverlay(
                        debugInfo = debugInfo,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
                
            } else if (uiState.isInitializing) {
                // Initialization Screen
                InitializationScreen(
                    progress = uiState.initializationMessage,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (uiState.hasError) {
                // Error Screen
                ErrorScreen(
                    error = uiState.error ?: "Unknown error",
                    onRetry = { 
                        viewModel.initialize(context, ARNavigationConfig())
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Permission Request Screen
            PermissionRequestScreen(
                onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    
    // Permission rationale dialog
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Kamera-Berechtigung") },
            text = { Text("Wir benötigen Zugriff auf die Kamera für die AR-Navigation.") },
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
fun ARCameraView(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner,
    viewModel: ARNavigationViewModel,
    onFrameProcessed: (Mat) -> Unit
) {
    val context = LocalContext.current
    var cameraError by remember { mutableStateOf<String?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    
    // Camera executor for background processing
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
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
                                Log.e("ARCameraView", "No back camera available")
                                cameraError = "Keine Rückkamera verfügbar"
                                return@addListener
                            }
                            
                            // Preview use case
                            val preview = Preview.Builder()
                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                .build()
                                .also { p ->
                                    p.setSurfaceProvider(surfaceProvider)
                                }
                            
                            // Image analysis use case for landmark recognition
                            val analysis = ImageAnalysis.Builder()
                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analyzer ->
                                    analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                                        processImageProxy(imageProxy, onFrameProcessed)
                                    }
                                }
                            
                            imageAnalysis = analysis
                            
                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            
                            // Unbind all previous bindings
                            cameraProvider.unbindAll()
                            
                            // Bind camera to lifecycle
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                analysis
                            )
                            
                            // Start AR session
                            viewModel.startARSession()
                            
                            Log.d("ARCameraView", "Camera initialized successfully")
                            
                        } catch (exc: Exception) {
                            Log.e("ARCameraView", "Camera binding failed", exc)
                            cameraError = "Kamera konnte nicht gestartet werden"
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                } catch (exc: Exception) {
                    Log.e("ARCameraView", "Camera setup failed", exc)
                    cameraError = "Kamera-Initialisierung fehlgeschlagen"
                }
            }
        }
    )
}

/**
 * Process camera image proxy for landmark recognition
 */
private fun processImageProxy(
    imageProxy: ImageProxy,
    onFrameProcessed: (Mat) -> Unit
) {
    try {
        // Convert ImageProxy to OpenCV Mat
        val mat = imageProxyToMat(imageProxy)
        
        // Process the frame
        onFrameProcessed(mat)
        
        // Clean up
        mat.release()
        
    } catch (e: Exception) {
        Log.e("ARCameraView", "Error processing image proxy", e)
    } finally {
        imageProxy.close()
    }
}

/**
 * Convert ImageProxy to OpenCV Mat
 */
private fun imageProxyToMat(imageProxy: ImageProxy): Mat {
    val buffer = imageProxy.planes[0].buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    
    val mat = Mat(imageProxy.height, imageProxy.width, org.opencv.core.CvType.CV_8UC1)
    mat.put(0, 0, data)
    
    return mat
}

@Composable
fun InitializationScreen(
    progress: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = progress,
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Fehler",
                color = Color.Red,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Erneut versuchen")
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable { onRequestPermission() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Kamera erlauben",
            color = Color.White,
            fontSize = 18.sp
        )
    }
}

@Composable
fun DebugOverlay(
    debugInfo: com.example.arwalking.ui.DebugInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(8.dp)
            .width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Debug Info",
                color = Color.White,
                fontSize = 12.sp
            )
            
            debugInfo.navigationStats?.let { stats ->
                Text(
                    text = "Steps: ${stats.completedSteps}/${stats.totalSteps}",
                    color = Color.White,
                    fontSize = 10.sp
                )
                Text(
                    text = "Progress: ${(stats.progress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
            
            debugInfo.performanceMetrics?.let { metrics ->
                Text(
                    text = "FPS: ${metrics.avgMatchingTimeMs}ms",
                    color = Color.White,
                    fontSize = 10.sp
                )
                Text(
                    text = "Hit Rate: ${(metrics.hitRate * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}