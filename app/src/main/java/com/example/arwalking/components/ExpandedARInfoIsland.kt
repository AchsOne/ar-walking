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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.arwalking.R

/**
 * Erweiterte AR Info Island mit detaillierten Informationen
 * Zeigt Landmark-Count, Confidence und erweiterte Status-Informationen
 */
@Composable
fun ExpandedARInfoIsland(
    scanStatus: ARScanStatus,
    landmarkCount: Int = 0,
    confidence: Float = 0f,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier,
    currentInstruction: String? = null
) {
    if (!isVisible) return
    
    // Animation für das Ein-/Ausblenden
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 0.9f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "expanded_island_alpha"
    )
    
    // Pulsierender Effekt für bestimmte Status
    val pulseAnimation = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnimation.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    // Bestimme Farben basierend auf Status
    val (backgroundColor, contentColor, accentColor) = when (scanStatus) {
        ARScanStatus.INITIALIZING -> Triple(
            Color.Black.copy(alpha = 0.8f),
            Color.White,
            Color.Yellow
        )
        ARScanStatus.SCANNING -> Triple(
            Color.Black.copy(alpha = 0.8f),
            Color.White,
            Color.Cyan
        )
        ARScanStatus.TRACKING -> Triple(
            Color.Black.copy(alpha = 0.8f),
            Color.White,
            Color.Green
        )
        ARScanStatus.LOST -> Triple(
            Color.Black.copy(alpha = 0.8f),
            Color.White,
            Color.Red
        )
        ARScanStatus.NAVIGATING -> Triple(
            Color.Black.copy(alpha = 0.8f),
            Color.White,
            Color.Blue
        )
    }
    
    Card(
        modifier = modifier
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = alpha)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status-Icon mit Animation
            val iconAlpha = if (scanStatus == ARScanStatus.SCANNING || scanStatus == ARScanStatus.INITIALIZING) {
                pulseAlpha
            } else {
                1f
            }
            
            // Verwende Navigationsicon wenn verfügbar, sonst Standard-Icon
            if (scanStatus == ARScanStatus.NAVIGATING && currentInstruction != null) {
                val navigationAction = getNavigationAction(currentInstruction)
                Icon(
                    painter = painterResource(id = navigationAction.getIconResource()),
                    contentDescription = null,
                    tint = accentColor.copy(alpha = iconAlpha),
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = getStatusIcon(scanStatus),
                    contentDescription = null,
                    tint = accentColor.copy(alpha = iconAlpha),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Status-Text und Details
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Status Text - zeige Navigationsanweisung ohne Distanz wenn verfügbar
                val displayText = if (scanStatus == ARScanStatus.NAVIGATING && currentInstruction != null) {
                    // Entferne Distanzangaben aus der Anweisung
                    currentInstruction
                        .replace(Regex("\\d+\\s*m"), "") // Entferne "123m" oder "123 m"
                        .replace(Regex("\\d+\\s*meter"), "") // Entferne "123 meter"
                        .replace(Regex("\\d+\\s*Meter"), "") // Entferne "123 Meter"
                        .replace(Regex("\\s+"), " ") // Mehrfache Leerzeichen durch einfache ersetzen
                        .trim()
                } else {
                    getStatusText(scanStatus)
                }
                
                Text(
                    text = displayText,
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // Erweiterte Informationen
                if (landmarkCount > 0 || confidence > 0f) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (landmarkCount > 0) {
                            Text(
                                text = "$landmarkCount Landmarks",
                                color = contentColor.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        
                        if (confidence > 0f) {
                            Text(
                                text = "•",
                                color = contentColor.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                            
                            Text(
                                text = "${(confidence * 100).toInt()}%",
                                color = getConfidenceColor(confidence),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Zusätzlicher Status-Indikator
            if (scanStatus == ARScanStatus.TRACKING || scanStatus == ARScanStatus.NAVIGATING) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            accentColor.copy(alpha = pulseAlpha),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

/**
 * Bestimmt das Icon basierend auf dem Scan-Status
 */
private fun getStatusIcon(status: ARScanStatus): ImageVector {
    return when (status) {
        ARScanStatus.INITIALIZING -> Icons.Default.CameraAlt
        ARScanStatus.SCANNING -> Icons.Default.Search
        ARScanStatus.TRACKING -> Icons.Default.CheckCircle
        ARScanStatus.LOST -> Icons.Default.Warning
        ARScanStatus.NAVIGATING -> Icons.Default.LocationOn
    }
}

/**
 * Bestimmt den Status-Text
 */
private fun getStatusText(status: ARScanStatus): String {
    return when (status) {
        ARScanStatus.INITIALIZING -> "AR wird initialisiert..."
        ARScanStatus.SCANNING -> "Suche nach Landmarks..."
        ARScanStatus.TRACKING -> "Landmark erkannt"
        ARScanStatus.LOST -> "Landmark verloren"
        ARScanStatus.NAVIGATING -> "Navigation aktiv"
    }
}

/**
 * Bestimmt die Farbe basierend auf der Confidence
 */
private fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.9f -> Color.Green
        confidence >= 0.8f -> Color(0xFF90EE90) // Light Green
        confidence >= 0.7f -> Color.Yellow
        confidence >= 0.6f -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }
}

/**
 * Bestimmt die Navigationsaktion basierend auf der Anweisung
 */
private fun getNavigationAction(instruction: String): NavigationAction {
    val lowerInstruction = instruction.lowercase()
    return when {
        lowerInstruction.contains("rechts") || lowerInstruction.contains("right") -> NavigationAction.TURN_RIGHT
        lowerInstruction.contains("links") || lowerInstruction.contains("left") -> NavigationAction.TURN_LEFT
        lowerInstruction.contains("tür") || lowerInstruction.contains("door") || 
        lowerInstruction.contains("eingang") || lowerInstruction.contains("entrance") ||
        lowerInstruction.contains("durch") || lowerInstruction.contains("through") -> NavigationAction.THROUGH_DOOR
        else -> NavigationAction.STRAIGHT
    }
}

/**
 * Gibt das passende Icon für die Navigationsaktion zurück
 */
private fun NavigationAction.getIconResource(): Int = when (this) {
    NavigationAction.TURN_RIGHT -> R.drawable.corner_up_right_1
    NavigationAction.TURN_LEFT -> R.drawable.left
    NavigationAction.THROUGH_DOOR -> R.drawable.door
    NavigationAction.STRAIGHT -> R.drawable.arrow_up_1
    NavigationAction.UNKNOWN -> R.drawable.navigation21
}