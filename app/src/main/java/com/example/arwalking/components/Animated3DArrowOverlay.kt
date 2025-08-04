package com.example.arwalking.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun Animated3DArrowOverlay(
    direction: Float = 0f, // Direction in degrees
    distance: Float = 100f, // Distance to target
    modifier: Modifier = Modifier
) {
    // Animation for pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "arrow_animation")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    // Animation for rotation
    val rotationAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val center = Offset(centerX, centerY)
        
        // Draw 3D-style arrow
        draw3DArrow(
            center = center,
            direction = direction,
            scale = pulseScale,
            rotation = rotationAnimation,
            distance = distance
        )
    }
}

private fun DrawScope.draw3DArrow(
    center: Offset,
    direction: Float,
    scale: Float,
    rotation: Float,
    distance: Float
) {
    val arrowSize = 60.dp.toPx() * scale
    val arrowColor = Color(0xFF4CAF50)
    val shadowColor = Color.Black.copy(alpha = 0.3f)
    
    rotate(degrees = direction + rotation, pivot = center) {
        // Draw shadow (offset slightly)
        val shadowOffset = Offset(center.x + 4, center.y + 4)
        drawArrowShape(shadowOffset, arrowSize, shadowColor)
        
        // Draw main arrow
        drawArrowShape(center, arrowSize, arrowColor)
        
        // Draw highlight for 3D effect
        val highlightColor = arrowColor.copy(alpha = 0.7f)
        drawArrowShape(
            center = Offset(center.x - 2, center.y - 2),
            size = arrowSize * 0.8f,
            color = highlightColor
        )
    }
    
    // Draw distance indicator
    if (distance > 0) {
        val distanceText = "${distance.toInt()}m"
        // Note: In a real implementation, you'd use drawText with proper text measurement
        // For now, we'll draw a simple indicator circle
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = 20.dp.toPx(),
            center = Offset(center.x, center.y + arrowSize + 40.dp.toPx())
        )
    }
}

private fun DrawScope.drawArrowShape(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = Path().apply {
        // Arrow head
        moveTo(center.x, center.y - size / 2)
        lineTo(center.x - size / 4, center.y - size / 4)
        lineTo(center.x - size / 8, center.y - size / 4)
        
        // Arrow body
        lineTo(center.x - size / 8, center.y + size / 2)
        lineTo(center.x + size / 8, center.y + size / 2)
        lineTo(center.x + size / 8, center.y - size / 4)
        lineTo(center.x + size / 4, center.y - size / 4)
        
        close()
    }
    
    drawPath(
        path = path,
        color = color
    )
}