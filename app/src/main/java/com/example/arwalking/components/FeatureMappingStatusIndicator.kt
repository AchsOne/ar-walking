package com.example.arwalking.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class FeatureMappingStatus {
    DISABLED,
    INITIALIZING,
    ACTIVE,
    PROCESSING,
    ERROR
}

@Composable
fun FeatureMappingStatusIndicator(
    status: FeatureMappingStatus,
    matchCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val (backgroundColorValue, indicatorColor, statusText) = when (status) {
        FeatureMappingStatus.DISABLED -> Triple(
            Color.Gray.copy(alpha = 0.8f),
            Color.Gray,
            "Feature Mapping Disabled"
        )
        FeatureMappingStatus.INITIALIZING -> Triple(
            Color.Blue.copy(alpha = 0.8f),
            Color.Blue,
            "Initializing..."
        )
        FeatureMappingStatus.ACTIVE -> Triple(
            Color.Green.copy(alpha = 0.8f),
            Color.Green,
            "Active ($matchCount matches)"
        )
        FeatureMappingStatus.PROCESSING -> Triple(
            Color(0xFFFF9800).copy(alpha = 0.8f),
            Color(0xFFFF9800),
            "Processing..."
        )
        FeatureMappingStatus.ERROR -> Triple(
            Color.Red.copy(alpha = 0.8f),
            Color.Red,
            "Error"
        )
    }
    
    // Pulsing animation for active states
    val infiniteTransition = rememberInfiniteTransition(label = "status_animation")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    
    Row(
        modifier = modifier
            .background(
                color = backgroundColorValue,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    color = if (status == FeatureMappingStatus.ACTIVE || status == FeatureMappingStatus.PROCESSING) {
                        indicatorColor.copy(alpha = pulseAlpha)
                    } else {
                        indicatorColor
                    }
                )
        )
        
        // Status text
        Text(
            text = statusText,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}