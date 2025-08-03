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
 * AR Info Island - Semitransparente UI-Komponente im Apple Dynamic Island Style
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
                val iconAlpha = if (scanStatus.shouldPulse) pulseAlpha else 1f
                Icon(
                    imageVector = scanStatus.icon,
                    contentDescription = null,
                    tint = scanStatus.color.copy(alpha = iconAlpha),
                    modifier = Modifier.size(20.dp)
                )
                
                // Status Text
                Text(
                    text = scanStatus.message,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                
                // Optionaler Fortschrittsindikator
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
 * Erweiterte AR Info Island mit mehr Informationen
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
                // Hauptstatus
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
                
                // Zusätzliche Informationen
                if (landmarkCount > 0 || confidence > 0f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (landmarkCount > 0) {
                            InfoChip(
                                label = "Landmarks",
                                value = landmarkCount.toString(),
                                color = Color.Blue
                            )
                        }
                        
                        if (confidence > 0f) {
                            InfoChip(
                                label = "Genauigkeit",
                                value = "${(confidence * 100).toInt()}%",
                                color = getConfidenceColor(confidence)
                            )
                        }
                    }
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
 * AR Scan Status Datenklasse
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
            !stableTrackingState -> ARScanStatus.TRACKING_LOST
            landmarkCount == 0 -> ARScanStatus.SCANNING
            bestConfidence < 0.4f -> ARScanStatus.LOW_CONFIDENCE
            bestConfidence < 0.7f -> ARScanStatus.MOVE_CAMERA
            else -> ARScanStatus.LANDMARK_FOUND
        }
    }
    
    return currentStatus
}