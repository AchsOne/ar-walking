package com.example.arwalking.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Safe border modifier that ensures brushes have at least 2 colors
 */
fun Modifier.safeBorder(
    width: Dp,
    brush: Brush,
    shape: Shape = RectangleShape
): Modifier {
    return try {
        this.border(width, brush, shape)
    } catch (e: IllegalArgumentException) {
        // Fallback to solid color border if brush fails
        this.border(width, Color.Gray, shape)
    }
}

/**
 * Safe border modifier with color
 */
fun Modifier.safeBorder(
    width: Dp,
    color: Color,
    shape: Shape = RectangleShape
): Modifier {
    return this.border(width, color, shape)
}

/**
 * Safe border modifier with BorderStroke
 */
fun Modifier.safeBorder(
    border: BorderStroke,
    shape: Shape = RectangleShape
): Modifier {
    return try {
        this.border(border, shape)
    } catch (e: IllegalArgumentException) {
        // Fallback to solid color border if brush fails
        this.border(border.width, Color.Gray, shape)
    }
}