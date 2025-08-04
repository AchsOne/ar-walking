package com.example.arwalking.components

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

/**
 * Status-Enum für AR-Scanning
 */
enum class ARScanStatus {
    INITIALIZING,   // System wird initialisiert
    SCANNING,       // Sucht nach Landmarks
    TRACKING,       // Landmark wird getrackt
    LOST,          // Tracking verloren
    NAVIGATING     // Navigation aktiv
}

/**
 * Helper functions für ARScanStatus enum
 */
private fun ARScanStatus.shouldPulse(): Boolean = when (this) {
    ARScanStatus.SCANNING, ARScanStatus.INITIALIZING -> true
    else -> false
}

private fun ARScanStatus.getIcon(): ImageVector = when (this) {
    ARScanStatus.INITIALIZING -> Icons.Default.CameraAlt
    ARScanStatus.SCANNING -> Icons.Default.Search
    ARScanStatus.TRACKING -> Icons.Default.CheckCircle
    ARScanStatus.LOST -> Icons.Default.Warning
    ARScanStatus.NAVIGATING -> Icons.Default.CheckCircle
}

private fun ARScanStatus.getColor(): Color = when (this) {
    ARScanStatus.INITIALIZING -> Color.Blue
    ARScanStatus.SCANNING -> Color.Yellow
    ARScanStatus.TRACKING -> Color.Green
    ARScanStatus.LOST -> Color.Red
    ARScanStatus.NAVIGATING -> Color.Green
}

private fun ARScanStatus.getMessage(): String = when (this) {
    ARScanStatus.INITIALIZING -> "AR wird initialisiert..."
    ARScanStatus.SCANNING -> "Suche nach Landmarks..."
    ARScanStatus.TRACKING -> "Landmark erkannt"
    ARScanStatus.LOST -> "Landmark verloren"
    ARScanStatus.NAVIGATING -> "Navigation aktiv"
}

private fun ARScanStatus.showProgress(): Boolean = when (this) {
    ARScanStatus.INITIALIZING -> true
    else -> false
}

/**
 * AR Info Island - Semitransparente UI-Komponente im Dynamic Island Style
 * Zeigt Scan-Status und subtile Benutzerführung während der AR-Navigation
 */
@Composable
fun ARInfoIsland(
    scanStatus: ARScanStatus,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    if (!isVisible) return
    
    // Animation für das Ein-/Ausblenden
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 0.85f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "island_alpha"
    )
    
    // Pulsierender Effekt für bestimmte Status
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
                .blur(radius = 0.5.dp), // Subtiler Blur-Effekt
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
                // Status Icon mit Animation
                val iconAlpha = if (scanStatus.shouldPulse()) pulseAlpha else 1f
                Icon(
                    imageVector = scanStatus.getIcon(),
                    contentDescription = null,
                    tint = scanStatus.getColor().copy(alpha = iconAlpha),
                    modifier = Modifier.size(20.dp)
                )
                
                // Status Text
                Text(
                    text = scanStatus.getMessage(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                // Optionaler Fortschrittsindikator
                if (scanStatus.showProgress()) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = scanStatus.getColor(),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}



/**
 * Bestimmt die Farbe basierend auf der Confidence
 */
private fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.8f -> Color.Green
        confidence >= 0.6f -> Color.Yellow
        confidence >= 0.4f -> Color(0xFFFF9800)
        else -> Color.Red
    }
}

/**
 * Hook für automatische Status-Updates basierend auf AR-Zustand
 */
@Composable
fun rememberARScanStatus(
    isInitialized: Boolean,
    landmarkCount: Int,
    bestConfidence: Float,
    isTracking: Boolean
): ARScanStatus {
    var currentStatus by remember { mutableStateOf(ARScanStatus.INITIALIZING) }
    var stableTrackingState by remember { mutableStateOf(true) }
    var lastTrackingTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Stabilisiere den Tracking-Status um falsches "Tracking verloren" zu vermeiden
    LaunchedEffect(isTracking) {
        if (isTracking) {
            stableTrackingState = true
            lastTrackingTime = System.currentTimeMillis()
        } else {
            // Warte 5 Sekunden ohne Tracking bevor "Tracking verloren" angezeigt wird
            delay(5000)
            if (System.currentTimeMillis() - lastTrackingTime >= 5000) {
                stableTrackingState = false
            }
        }
    }
    
    LaunchedEffect(isInitialized, landmarkCount, bestConfidence, stableTrackingState) {
        currentStatus = when {
            !isInitialized -> ARScanStatus.INITIALIZING
            !stableTrackingState -> ARScanStatus.LOST
            landmarkCount == 0 -> ARScanStatus.SCANNING
            bestConfidence >= 0.7f && isTracking -> ARScanStatus.NAVIGATING
            bestConfidence >= 0.5f && isTracking -> ARScanStatus.TRACKING
            else -> ARScanStatus.SCANNING
        }
    }
    
    return currentStatus
}