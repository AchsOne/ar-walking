

package com.example.arwalking.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arwalking.FeatureMatchResult
import com.example.arwalking.R
import kotlinx.coroutines.delay

/**
 * Overlay-Komponente für die Anzeige von Feature-Match-Ergebnissen
 */
@Composable
fun FeatureMatchOverlay(
    matches: List<FeatureMatchResult>,
    isFeatureMappingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    // Nur anzeigen wenn Feature Mapping aktiviert ist und Matches vorhanden sind
    AnimatedVisibility(
        visible = isFeatureMappingEnabled && matches.isNotEmpty(),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.location_searching),
                        contentDescription = "Feature Detection",
                        tint = Color(0xFF94AD0C),
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Text(
                        text = "Erkannte Landmarks",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = "${matches.size}",
                        color = Color(0xFF94AD0C),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Match Results
            matches.take(3).forEach { match ->
                FeatureMatchCard(match = match)
            }
        }
    }
}

@Composable
private fun FeatureMatchCard(
    match: FeatureMatchResult,
    modifier: Modifier = Modifier
) {
    val confidenceColor = getConfidenceColor(match.confidence)
    val confidenceText = "${(match.confidence * 100).toInt()}%"
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Confidence Indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(confidenceColor.copy(alpha = 0.2f))
                    .border(
                        width = 2.dp,
                        color = confidenceColor,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = confidenceText,
                    color = confidenceColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            
            // Landmark Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = match.landmark.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                
                if (match.landmark.description.isNotEmpty()) {
                    Text(
                        text = match.landmark.description,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                
                // Match Details
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${match.matchCount} Features",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                    
                    match.distance?.let { distance ->
                        Text(
                            text = "~${distance.toInt()}m",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            // Status Icon
            Icon(
                painter = painterResource(
                    id = when {
                        match.confidence >= 0.8f -> R.drawable.star_filled
                        match.confidence >= 0.6f -> R.drawable.star_outline
                        else -> R.drawable.alert_circle
                    }
                ),
                contentDescription = "Match Quality",
                tint = confidenceColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Gibt die Farbe basierend auf der Confidence zurück
 */
private fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.8f -> Color(0xFF4CAF50) // Grün - Sehr gut
        confidence >= 0.6f -> Color(0xFFFF9800) // Orange - Gut
        confidence >= 0.4f -> Color(0xFFFFEB3B) // Gelb - Okay
        else -> Color(0xFFF44336) // Rot - Schlecht
    }
}

/**
 * Debug-Overlay für Feature-Matching (nur in Debug-Builds)
 */
@Composable
fun FeatureMatchDebugOverlay(
    matches: List<FeatureMatchResult>,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible && matches.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Feature Matching Debug",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                matches.forEach { match ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = match.landmark.id,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = "${match.matchCount}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                        
                        Text(
                            text = String.format("%.2f", match.confidence),
                            color = getConfidenceColor(match.confidence),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Erweiterte Status-Anzeige für Feature Mapping mit sofortiger Aktivierung
 */
@Composable
fun FeatureMappingStatusIndicator(
    isEnabled: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    var showStartupAnimation by remember { mutableStateOf(true) }
    var stableProcessingState by remember { mutableStateOf(false) }
    
    // Startup-Animation für 3 Sekunden anzeigen
    LaunchedEffect(Unit) {
        delay(3000)
        showStartupAnimation = false
    }
    
    // Stabilisiere den Processing-Status um Flackern zu vermeiden
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            stableProcessingState = true
            delay(1000) // Mindestens 1 Sekunde anzeigen
        } else {
            delay(500) // Kurze Verzögerung bevor auf "Ready" gewechselt wird
            stableProcessingState = false
        }
    }
    
    AnimatedVisibility(
        visible = isEnabled, // Nur anzeigen wenn Feature Mapping aktiviert ist
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    showStartupAnimation -> Color(0xFF2196F3).copy(alpha = 0.8f) // Blau für Startup
                    stableProcessingState -> Color.Black.copy(alpha = 0.7f) // Schwarz für Processing
                    isEnabled -> Color(0xFF4CAF50).copy(alpha = 0.7f) // Grün für Ready
                    else -> Color(0xFFFF5722).copy(alpha = 0.7f) // Rot für Disabled
                }
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Status Icon mit Animation
                when {
                    showStartupAnimation -> {
                        val rotation by rememberInfiniteTransition(label = "startup_rotation").animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "startup_animation"
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.location_searching),
                            contentDescription = "Feature Mapping Starting",
                            tint = Color.White,
                            modifier = Modifier
                                .size(12.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    }
                    stableProcessingState -> {
                        val rotation by rememberInfiniteTransition(label = "processing_rotation").animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "processing_animation"
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.location_searching),
                            contentDescription = "Feature Mapping Processing",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier
                                .size(12.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    }
                    isEnabled -> {
                        Icon(
                            painter = painterResource(id = R.drawable.star_filled),
                            contentDescription = "Feature Mapping Ready",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    else -> {
                        Icon(
                            painter = painterResource(id = R.drawable.star_filled),
                            contentDescription = "Feature Mapping Disabled",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                
                Text(
                    text = when {
                        showStartupAnimation -> "Aktiviere..."
                        stableProcessingState -> "Scanning..."
                        isEnabled -> "Aktiv"
                        else -> "Inaktiv"
                    },
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}