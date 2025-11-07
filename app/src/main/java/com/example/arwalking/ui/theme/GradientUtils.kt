package com.example.arwalking.ui.theme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
}