package com.example.arwalking.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug-Overlay zur Anzeige der geladenen Landmarks
 * Zeigt welche Landmarks für die aktuelle Route geladen wurden
 */
@Composable
fun LandmarkDebugOverlay(
    requiredLandmarkIds: List<String>,
    loadedLandmarkIds: List<String>,
    isVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🔍 Landmark Debug Info",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Route-spezifische Landmarks
            Text(
                text = "📍 Benötigte Landmarks (${requiredLandmarkIds.size}):",
                color = Color.Cyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (requiredLandmarkIds.isEmpty()) {
                Text(
                    text = "   Keine Route geladen",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 100.dp)
                ) {
                    items(requiredLandmarkIds) { landmarkId ->
                        val isLoaded = loadedLandmarkIds.contains(landmarkId)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isLoaded) "✅" else "❌",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = landmarkId,
                                color = if (isLoaded) Color.Green else Color.Red,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Geladene Landmarks
            Text(
                text = "💾 Geladene Landmarks (${loadedLandmarkIds.size}):",
                color = Color.Yellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (loadedLandmarkIds.isEmpty()) {
                Text(
                    text = "   Keine Landmarks geladen",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 80.dp)
                ) {
                    items(loadedLandmarkIds) { landmarkId ->
                        Text(
                            text = "   • $landmarkId",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Status-Zusammenfassung
            val matchingCount = requiredLandmarkIds.intersect(loadedLandmarkIds.toSet()).size
            val statusColor = when {
                requiredLandmarkIds.isEmpty() -> Color.Gray
                matchingCount == requiredLandmarkIds.size -> Color.Green
                matchingCount > 0 -> Color.Yellow
                else -> Color.Red
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Status:",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        requiredLandmarkIds.isEmpty() -> "Keine Route"
                        matchingCount == requiredLandmarkIds.size -> "Alle geladen"
                        matchingCount > 0 -> "$matchingCount/${requiredLandmarkIds.size} geladen"
                        else -> "Keine passenden"
                    },
                    color = statusColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Kompakte Version des Debug-Overlays
 */
@Composable
fun CompactLandmarkDebugInfo(
    requiredCount: Int,
    loadedCount: Int,
    matchingCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔍",
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$matchingCount/$requiredCount",
                color = when {
                    requiredCount == 0 -> Color.Gray
                    matchingCount == requiredCount -> Color.Green
                    matchingCount > 0 -> Color.Yellow
                    else -> Color.Red
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}