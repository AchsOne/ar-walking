package com.example.arwalking.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.arwalking.RouteViewModel
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.Point
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.Node
import com.example.arwalking.ar.ArrowController
import com.google.ar.core.Pose
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * ARCoreArrowView
 * - Controls camera via ARCore (ArSceneView)
 * - Performs center-screen hit testing against horizontal planes
 * - Anchors a simple 3D arrow (built from cubes) on the ground
 * - Feeds CPU camera frames into RouteViewModel for landmark recognition
 * - Confidence threshold for landmark recognition: 60%
 */

// Confidence-Schwelle für zuverlässige Landmarken-Erkennung
private const val MIN_CONFIDENCE_FOR_ARROW = 0.60f // 60%
private const val MIN_MATCHES_FOR_ARROW = 3 // Mindestanzahl Feature-Matches
// Camera-relative Platzierung Konfiguration
private const val ARROW_DISTANCE_M = 1.5f      // Wie weit vor Kamera
private const val ARROW_HEIGHT_OFFSET_M = -1.2f // Wie tief unter Kamera
@Composable
fun ARCoreArrowView(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    routeViewModel: RouteViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var asvRef by androidx.compose.runtime.remember { mutableStateOf<ArSceneView?>(null) }
    var sessionConfigured by androidx.compose.runtime.remember { mutableStateOf(false) }

    // Navigation state
    val arrowState by routeViewModel.arrowState.collectAsState()
    val isEnabled by routeViewModel.featureMappingEnabled.collectAsState()
    val matches by routeViewModel.currentMatches.collectAsState()

    // Get camera permission state from context
    val hasCameraPermission = remember {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val arSceneView = ArSceneView(ctx)
            Log.d("ARCoreArrow", "ArSceneView created")
            // Do not setup session yet; wait for camera permission
            // Session will be configured in update() when hasCameraPermission = true

            // Persist ArrowController on the view so update() can access the same instance
            val controller = ArrowController(ctx)
            arSceneView.tag = controller

            arSceneView.scene.addOnUpdateListener { _ ->
                val frame = arSceneView.arFrame ?: return@addOnUpdateListener
                val session = arSceneView.session ?: return@addOnUpdateListener
                
                try {
                    Log.v("ARCoreArrow", "Scene tick")
                    
                    val controller = (arSceneView.tag as? ArrowController) ?: ArrowController(arSceneView.context).also { arSceneView.tag = it }
                    controller.attachTo(arSceneView.scene)
                    
                    // Diagnostic: camera tracking state, planes, recognition
                    val camState = frame.camera.trackingState
                    val planesAllDiag = session.getAllTrackables(Plane::class.java)
                    val trackedCountDiag = planesAllDiag.count { plane -> plane.trackingState == TrackingState.TRACKING }
                    val matchesNow = try { routeViewModel.currentMatches.value } catch (_: Exception) { emptyList() }
                    val top = matchesNow.firstOrNull()
                    val recognizedNow = top?.let { 
                        it.matchCount >= MIN_MATCHES_FOR_ARROW && it.confidence >= MIN_CONFIDENCE_FOR_ARROW 
                    } == true
                    val topInfo = top?.let { 
                        val conf = (it.confidence * 100).toInt()
                        val status = if (it.confidence >= MIN_CONFIDENCE_FOR_ARROW) "✓" else "✗"
                        "${it.landmark.id} ${it.matchCount}m ${conf}% ${status}"
                    } ?: "none"
                    val anchorSet = controller.isAnchored()
                    val dbg = "cam=${camState}, planes=${trackedCountDiag}, matches=${matchesNow.size}, top=${topInfo}, anchor=${anchorSet}"
                    Log.v("ARCoreArrow", "Tick diag: ${dbg}")
                } catch (e: Exception) {
                    Log.e("ARCoreArrow", "Scene update error: ${e.message}", e)
                    return@addOnUpdateListener
                }

                // Only continue when ARCore tracking is active  
                val currentCamState = try { frame.camera.trackingState } catch (e: Exception) { TrackingState.STOPPED }
                if (currentCamState != TrackingState.TRACKING) {
                    Log.v("ARCoreArrow", "Arrow not processed: camera not TRACKING (${currentCamState})")
                    return@addOnUpdateListener
                }

                // 1) Feed frame to OpenCV feature mapping using raw ARCore camera image (YUV_420_888)
                if (hasCameraPermission && isEnabled) {
                    try {
                        tryAcquireCameraImage(frame, routeViewModel, arSceneView.context)
                    } catch (e: Exception) {
                        Log.w("ARCoreArrow", "Camera image processing error: ${e.message}")
                    }
                }

                // 2) New route-based arrow logic
                val controller = (arSceneView.tag as? ArrowController) ?: return@addOnUpdateListener
                if (!isEnabled) {
                    Log.v("ARCoreArrow", "Arrow hidden: feature mapping disabled")
                    controller.clear()
                    return@addOnUpdateListener
                }
                
                // Update position for smart step progression (async)
                kotlinx.coroutines.GlobalScope.launch {
                    try {
                        routeViewModel.updatePosition(frame)
                    } catch (e: Exception) {
                        Log.w("ARCoreArrow", "updatePosition failed: ${e.message}")
                    }
                }
                
                // Route-based arrow logic with persistent anchoring
                val currentStep = routeViewModel.currentStep.value
                val currentRoute = routeViewModel.currentRoute.value
                
                if (currentRoute == null || currentStep >= currentRoute.steps.size) {
                    controller.clear()
                    return@addOnUpdateListener
                }
                
                val step = currentRoute.steps[currentStep]
                val instruction = step.instruction
                
                // Determine if we should show arrow based on step type and conditions
                // RULE: Arrow only visible when feature mapping is enabled AND conditions are met
                val shouldShowArrow = when {
                    // Landmark-based steps: show only when we detect the landmark with good confidence
                    step.landmarks.isNotEmpty() -> {
                        val matches = try { routeViewModel.currentMatches.value } catch (e: Exception) { emptyList() }
                        val hasGoodMatch = matches.isNotEmpty() && matches.first().confidence >= MIN_CONFIDENCE_FOR_ARROW
                        Log.d("ARCoreArrow", "Landmark step $currentStep: ${matches.size} matches, best=${matches.firstOrNull()?.let { "${(it.confidence*100).toInt()}%" } ?: "none"}, show=$hasGoodMatch")
                        hasGoodMatch
                    }
                    // Direction-based steps (no landmarks): show when feature mapping is active
                    // This ensures the user has started the AR session properly
                    else -> {
                        val featureMappingActive = try { routeViewModel.featureMappingEnabled.value } catch (e: Exception) { false }
                        Log.d("ARCoreArrow", "Direction step $currentStep: '${instruction}' - feature mapping active: $featureMappingActive, show=$featureMappingActive")
                        featureMappingActive
                    }
                }
                
                if (!shouldShowArrow) {
                    Log.v("ARCoreArrow", "Arrow conditions not met for step $currentStep")
                    return@addOnUpdateListener
                }
                
                // ========================================
                // Direction Calculation Based on Instruction
                // ========================================
                
                val targetYaw = calculateArrowDirection(instruction)
                Log.d("ARCoreArrow", "Arrow visible: step=$currentStep, instruction='$instruction', yaw=${"%.1f".format(targetYaw)}°")

                // Persistent Arrow Placement - Anchor bleibt nach erfolgreichem Platzieren bestehen
                try {
                    val stepKey = "step_${currentStep}_${instruction.hashCode()}"
                    val isNewStep = controller.getCurrentStepKey() != stepKey
                    
                    if (!controller.isAnchored() || isNewStep) {
                        // Clear old arrow for new step
                        if (isNewStep) {
                            controller.clear()
                            Log.d("ARCoreArrow", "Cleared arrow for step transition to: $stepKey")
                        }
                        
                        // Neuer Step oder noch kein Pfeil → erstelle camera-relative Anchor
                        val cameraPose = frame.camera.pose

                        // Berechne Forward-Vektor (ARROW_DISTANCE_M vor Kamera)
                        val forward = floatArrayOf(0f, 0f, -ARROW_DISTANCE_M)
                        val rotated = FloatArray(3)
                        cameraPose.rotateVector(forward, 0, rotated, 0)

                        // Zielposition: Kamera + Forward, abgesenkt auf Bodenhöhe
                        val targetX = cameraPose.tx() + rotated[0]
                        val targetY = cameraPose.ty() + ARROW_HEIGHT_OFFSET_M
                        val targetZ = cameraPose.tz() + rotated[2]

                        // Erstelle Anchor an berechneter Position
                        val targetPose = Pose(
                            floatArrayOf(targetX, targetY, targetZ),
                            floatArrayOf(0f, 0f, 0f, 1f)  // Keine Rotation
                        )
                        val anchor = arSceneView.session?.createAnchor(targetPose)

                        if (anchor != null) {
                            controller.placeAnchor(arSceneView.scene, anchor, targetYaw)
                            controller.setCurrentStepKey(stepKey) // Markiere aktuellen Step
                            Log.d("ARCoreArrow", "Anchor placed for $stepKey at (${"%.2f".format(targetX)}, ${"%.2f".format(targetY)}, ${"%.2f".format(targetZ)}), yaw=${"%.1f".format(targetYaw)}°")
                        } else {
                            Log.e("ARCoreArrow", "Failed to create anchor for $stepKey")
                        }
                    } else {
                        // Pfeil existiert bereits für diesen Step → nur Richtung bei Bedarf updaten
                        Log.v("ARCoreArrow", "Arrow persistent for $stepKey, yaw=${"%.1f".format(targetYaw)}°")
                    }
                } catch (e: Exception) {
                    Log.e("ARCoreArrow", "Arrow placement error: ${e.message}", e)
                }
            }

            asvRef = arSceneView
            arSceneView
        },
        update = { arSceneView ->
            asvRef = arSceneView

            // Lazy session setup when permission is granted
            if (hasCameraPermission && !sessionConfigured) {
                try {
                    val session = Session(arSceneView.context)
                    val config = Config(session).apply {
                        // Track both horizontal and vertical planes to speed up first valid hits
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                        lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                        // Depth disabled for stability on devices that report invalid depth
                        depthMode = Config.DepthMode.DISABLED
                        // Improve sharpness: enable continuous autofocus when available
                        focusMode = Config.FocusMode.AUTO
                        // Enable instant placement so we can anchor quickly without a mapped plane
                        instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                        // Sceneform requires LATEST_CAMERA_IMAGE; BLOCKING will crash
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    }
                    session.configure(config)
                    arSceneView.setupSession(session)
                    routeViewModel.setSession(session)
                    Log.d("ARCoreArrow", "Session configured; resuming ArSceneView")
                    try { arSceneView.resume() } catch (e: Exception) { Log.e("ARCoreArrow", "resume fail: ${e.message}") }
                    sessionConfigured = true
                } catch (e: Exception) {
                    Log.e("ARCoreArrow", "Session setup failed: ${e.message}")
                }
            }
        },
        onRelease = { view ->
            (view as? ArSceneView)?.let { asv ->
                try { asv.pause() } catch (_: Exception) {}
                try { asv.session?.close() } catch (_: Exception) {}
                // Clear state tag to avoid stale references
                asv.tag = null
                asv.destroy()
            }
        }
    )

    // Manage resume/pause with lifecycle and permission
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, hasCameraPermission) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val asv = asvRef
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (hasCameraPermission && sessionConfigured) {
                        try { asv?.resume() } catch (e: Exception) { Log.e("ARCoreArrow", "resume fail: ${e.message}") }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try { asv?.pause() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try { asvRef?.pause() } catch (_: Exception) {}
        }
    }
}

private fun performCenterHitTest(frame: Frame, x: Float, y: Float): HitResult? {
    val hits = frame.hitTest(x, y)
    for (hit in hits) {
        val trackable = hit.trackable
        if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose) && trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
            val p = hit.hitPose
            val dx = p.tx(); val dy = p.ty(); val dz = p.tz()
            val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            Log.d("ArrowPos", "CenterHit: pose=(${"%.2f".format(dx)}, ${"%.2f".format(dy)}, ${"%.2f".format(dz)}), dist=${"%.2f".format(dist)}m, trackable=Plane")
            return hit
        }
        if (trackable is Point) {
            val p = hit.hitPose
            val dx = p.tx(); val dy = p.ty(); val dz = p.tz()
            val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            Log.d("ArrowPos", "CenterHit: pose=(${"%.2f".format(dx)}, ${"%.2f".format(dy)}, ${"%.2f".format(dz)}), dist=${"%.2f".format(dist)}m, trackable=Point")
            return hit
        }
    }
    Log.d("ArrowPos", "CenterHit: no valid plane/point hit at (${"%.1f".format(x)}, ${"%.1f".format(y)})")
    return null
}

private fun performNeighborHitTest(frame: Frame, cx: Float, cy: Float, w: Int, h: Int): HitResult? {
    // Sample neighborhood around center at multiple radii to find a plane/point hit
    val radii = floatArrayOf(0.05f, 0.10f, 0.15f)
    for (r in radii) {
        val dx = w * r
        val dy = h * r
        val candidates = listOf(
            Pair(cx, cy),
            Pair(cx - dx, cy), Pair(cx + dx, cy),
            Pair(cx, cy - dy), Pair(cx, cy + dy),
            Pair(cx - dx, cy - dy), Pair(cx + dx, cy - dy),
            Pair(cx - dx, cy + dy), Pair(cx + dx, cy + dy)
        )
        for ((x, y) in candidates) {
            val clampedX = x.coerceIn(0f, (w - 1).toFloat())
            val clampedY = y.coerceIn(0f, (h - 1).toFloat())
            val hit = performCenterHitTest(frame, clampedX, clampedY)
            if (hit != null) {
                Log.d("ArrowPos", "NeighborHit: used point=(${"%.1f".format(clampedX)}, ${"%.1f".format(clampedY)}), radius=${(r*100).toInt()}%")
                return hit
            }
        }
    }
    Log.d("ArrowPos", "NeighborHit: no hit around center neighborhood (multi-radius)")
    return null
}

private class ArrowState {
    var anchor: Anchor? = null
    var anchorNode: AnchorNode? = null
    var arrowNode: Node? = null

    fun clearArrow() {
        try {
            arrowNode?.setParent(null)
            arrowNode = null
            anchorNode?.anchor?.detach()
            anchorNode?.setParent(null)
            anchorNode = null
            anchor = null
        } catch (_: Exception) {}
    }

    fun placeOrUpdateArrow(scene: Scene, newAnchor: Anchor, yawDeg: Float) {
        // Replace existing anchor/anchorNode if different
        val replacing = (anchor != null)
        try { anchorNode?.anchor?.detach() } catch (_: Exception) {}
        anchor = newAnchor
        if (anchorNode == null) {
            anchorNode = AnchorNode(newAnchor)
            anchorNode!!.setParent(scene)
        } else {
            anchorNode!!.anchor = newAnchor
        }

        if (arrowNode == null) {
            arrowNode = createArrowNode(scene.view.context)
            arrowNode!!.setParent(anchorNode)
        } else if (arrowNode!!.parent !== anchorNode) {
            arrowNode!!.setParent(anchorNode)
        }

        // Place relative to anchor origin
        arrowNode!!.localPosition = Vector3(0f, 0f, 0f)
        arrowNode!!.localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), yawDeg)

        val pose = newAnchor.pose
        val world = anchorNode!!.worldPosition
        Log.d(
            "ArrowPos",
            (if (replacing) "ReplacedAnchor" else "Placed") +
                ": anchorPose=(${"%.2f".format(pose.tx())}, ${"%.2f".format(pose.ty())}, ${"%.2f".format(pose.tz())}), " +
                "anchorWorld=(${"%.2f".format(world.x)}, ${"%.2f".format(world.y)}, ${"%.2f".format(world.z)}), yaw=${"%.1f".format(yawDeg)}"
        )
    }

    fun updateArrowYaw(yawDeg: Float) {
        arrowNode?.let { node ->
            node.localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), yawDeg)
            val world = node.worldPosition
            Log.d("ArrowPos", "YawUpdate: yaw=${"%.1f".format(yawDeg)} at world=(${"%.2f".format(world.x)}, ${"%.2f".format(world.y)}, ${"%.2f".format(world.z)})")
        }
    }

    fun placeOrUpdateFallback(scene: Scene, yawDeg: Float) {
        if (arrowNode == null) {
            arrowNode = createArrowNode(scene.view.context)
        }
        val cam = scene.camera
        if (arrowNode!!.parent != cam) {
            arrowNode!!.setParent(cam)
        }
        // Slightly above ground estimate and 2m ahead
        arrowNode!!.localPosition = Vector3(0f, -1.2f, -2.0f)
        arrowNode!!.localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), yawDeg)
        Log.d("ArrowPos", "Fallback: camera-relative at local=(0.00, -1.20, -2.00), yaw=${"%.1f".format(yawDeg)}")
    }
}

private fun createArrowNode(context: Context): Node {
    val node = Node()

    // Materials
    val colorBody = Color(0.0f, 0.72f, 0.83f) // turquoise
    val colorHead = Color(0.15f, 0.82f, 0.91f)

    // Build two cubes: stem and head, both very thin in Y to look flattened on ground
    val height = 0.01f // thin height to "lie" on ground

    // Stem: length 0.35m, width 0.05m
    val stemSize = Vector3(0.05f, height, 0.35f)
    val headSize = Vector3(0.14f, height, 0.14f)

    MaterialFactory.makeOpaqueWithColor(context, colorBody).thenAccept { stemMat ->
        MaterialFactory.makeOpaqueWithColor(context, colorHead).thenAccept { headMat ->
            try {
                val stem = ShapeFactory.makeCube(stemSize, Vector3(0f, 0f, -stemSize.z / 2f), stemMat)
                val head = ShapeFactory.makeCube(headSize, Vector3(0f, 0f, headSize.z / 2f), headMat)

                val stemNode = Node().apply {
                    renderable = stem
                }
                val headNode = Node().apply {
                    renderable = head
                }
                node.addChild(stemNode)
                node.addChild(headNode)
            } catch (e: Exception) {
                Log.e("ARCoreArrow", "Failed to build arrow renderables: ${e.message}", e)
            }
        }
    }

    return node
}

private var lastAcquireTimeMs = 0L
private var acquireInProgress = false

private fun tryAcquireCameraImage(frame: Frame, vm: RouteViewModel, context: Context) {
    val now = System.currentTimeMillis()
    if (acquireInProgress || now - lastAcquireTimeMs < 500L) return
    try {
        // Attempt to acquire the CPU image for this frame
        val image = frame.acquireCameraImage()
        acquireInProgress = true
        try {
            // Preferred fast path: use Y plane directly as grayscale Mat
            val gray = imageToGrayMat(image)
            if (gray != null) {
                // Clone before handing over to VM because VM processes asynchronously
                val safeClone = try { gray.clone() } catch (_: Exception) { null }
                try {
                    if (safeClone != null) {
                        // Use existing processFrame method with conversion from Mat to Bitmap
                        val bitmap = matToBitmap(safeClone)
                        if (bitmap != null) {
                            // Run processFrame in coroutine with scene context
                            GlobalScope.launch {
                                try {
                                    vm.processFrame(bitmap, context)
                                } catch (e: Exception) {
                                    Log.w("ARCoreArrow", "processFrame failed: ${e.message}")
                                }
                            }
                        }
                    }
                } finally {
                    try { gray.release() } catch (_: Exception) {}
                }
            } else {
                // Fallback: legacy Bitmap path
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    try {
                        GlobalScope.launch {
                            try {
                                vm.processFrame(bitmap, context)
                            } catch (e: Exception) {
                                Log.w("ARCoreArrow", "processFrame fallback failed: ${e.message}")
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            try { image.close() } catch (_: Exception) {}
            acquireInProgress = false
            lastAcquireTimeMs = now
        }
    } catch (e: Exception) {
        // NotYetAvailable or other transient errors; just skip this frame
    }
}

private fun imageToBitmap(image: Image): Bitmap? {
    return try {
        if (image.format != ImageFormat.YUV_420_888) return null
        val nv21 = yuv420ToNv21(image)
        val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 90, out)
        val bytes = out.toByteArray()
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("ARCoreArrow", "imageToBitmap failed: ${e.message}", e)
        null
    }
}

private fun yuv420ToNv21(image: Image): ByteArray {
    val width = image.width
    val height = image.height
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yRowStride = yPlane.rowStride
    val yPixelStride = yPlane.pixelStride // usually 1
    val uvRowStride = uPlane.rowStride
    val uvPixelStride = uPlane.pixelStride // usually 2

    val nv21 = ByteArray(width * height + width * height / 2)

    // Copy Y taking into account row and pixel stride
    var outIndex = 0
    val yBuffer = yPlane.buffer
    val yRow = ByteArray(yRowStride)
    for (row in 0 until height) {
        yBuffer.position(row * yRowStride)
        yBuffer.get(yRow, 0, yRowStride)
        var col = 0
        while (col < width) {
            nv21[outIndex++] = yRow[col * yPixelStride]
            col++
        }
    }

    // Copy UV and interleave as NV21 (V,U)
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    val vRowStride = vPlane.rowStride
    val vPixelStride = vPlane.pixelStride

    val uRow = ByteArray(uvRowStride)
    val vRow = ByteArray(vRowStride)

    val uvWidth = width / 2
    val uvHeight = height / 2

    for (row in 0 until uvHeight) {
        uBuffer.position(row * uvRowStride)
        vBuffer.position(row * vRowStride)
        uBuffer.get(uRow, 0, uvRowStride)
        vBuffer.get(vRow, 0, vRowStride)
        var col = 0
        while (col < uvWidth) {
            val v = vRow[col * vPixelStride]
            val u = uRow[col * uvPixelStride]
            nv21[outIndex++] = v
            nv21[outIndex++] = u
            col++
        }
    }
    return nv21
}

private fun matToBitmap(mat: Mat): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        bitmap
    } catch (e: Exception) {
        Log.e("ARCoreArrow", "matToBitmap failed: ${e.message}", e)
        null
    }
}

private fun imageToGrayMat(image: Image): Mat? {
    return try {
        if (image.format != ImageFormat.YUV_420_888) return null
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        val gray = Mat(height, width, CvType.CV_8UC1)

        if (yPixelStride == 1 && yRowStride == width) {
            // Fast-path: direct copy
            val bytes = ByteArray(width * height)
            yBuffer.position(0)
            yBuffer.get(bytes, 0, bytes.size)
            gray.put(0, 0, bytes)
        } else {
            // General path: copy row by row honoring rowStride and pixelStride
            val rowData = ByteArray(yRowStride)
            var row = 0
            while (row < height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(rowData, 0, yRowStride)
                // Extract each pixel with pixelStride
                val outLine = ByteArray(width)
                var col = 0
                while (col < width) {
                    outLine[col] = rowData[col * yPixelStride]
                    col++
                }
                gray.put(row, 0, outLine)
                row++
            }
        }
        gray
    } catch (e: Exception) {
        Log.e("ARCoreArrow", "imageToGrayMat failed: ${e.message}", e)
        null
    }
}

private fun performInstantPlacementHitTest(frame: Frame, centerX: Float, centerY: Float, estimatedDistanceM: Float): HitResult? {
    return try {
        val hits = frame.hitTestInstantPlacement(centerX, centerY, estimatedDistanceM)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is InstantPlacementPoint || trackable is Point || trackable is Plane) {
                val p = hit.hitPose
                val dx = p.tx(); val dy = p.ty(); val dz = p.tz()
                val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                Log.d("ArrowPos", "InstantHit: pose=(${"%.2f".format(dx)}, ${"%.2f".format(dy)}, ${"%.2f".format(dz)}), dist=${"%.2f".format(dist)}m, trackable=${trackable.javaClass.simpleName}")
                return hit
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

private fun extractYawFromPose(pose: Pose): Float {
    // Extract rotation from pose quaternion and convert to yaw angle
    val qx = pose.qx()
    val qy = pose.qy()
    val qz = pose.qz()
    val qw = pose.qw()
    
    // Convert quaternion to yaw (rotation around Y axis)
    val yaw = kotlin.math.atan2(2.0 * (qw * qy + qx * qz), 1.0 - 2.0 * (qy * qy + qz * qz))
    return Math.toDegrees(yaw).toFloat()
}

/**
 * Berechnet die Pfeil-Richtung basierend auf der Instruction
 * Basierend auf der JSON-Route-Struktur und Ihrem Plan
 */
private fun calculateArrowDirection(instruction: String): Float {
    val cleanInstruction = instruction.replace(Regex("</?b>"), "").lowercase()
    
    // Calculate base direction
    val baseYaw = when {
        // Richtungsangaben (häufigste Fälle zuerst)
        cleanInstruction.contains("links ab") || cleanInstruction.contains("turn left") -> -90f
        cleanInstruction.contains("rechts ab") || cleanInstruction.contains("turn right") -> 90f
        cleanInstruction.contains("geradeaus") || cleanInstruction.contains("straight") -> 0f
        
        // Detailliertere Richtungen
        cleanInstruction.contains("scharf links") || cleanInstruction.contains("sharp left") -> -120f
        cleanInstruction.contains("scharf rechts") || cleanInstruction.contains("sharp right") -> 120f
        cleanInstruction.contains("leicht links") || cleanInstruction.contains("slight left") -> -45f
        cleanInstruction.contains("leicht rechts") || cleanInstruction.contains("slight right") -> 45f
        
        // U-Turn
        cleanInstruction.contains("umkehren") || cleanInstruction.contains("u-turn") -> 180f
        
        // Durchgang/Tür-basierte Anweisungen - meist geradeaus
        cleanInstruction.contains("durch") || cleanInstruction.contains("through") ||
        cleanInstruction.contains("tür") || cleanInstruction.contains("door") ||
        cleanInstruction.contains("verlassen") || cleanInstruction.contains("leave") -> 0f
        
        // Treppe
        cleanInstruction.contains("treppe") || cleanInstruction.contains("stairs") -> {
            when {
                cleanInstruction.contains("hinauf") || cleanInstruction.contains("up") -> 0f
                cleanInstruction.contains("hinab") || cleanInstruction.contains("down") -> 0f
                else -> 0f
            }
        }
        
        // Standard: geradeaus
        else -> 0f
    }
    
    // INVERT yaw for ARCore coordinate system (left-handed vs right-handed)
    // ARCore Y-rotation is inverted compared to intuitive direction
    return -baseYaw
}

