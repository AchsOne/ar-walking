package com.example.arwalking.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * Utility functions for creating safe gradients that always have at least 2 colors
 */
object GradientUtils {
    
    /**
     * Creates a safe vertical gradient that ensures at least 2 colors are present
     */
    fun safeVerticalGradient(
        colors: List<Color>,
        startY: Float = 0.0f,
        endY: Float = Float.POSITIVE_INFINITY
    ): Brush {
        val safeColors = when {
            colors.isEmpty() -> listOf(Color.Transparent, Color.Transparent)
            colors.size == 1 -> listOf(colors[0], colors[0])
            else -> colors
        }
        
        return Brush.verticalGradient(
            colors = safeColors,
            startY = startY,
            endY = endY
        )
    }
    
    /**
     * Creates a safe horizontal gradient that ensures at least 2 colors are present
     */
    fun safeHorizontalGradient(
        colors: List<Color>,
        startX: Float = 0.0f,
        endX: Float = Float.POSITIVE_INFINITY
    ): Brush {
        val safeColors = when {
            colors.isEmpty() -> listOf(Color.Transparent, Color.Transparent)
            colors.size == 1 -> listOf(colors[0], colors[0])
            else -> colors
        }
        
        return Brush.horizontalGradient(
            colors = safeColors,
            startX = startX,
            endX = endX
        )
    }
    
    /**
     * Creates a safe linear gradient that ensures at least 2 colors are present
     */
    fun safeLinearGradient(
        colors: List<Color>,
        start: Offset = Offset.Zero,
        end: Offset = Offset.Infinite,
        tileMode: TileMode = TileMode.Clamp
    ): Brush {
        val safeColors = when {
            colors.isEmpty() -> listOf(Color.Transparent, Color.Transparent)
            colors.size == 1 -> listOf(colors[0], colors[0])
            else -> colors
        }
        
        return Brush.linearGradient(
            colors = safeColors,
            start = start,
            end = end,
            tileMode = tileMode
        )
    }
    
    /**
     * Creates a safe radial gradient that ensures at least 2 colors are present
     */
    fun safeRadialGradient(
        colors: List<Color>,
        center: Offset = Offset.Unspecified,
        radius: Float = Float.POSITIVE_INFINITY,
        tileMode: TileMode = TileMode.Clamp
    ): Brush {
        val safeColors = when {
            colors.isEmpty() -> listOf(Color.Transparent, Color.Transparent)
            colors.size == 1 -> listOf(colors[0], colors[0])
            else -> colors
        }
        
        return Brush.radialGradient(
            colors = safeColors,
            center = center,
            radius = radius,
            tileMode = tileMode
        )
    }
}