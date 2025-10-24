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
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.arwalking.R
import com.example.arwalking.RouteViewModel
import com.example.arwalking.ar.ARCoreArrowView
import com.example.arwalking.components.*
import com.example.arwalking.ar.rendering.*
import com.example.arwalking.data.FavoritesRepository
import com.example.arwalking.ui.theme.GradientUtils
import components.NavigationDrawer
import components.NavigationStepData
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("No NavController provided")
}

@Composable
fun CameraNavigation(
    destination: String = "Unknown destination",
    startLocation: String = "Unknown start"
) {
    val mainNavController = LocalNavController.current

    CameraScreen(
        mainNavController = mainNavController,
        destination = destination,
        startLocation = startLocation
    )
}

@Composable
fun CameraScreen(
    mainNavController: NavController,
    destination: String = "Unknown destination",
    startLocation: String = "Unknown start"
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val routeViewModel: RouteViewModel = viewModel()

    LaunchedEffect(Unit) {
        try {
            val route = routeViewModel.loadRoute(context)
            if (route != null) {
                routeViewModel.logRoute(route)
                routeViewModel.initFeatureMapping(context)
                routeViewModel.startProcessing()
            } else {
                Log.e("CameraScreen", "Failed to load route")
            }
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error initializing RouteViewModel", e)
        }
    }

    val currentRoute by routeViewModel.currentRoute.collectAsState()
    val actualStartLocation = currentRoute?.startPoint ?: startLocation
    val actualDestination = currentRoute?.endPoint ?: destination

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
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
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            ) {
                showRationaleDialog = true
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermission) {
            // Use ARCore with integrated camera and feature matching
            ARCoreArrowView(
                modifier = Modifier.fillMaxSize(),
                routeViewModel = routeViewModel
            )

            NavigationOverlay(
                mainNavController = mainNavController,
                destination = actualDestination,
                startLocation = actualStartLocation,
                routeViewModel = routeViewModel
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { launcher.launch(Manifest.permission.CAMERA) },
                contentAlignment = Alignment.Center
            ) {
                Text("Allow camera access", color = Color.White)
            }
        }
    }

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Camera Permission") },
            text = { Text("We need camera access to show the preview.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                    launcher.launch(Manifest.permission.CAMERA)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationaleDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NavigationOverlay(
    mainNavController: NavController,
    destination: String,
    startLocation: String,
    routeViewModel: RouteViewModel
) {
    val currentRoute by routeViewModel.currentRoute.collectAsState()
    val favorites by FavoritesRepository.favorites.collectAsState()
    val isFavorite = favorites.any {
        it.startLocation == startLocation && it.destination == destination
    }

    val featureMatches by routeViewModel.currentMatches.collectAsState()
    val isFeatureMappingEnabled by routeViewModel.featureMappingEnabled.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        TopGradient()

        TopBar(
            mainNavController = mainNavController,
            destination = destination,
            currentRoute = currentRoute,
            routeViewModel = routeViewModel,
            isFavorite = isFavorite,
            startLocation = startLocation,
            onToggleFavorite = {
                if (isFavorite) {
                    favorites.find {
                        it.startLocation == startLocation && it.destination == destination
                    }?.let { FavoritesRepository.removeFavorite(it) }
                } else {
                    FavoritesRepository.addFavorite(startLocation, destination)
                }
            }
        )

        FeatureMappingStatusIndicator(
            isEnabled = isFeatureMappingEnabled,
            isProcessing = featureMatches.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp, end = 16.dp)
        )

      /*  ARInfoIsland(
            routeViewModel = routeViewModel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp)
                .offset(y = 140.dp)
        )*/

        val navigationSteps = currentRoute?.steps?.map { step ->
            val iconRes = when {
                step.instruction.contains("door", ignoreCase = true) -> R.drawable.navigation21
                step.instruction.contains("left", ignoreCase = true) -> R.drawable.left
                step.instruction.contains("right", ignoreCase = true) -> R.drawable.corner_up_right_1
                step.instruction.contains("straight", ignoreCase = true) -> R.drawable.arrow_up_1
                step.instruction.contains("exit", ignoreCase = true) -> R.drawable.navigation21
                step.instruction.contains("turn", ignoreCase = true) -> R.drawable.corner_up_right_1
                else -> R.drawable.arrow_up_1
            }
            NavigationStepData(
                text = step.instruction.replace(Regex("</?b>"), ""),
                icon = iconRes
            )
        } ?: emptyList()

        val currentStepNumber by routeViewModel.currentStep.collectAsState()
        val safeStepNumber = maxOf(0, currentStepNumber)  // Prevent negative values
        val visibleSteps = navigationSteps.drop(safeStepNumber)

        NavigationDrawer(
            navigationSteps = visibleSteps,
            destinationLabel = destination,
            onClose = {},
            onNextStep = { routeViewModel.skipStep() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

    }
}

@Composable
private fun TopGradient() {
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
}

@Composable
private fun TopBar(
    mainNavController: NavController,
    destination: String,
    currentRoute: com.example.arwalking.NavigationRoute?,
    routeViewModel: RouteViewModel,
    isFavorite: Boolean,
    startLocation: String,
    onToggleFavorite: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = 70.dp)
            .padding(horizontal = 20.dp)
    ) {
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

        val currentStepIndex by routeViewModel.currentStep.collectAsState()
        val currentLandmarkId = currentRoute?.steps?.getOrNull(currentStepIndex)
            ?.landmarks?.firstOrNull()?.id
        val displayText = currentLandmarkId ?: destination

        Text(
            text = displayText,
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
        )

        Icon(
            painter = painterResource(
                id = if (isFavorite) R.drawable.star_filled else R.drawable.star_outline
            ),
            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
            tint = Color.Unspecified,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(35.dp)
                .clickable { onToggleFavorite() }
                .padding(4.dp)
        )
    }
}


@Composable
private fun ARInfoIsland(
    routeViewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val matches by routeViewModel.currentMatches.collectAsState()
    val isEnabled by routeViewModel.featureMappingEnabled.collectAsState()

    val landmarkCount = matches.size
    val bestConfidence = matches.maxOfOrNull { it.confidence } ?: 0f
    val isTracking = matches.isNotEmpty()

    val arStatus = rememberARScanStatus(
        isInitialized = isEnabled,
        landmarkCount = landmarkCount,
        bestConfidence = bestConfidence,
        isTracking = isTracking
    )

    ExpandedARInfoIsland(
        scanStatus = arStatus,
        landmarkCount = landmarkCount,
        confidence = bestConfidence,
        modifier = modifier,
        isVisible = isEnabled
    )
}

@Composable
private fun CameraPreview(
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
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    if (cameraError != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera error: $cameraError",
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
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            cameraError = "No back camera available"
                            return@addListener
                        }

                        val preview = Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                            .build()
                            .also { it.setSurfaceProvider(surfaceProvider) }

                        val imageAnalysis = onFrameProcessed?.let {
                            ImageAnalysis.Builder()
                                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .apply {
                                    setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                        try {
                                            imageProxyToBitmap(imageProxy)?.let { bitmap ->
                                                onFrameProcessed(bitmap)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Frame processing error", e)
                                        } finally {
                                            imageProxy.close()
                                        }
                                    }
                                }
                        }

                        cameraProvider.unbindAll()

                        val useCases = listOfNotNull(preview, imageAnalysis).toTypedArray()
                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            *useCases
                        )

                        camera = boundCamera
                        setupZoomCapabilities(boundCamera, onAvailableZoomRatiosChanged)
                        boundCamera.cameraControl.setZoomRatio(zoomRatio)

                        Log.d(TAG, "Camera initialized")
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera binding failed", e)
                        cameraError = "Could not start camera"
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    return try {
        when (imageProxy.format) {
            ImageFormat.YUV_420_888 -> {
                val nv21 = yuv420ToNv21(imageProxy)
                val yuvImage = YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    imageProxy.width,
                    imageProxy.height,
                    null
                )
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(
                    Rect(0, 0, imageProxy.width, imageProxy.height),
                    90,
                    out
                )
                val bytes = out.toByteArray()
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
            }
            ImageFormat.JPEG -> {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)
            }
            else -> {
                Log.w(TAG, "Unsupported image format: ${imageProxy.format}")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "ImageProxy conversion failed", e)
        null
    }
}

private fun yuv420ToNv21(imageProxy: ImageProxy): ByteArray {
    val yPlane = imageProxy.planes[0]
    val uPlane = imageProxy.planes[1]
    val vPlane = imageProxy.planes[2]

    val ySize = yPlane.buffer.remaining()
    val uSize = uPlane.buffer.remaining()
    val vSize = vPlane.buffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yPlane.buffer.get(nv21, 0, ySize)

    val uvPixelStride = uPlane.pixelStride
    if (uvPixelStride == 1) {
        uPlane.buffer.get(nv21, ySize, uSize)
        vPlane.buffer.get(nv21, ySize + uSize, vSize)
    } else {
        val uvBuffer = ByteArray(uSize + vSize)
        uPlane.buffer.get(uvBuffer, 0, uSize)
        vPlane.buffer.get(uvBuffer, uSize, vSize)

        var uvIndex = 0
        for (i in 0 until uSize) {
            nv21[ySize + uvIndex] = uvBuffer[uSize + i]
            nv21[ySize + uvIndex + 1] = uvBuffer[i]
            uvIndex += 2
        }
    }

    return nv21
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return bitmap

    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }

    return try {
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        rotated
    } catch (e: OutOfMemoryError) {
        Log.e(TAG, "Out of memory rotating bitmap", e)
        bitmap
    }
}

private fun setupZoomCapabilities(
    camera: Camera,
    onAvailableZoomRatiosChanged: (List<Float>) -> Unit
) {
    val zoomState = camera.cameraInfo.zoomState.value ?: run {
        onAvailableZoomRatiosChanged(listOf(0.7f, 1.0f, 2.0f))
        return
    }

    val minZoom = zoomState.minZoomRatio
    val maxZoom = zoomState.maxZoomRatio

    val ratios = buildList {
        if (minZoom < 1.0f) add(maxOf(minZoom, 0.5f))
        add(1.0f)
        if (maxZoom >= 2.0f) add(2.0f)
        if (minZoom >= 1.0f && size < 3) add(0, 0.7f)
    }

    onAvailableZoomRatiosChanged(ratios)
}

private const val TAG = "CameraNavigation"