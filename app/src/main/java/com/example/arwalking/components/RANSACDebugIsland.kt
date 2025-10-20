package com.example.arwalking.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arwalking.RouteViewModel

@Composable
fun RANSACDebugIsland(
    ransacStats: RouteViewModel.RANSACStats?,
    isFeatureMappingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isFeatureMappingEnabled && ransacStats != null,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        ransacStats?.let { stats ->
            Card(
                modifier = Modifier
                    .width(340.dp)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔍 RANSAC Debug",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (stats.usedRANSAC) Color(0xFF4CAF50)
                                    else Color(0xFFFF9800)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (stats.usedRANSAC) "RANSAC" else "BASIC",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Landmark Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Landmark:",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Text(
                            text = stats.landmarkId,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Confidence mit Progress Bar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Confidence:",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${(stats.confidence * 100).toInt()}%",
                                color = getConfidenceColor(stats.confidence),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Animated Progress Bar
                        val animatedProgress by animateFloatAsState(
                            targetValue = stats.confidence,
                            animationSpec = tween(500),
                            label = "confidence_progress"
                        )

                        LinearProgressIndicator(
                            progress = animatedProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = getConfidenceColor(stats.confidence),
                            trackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    }

                    if (stats.usedRANSAC) {
                        // RANSAC-spezifische Infos
                        Spacer(modifier = Modifier.height(4.dp))

                        // Matches Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Matches",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${stats.totalMatches}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Inliers",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${stats.inliers}",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Outliers",
                                    color = Color(0xFFF44336),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${stats.outliers}",
                                    color = Color(0xFFF44336),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Inlier Ratio
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Inlier Ratio:",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${(stats.inlierRatio * 100).toInt()}%",
                                    color = getInlierRatioColor(stats.inlierRatio),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            val animatedInlierProgress by animateFloatAsState(
                                targetValue = stats.inlierRatio,
                                animationSpec = tween(500),
                                label = "inlier_progress"
                            )

                            LinearProgressIndicator(
                                progress = animatedInlierProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = getInlierRatioColor(stats.inlierRatio),
                                trackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        }

                        // Reprojection Error
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Reproj. Error:",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (stats.reprojectionError != Double.MAX_VALUE) {
                                    "${"%.2f".format(stats.reprojectionError)} px"
                                } else {
                                    "N/A"
                                },
                                color = getReprojErrorColor(stats.reprojectionError),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        // Nicht-RANSAC Info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF9800).copy(alpha = 0.2f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "⚠️ Too few matches for RANSAC (<4)",
                                color = Color(0xFFFF9800),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Quality Indicator
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(getQualityBackgroundColor(stats))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Match Quality:",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = getQualityLabel(stats),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Helper Functions für Farb-Coding
private fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.75f -> Color(0xFF4CAF50)  // Grün
        confidence >= 0.60f -> Color(0xFF8BC34A)  // Hellgrün
        confidence >= 0.45f -> Color(0xFFFF9800)  // Orange
        else -> Color(0xFFF44336)                 // Rot
    }
}

private fun getInlierRatioColor(ratio: Float): Color {
    return when {
        ratio >= 0.80f -> Color(0xFF4CAF50)
        ratio >= 0.60f -> Color(0xFF8BC34A)
        ratio >= 0.40f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

private fun getReprojErrorColor(error: Double): Color {
    return when {
        error == Double.MAX_VALUE -> Color.Gray
        error < 2.0 -> Color(0xFF4CAF50)
        error < 5.0 -> Color(0xFF8BC34A)
        error < 10.0 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}

private fun getQualityBackgroundColor(stats: RouteViewModel.RANSACStats): Color {
    if (!stats.usedRANSAC) return Color(0xFFFF9800).copy(alpha = 0.6f)

    val score = when {
        stats.inlierRatio >= 0.75f && stats.confidence >= 0.70f -> 3  // Excellent
        stats.inlierRatio >= 0.60f && stats.confidence >= 0.55f -> 2  // Good
        stats.inlierRatio >= 0.40f && stats.confidence >= 0.40f -> 1  // Fair
        else -> 0  // Poor
    }

    return when (score) {
        3 -> Color(0xFF4CAF50).copy(alpha = 0.6f)
        2 -> Color(0xFF8BC34A).copy(alpha = 0.6f)
        1 -> Color(0xFFFF9800).copy(alpha = 0.6f)
        else -> Color(0xFFF44336).copy(alpha = 0.6f)
    }
}

private fun getQualityLabel(stats: RouteViewModel.RANSACStats): String {
    if (!stats.usedRANSAC) return "FALLBACK"

    return when {
        stats.inlierRatio >= 0.75f && stats.confidence >= 0.70f -> "✓ EXCELLENT"
        stats.inlierRatio >= 0.60f && stats.confidence >= 0.55f -> "✓ GOOD"
        stats.inlierRatio >= 0.40f && stats.confidence >= 0.40f -> "⚠ FAIR"
        else -> "✗ POOR"
    }
}