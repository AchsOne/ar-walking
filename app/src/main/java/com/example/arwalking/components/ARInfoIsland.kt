package com.example.arwalking.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.example.arwalking.R
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
 * Enum für verschiedene Navigationsaktionen
 */
enum class NavigationAction {
    STRAIGHT,       // Geradeaus gehen
    TURN_LEFT,      // Links abbiegen
    TURN_RIGHT,     // Rechts abbiegen
    THROUGH_DOOR,   // Durch Tür gehen
    UNKNOWN         // Unbekannte Aktion
}

/**
 * Helper functions für ARScanStatus enum
 */
private fun ARScanStatus.shouldPulse(): Boolean = when (this) {
    ARScanStatus.SCANNING, ARScanStatus.INITIALIZING -> true
    else -> false
}

private fun ARScanStatus.getIconResource(): Int = when (this) {
    ARScanStatus.INITIALIZING -> R.drawable.location_searching
    ARScanStatus.SCANNING -> R.drawable.location_searching
    ARScanStatus.TRACKING -> R.drawable.navigation21
    ARScanStatus.LOST -> R.drawable.alert_circle
    ARScanStatus.NAVIGATING -> R.drawable.navigation21
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

/**
 * AR Info Island - Semitransparente UI-Komponente im Dynamic Island Style
 * Zeigt Scan-Status und subtile Benutzerführung während der AR-Navigation
 */
@Composable
fun ARInfoIsland(
    scanStatus: ARScanStatus,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    currentInstruction: String? = null
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
                // Status Icon mit Animation - verwende Navigationsicon wenn verfügbar
                val iconAlpha = if (scanStatus.shouldPulse()) pulseAlpha else 1f
                val iconResource = if (scanStatus == ARScanStatus.NAVIGATING && currentInstruction != null) {
                    getNavigationAction(currentInstruction).getIconResource()
                } else {
                    scanStatus.getIconResource()
                }
                
                Icon(
                    painter = painterResource(id = iconResource),
                    contentDescription = null,
                    tint = scanStatus.getColor().copy(alpha = iconAlpha),
                    modifier = Modifier.size(20.dp)
                )
                
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
                    scanStatus.getMessage()
                }
                
                Text(
                    text = displayText,
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
        android.util.Log.d("ARInfoIsland", "=== AR STATUS DEBUG ===")
        android.util.Log.d("ARInfoIsland", "isTracking changed to: $isTracking")
        android.util.Log.d("ARInfoIsland", "landmarkCount: $landmarkCount")
        android.util.Log.d("ARInfoIsland", "bestConfidence: $bestConfidence")
        android.util.Log.d("ARInfoIsland", "isInitialized: $isInitialized")
        
        if (isTracking) {
            android.util.Log.d("ARInfoIsland", "Tracking active - setting stable state to true")
            stableTrackingState = true
            lastTrackingTime = System.currentTimeMillis()
        } else {
            android.util.Log.w("ARInfoIsland", "Tracking lost - waiting 5 seconds before showing 'Landmark verloren'")
            // Warte 5 Sekunden ohne Tracking bevor "Tracking verloren" angezeigt wird
            delay(5000)
            if (System.currentTimeMillis() - lastTrackingTime >= 5000) {
                android.util.Log.w("ARInfoIsland", "5 seconds passed without tracking - setting stable state to false")
                stableTrackingState = false
            } else {
                android.util.Log.d("ARInfoIsland", "Tracking recovered within 5 seconds - keeping stable state")
            }
        }
        android.util.Log.d("ARInfoIsland", "=======================")
    }
    
    LaunchedEffect(isInitialized, landmarkCount, bestConfidence, stableTrackingState) {
        val newStatus = when {
            !isInitialized -> ARScanStatus.INITIALIZING
            !stableTrackingState -> ARScanStatus.LOST
            landmarkCount == 0 -> ARScanStatus.SCANNING
            bestConfidence >= 0.7f && isTracking -> ARScanStatus.NAVIGATING
            bestConfidence >= 0.5f && isTracking -> ARScanStatus.TRACKING
            else -> ARScanStatus.SCANNING
        }
        
        if (newStatus != currentStatus) {
            android.util.Log.i("ARInfoIsland", "=== STATUS CHANGE ===")
            android.util.Log.i("ARInfoIsland", "Old status: ${currentStatus.getMessage()}")
            android.util.Log.i("ARInfoIsland", "New status: ${newStatus.getMessage()}")
            android.util.Log.i("ARInfoIsland", "Conditions:")
            android.util.Log.i("ARInfoIsland", "- isInitialized: $isInitialized")
            android.util.Log.i("ARInfoIsland", "- stableTrackingState: $stableTrackingState")
            android.util.Log.i("ARInfoIsland", "- landmarkCount: $landmarkCount")
            android.util.Log.i("ARInfoIsland", "- bestConfidence: $bestConfidence")
            android.util.Log.i("ARInfoIsland", "- isTracking: $isTracking")
            android.util.Log.i("ARInfoIsland", "====================")
        }
        
        currentStatus = newStatus
    }
    
    return currentStatus
}