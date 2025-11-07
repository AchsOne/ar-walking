package com.example.arwalking.ar.rendering

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.util.Log

/**
 * AR Info Island - semi-transparent UI component
 * Shows scan status and assists with AR navigation
 */
@Composable
fun ARInfoIsland(
    scanStatus: ARScanStatus,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    if (!isVisible) return
    
    // Animation for fade in/out
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 0.85f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "island_alpha"
    )
    
    // Pulsing effect for certain statuses
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .clip(RoundedCornerShape(25.dp))
                .blur(radius = 0.5.dp), // Subtle blur effect
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = alpha)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status icon with animation
                val iconAlpha = if (scanStatus.shouldPulse) pulseAlpha else 1f
                Icon(
                    imageVector = scanStatus.icon,
                    contentDescription = null,
                    tint = scanStatus.color.copy(alpha = iconAlpha),
                    modifier = Modifier.size(20.dp)
                )
                
                // Status text
                Text(
                    text = scanStatus.message,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                // Optional progress indicator
                if (scanStatus.showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = scanStatus.color,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

/**
 * Expanded AR Info Island with more information
 */
@Composable
fun ExpandedARInfoIsland(
    scanStatus: ARScanStatus,
    landmarkCount: Int = 0,
    confidence: Float = 0f,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    if (!isVisible) return
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 0.85f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "expanded_island_alpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .blur(radius = 0.5.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = alpha)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Main status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = scanStatus.icon,
                        contentDescription = null,
                        tint = scanStatus.color,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Text(
                        text = scanStatus.message,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // 📊 EXTENDED information display - ALWAYS visible for debug
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Zeige IMMER Landmark-Count (auch wenn 0)
                    InfoChip(
                        label = "Landmarks",
                        value = landmarkCount.toString(),
                        color = if (landmarkCount > 0) Color.Blue else Color.Gray
                    )
                    
                    // Zeige IMMER Confidence (auch bei 0%)
                    InfoChip(
                        label = "AKAZE",
                        value = "${(confidence * 100).toInt()}%",
                        color = getConfidenceColor(confidence)
                    )
                }
                
                // 🔍 ZUSÄTZLICHE DEBUG-INFO: Zeige den besten Match
                if (landmarkCount > 0) {
                    Text(
                        text = "Beste Erkennung: ${(confidence * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
        Text(
            text = value,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * AR scan status data class
 */
data class ARScanStatus(
    val message: String,
    val icon: ImageVector,
    val color: Color,
    val shouldPulse: Boolean = false,
    val showProgress: Boolean = false
) {
    companion object {
        val INITIALIZING = ARScanStatus(
            message = "AR wird initialisiert...",
            icon = Icons.Default.CameraAlt,
            color = Color.Blue,
            showProgress = true
        )
        
        val SCANNING = ARScanStatus(
            message = "Suche nach Landmarks...",
            icon = Icons.Default.Search,
            color = Color.Yellow,
            shouldPulse = true
        )
        
        val LANDMARK_FOUND = ARScanStatus(
            message = "Landmark erkannt",
            icon = Icons.Default.CheckCircle,
            color = Color.Green
        )
        
        val MOVE_CAMERA = ARScanStatus(
            message = "Bewege Kamera langsam",
            icon = Icons.Default.CameraAlt,
            color = Color(0xFFFF9800),
            shouldPulse = true
        )
        
        val LOW_CONFIDENCE = ARScanStatus(
            message = "Bessere Beleuchtung benötigt",
            icon = Icons.Default.Warning,
            color = Color.Red,
            shouldPulse = true
        )
        
        val TRACKING_LOST = ARScanStatus(
            message = "Tracking verloren",
            icon = Icons.Default.Warning,
            color = Color.Red
        )
        
        fun custom(message: String, color: Color = Color.White) = ARScanStatus(
            message = message,
            icon = Icons.Default.CameraAlt,
            color = color
        )
    }
}

/**
 * Determines the color based on confidence - AKAZE-optimized
 */
private fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.6f -> Color.Green         // Green: Very good (60%+)
        confidence >= 0.3f -> Color.Yellow        // Yellow: Good (30-60%)
        confidence >= 0.15f -> Color(0xFFFF9800) // Orange: Acceptable (15-30%)
        confidence > 0f -> Color.Red              // Red: Weak (1-15%)
        else -> Color.Gray                        // Gray: No detection (0%)
    }
}

/**
 * Hook for automatic status updates based on AR state
 */
@Composable
fun rememberARScanStatus(
    isInitialized: Boolean,
    landmarkCount: Int,
    bestConfidence: Float,
    isTracking: Boolean
): ARScanStatus {
    var currentStatus by remember { mutableStateOf(ARScanStatus.INITIALIZING) }
    var stableTrackingState by remember { mutableStateOf(false) } // Initial false
    var lastTrackingTime by remember { mutableStateOf(0L) } // Initial 0
    var wasTrackingBefore by remember { mutableStateOf(false) } // Neue Variable

    // Debug logging for all parameters on each change
    LaunchedEffect(isInitialized, landmarkCount, bestConfidence, isTracking) {
        Log.d("ARScan", "=== Parameter Update ===")
        Log.d("ARScan", "isInitialized: $isInitialized (should be true when AR is ready)")
        Log.d("ARScan", "landmarkCount: $landmarkCount (should be > 0 when landmarks detected)")
        Log.d("ARScan", "bestConfidence: $bestConfidence (should be 0.0-1.0, >0.7 is good)")
        Log.d("ARScan", "isTracking: $isTracking (should reflect real tracking state)")
        Log.d("ARScan", "wasTrackingBefore: $wasTrackingBefore")
        Log.d("ARScan", "stableTrackingState: $stableTrackingState")
    }

    // Stabilize tracking status to avoid false "tracking lost"
    LaunchedEffect(isTracking) {
        Log.d("ARScan", "--- Tracking State Change ---")
        Log.d("ARScan", "isTracking changed to: $isTracking, wasTrackingBefore: $wasTrackingBefore")

        if (isTracking) {
            Log.d("ARScan", "Tracking started - setting stable state to true")
            stableTrackingState = true
            wasTrackingBefore = true
            lastTrackingTime = System.currentTimeMillis()
        } else if (wasTrackingBefore) {
            Log.d("ARScan", "Tracking lost - starting 5 second timer")
            delay(5000)
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastTrackingTime
            Log.d("ARScan", "5 seconds elapsed - time difference: ${timeDiff}ms")
            if (timeDiff >= 5000) {
                Log.d("ARScan", "Setting tracking lost state")
                stableTrackingState = false
            } else {
                Log.d("ARScan", "Time check failed - not setting tracking lost")
            }
        } else {
            Log.d("ARScan", "Tracking false but never tracked before - ignoring")
        }
    }

    LaunchedEffect(isInitialized, landmarkCount, bestConfidence, stableTrackingState) {
        val previousStatus = currentStatus

        currentStatus = when {
            !isInitialized -> {
                Log.d("ARScan", "Status: INITIALIZING (AR not initialized)")
                ARScanStatus.INITIALIZING
            }
            !stableTrackingState && wasTrackingBefore -> {
                Log.d("ARScan", "Status: TRACKING_LOST (lost tracking after having it)")
                ARScanStatus.TRACKING_LOST
            }
            landmarkCount == 0 -> {
                Log.d("ARScan", "Status: SCANNING (no landmarks found)")
                ARScanStatus.SCANNING
            }
            bestConfidence < 0.4f -> {
                Log.d("ARScan", "Status: LOW_CONFIDENCE (confidence: $bestConfidence < 0.4)")
                ARScanStatus.LOW_CONFIDENCE
            }
            bestConfidence < 0.7f -> {
                Log.d("ARScan", "Status: MOVE_CAMERA (confidence: $bestConfidence < 0.7)")
                ARScanStatus.MOVE_CAMERA
            }
            else -> {
                Log.d("ARScan", "Status: LANDMARK_FOUND (confidence: $bestConfidence >= 0.7)")
                ARScanStatus.LANDMARK_FOUND
            }
        }

        if (previousStatus != currentStatus) {
            Log.d("ARScan", "*** STATUS CHANGED: $previousStatus -> $currentStatus ***")
        }
    }

    return currentStatus
}