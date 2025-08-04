package com.example.arwalking.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.example.arwalking.FeatureMatchResult
import kotlin.math.*

/**
 * Snapchat-Style 3D-Pfeil für AR-Navigation
 * Zeigt einen 3D-Pfeil an, der auf erkannte Landmarks zeigt
 */
@Composable
fun SnapchatStyleAR3DArrow(
    matches: List<FeatureMatchResult>,
    isFeatureMappingEnabled: Boolean,
    screenWidth: Float,
    screenHeight: Float,
    cameraRotation: Float = 0f,
    deviceOrientation: Float = 0f,
    modifier: Modifier = Modifier
) {
    if (!isFeatureMappingEnabled || matches.isEmpty()) return
    
    // Verwende das beste Match für die Pfeil-Platzierung
    val bestMatch = matches.maxByOrNull { it.confidence } ?: return
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // Berechne Pfeil-Position basierend auf Landmark-Position
        val arrowPosition = calculateArrowPosition(
            bestMatch,
            screenWidth,
            screenHeight,
            cameraRotation,
            deviceOrientation
        )
        
        // Zeichne 3D-Pfeil
        draw3DArrow(
            position = arrowPosition,
            confidence = bestMatch.confidence,
            size = 80f
        )
    }
}

/**
 * Berechnet die Position des Pfeils basierend auf dem Landmark-Match
 */
private fun calculateArrowPosition(
    match: FeatureMatchResult,
    screenWidth: Float,
    screenHeight: Float,
    cameraRotation: Float,
    deviceOrientation: Float
): Offset {
    // Verwende die Bildschirmposition des Landmarks falls verfügbar
    val basePosition = match.screenPosition?.let { screenPos ->
        Offset(screenPos.x, screenPos.y)
    } ?: run {
        // Fallback: Zentrale Position
        Offset(screenWidth / 2f, screenHeight / 2f)
    }
    
    // Kompensiere Kamera-Rotation und Geräte-Orientierung
    val rotationCompensation = cameraRotation + deviceOrientation
    
    // Anpassung basierend auf Rotation (vereinfacht)
    val adjustedX = basePosition.x + cos(rotationCompensation * PI / 180f).toFloat() * 20f
    val adjustedY = basePosition.y + sin(rotationCompensation * PI / 180f).toFloat() * 20f
    
    return Offset(
        adjustedX.coerceIn(50f, screenWidth - 50f),
        adjustedY.coerceIn(50f, screenHeight - 50f)
    )
}

/**
 * Zeichnet einen 3D-Pfeil mit Snapchat-Style Effekten
 */
private fun DrawScope.draw3DArrow(
    position: Offset,
    confidence: Float,
    size: Float
) {
    // Farbe basierend auf Confidence
    val arrowColor = when {
        confidence >= 0.9f -> Color.Green
        confidence >= 0.8f -> Color(0xFF90EE90) // Light Green
        confidence >= 0.7f -> Color.Yellow
        confidence >= 0.6f -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }
    
    // Größe basierend auf Confidence
    val adjustedSize = size * (0.7f + confidence * 0.3f)
    
    translate(position.x, position.y) {
        // Schatten-Schicht (tiefste Ebene)
        drawArrowShape(
            size = adjustedSize * 1.1f,
            color = Color.Black.copy(alpha = 0.3f),
            offset = Offset(4f, 4f)
        )
        
        // Basis-Pfeil (mittlere Ebene)
        drawArrowShape(
            size = adjustedSize,
            color = arrowColor.copy(alpha = 0.8f)
        )
        
        // Highlight-Schicht (oberste Ebene)
        drawArrowShape(
            size = adjustedSize * 0.8f,
            color = arrowColor.copy(alpha = 0.6f),
            strokeWidth = 3f
        )
        
        // Glanz-Effekt
        drawArrowGloss(
            size = adjustedSize * 0.6f,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

/**
 * Zeichnet die Grundform des Pfeils
 */
private fun DrawScope.drawArrowShape(
    size: Float,
    color: Color,
    offset: Offset = Offset.Zero,
    strokeWidth: Float = 0f
) {
    val path = Path().apply {
        // Pfeil-Spitze
        moveTo(offset.x, offset.y - size / 2f)
        
        // Rechte Seite
        lineTo(offset.x + size / 3f, offset.y)
        lineTo(offset.x + size / 6f, offset.y)
        
        // Pfeil-Schaft
        lineTo(offset.x + size / 6f, offset.y + size / 2f)
        lineTo(offset.x - size / 6f, offset.y + size / 2f)
        
        // Linke Seite
        lineTo(offset.x - size / 6f, offset.y)
        lineTo(offset.x - size / 3f, offset.y)
        
        close()
    }
    
    if (strokeWidth > 0f) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth)
        )
    } else {
        drawPath(
            path = path,
            color = color
        )
    }
}

/**
 * Zeichnet einen Glanz-Effekt auf den Pfeil
 */
private fun DrawScope.drawArrowGloss(
    size: Float,
    color: Color
) {
    val glossPath = Path().apply {
        moveTo(-size / 4f, -size / 3f)
        lineTo(size / 6f, -size / 3f)
        lineTo(size / 8f, -size / 6f)
        lineTo(-size / 6f, -size / 6f)
        close()
    }
    
    drawPath(
        path = glossPath,
        color = color
    )
}