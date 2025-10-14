package com.example.arwalking.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.example.arwalking.RouteViewModel
/**
 * ProjectedGroundArrows
 *
 * Zeichnet eine Reihe von 2D-Pfeilen, die perspektivisch auf den "Boden" projiziert wirken.
 * Nutzt die bestehende Landmark- und Richtungslogik, zeigt aber ein 2D Overlay statt 3D GLB.
 */
@Composable
fun ProjectedGroundArrows(
    matches: List<RouteViewModel.LandmarkMatch>,
    isEnabled: Boolean,
    routeViewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val bestMatch = matches.maxByOrNull { it.confidence }
    val contextDirection = remember(bestMatch?.landmark?.id) {
        bestMatch?.landmark?.id
    }

    // Ground projection state from ViewModel
    val groundState by routeViewModel.groundProjection.collectAsState()

    // Current navigation instruction (by current step)
    val currentStepIndex by routeViewModel.currentNavigationStep.collectAsState()
    val steps = remember { mutableListOf<com.example.arwalking.NavigationStep>() }
    // Refresh steps snapshot when route changes length or index moves
    val stepList = routeViewModel.getCurrentNavigationSteps()
    steps.clear(); steps.addAll(stepList)

    val currentInstruction = steps.getOrNull(currentStepIndex)?.instruction ?: ""

    // Prefer instruction of the step that contains the recognized landmark
    val stepInstructionForBestMatch = bestMatch?.let { bm ->
        val stepForLm = steps.indexOfFirst { s -> s.landmarks.any { it.id == bm.landmark.id } }
        if (stepForLm >= 0) steps[stepForLm].instruction else null
    }

    val chosenInstruction = when {
        !stepInstructionForBestMatch.isNullOrBlank() -> stepInstructionForBestMatch
        currentInstruction.isNotBlank() -> currentInstruction
        else -> ""
    }

    val chosenDirectionName = when {
        chosenInstruction.contains("links", ignoreCase = true) -> "Links"
        chosenInstruction.contains("rechts", ignoreCase = true) -> "Rechts"
        chosenInstruction.contains("zurück", ignoreCase = true) -> "Zurück"
        chosenInstruction.isNotBlank() -> "Geradeaus"
        else -> "Geradeaus"
    }

    // Only show when we have a confident recognition
    val isRecognized = bestMatch != null && bestMatch.matchCount >= 2 && bestMatch.confidence >= 0.4f
    if (!isEnabled || !isRecognized) {
        Log.d("ProjectedArrows", "🔍 Kein Pfeil: enabled=$isEnabled, recognized=$isRecognized, best=${bestMatch?.landmark?.id}")
        return
    }

    // Combine route direction with camera roll to keep arrows level relative to ground
    val targetRotation = directionToRotation(chosenDirectionName) - groundState.rollDeg

    // Smooth rotation to reduce jitter
    var rotationDeg by androidx.compose.runtime.remember { mutableStateOf(targetRotation) }
    LaunchedEffect(targetRotation) {
        // simple exponential smoothing towards target
        val t = 0.2f // higher = faster response, lower = smoother
        rotationDeg = lerpAngle(rotationDeg, targetRotation, t)
    }

    if (!isEnabled) {
        Log.d("ProjectedArrows", "🔍 Kein Pfeil: disabled")
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // Wir rendern Pfeile vom unteren Bildschirmbereich in Richtung Top/Mitte.
        // Perspektive: weiter entfernte Pfeile kleiner und transparenter.
        val centerX = size.width / 2f
        val centerY = size.height * 0.66f // tiefer zentriert, verhindert Top-Überdeckung und hält Pfeile im Bild

        // Anpassung basierend auf Pitch: Kamera nach unten -> größere Abstände (stärkerer "Tiefeneindruck")
        val pitchFactor = (1f + (groundState.pitchDeg / 45f)).coerceIn(0.7f, 1.5f)

        // Welt zentriert platzieren, dann drehen
        translate(left = centerX, top = centerY) {
            rotate(degrees = rotationDeg) {
                // Weniger Elemente: drei Boden-Dreiecke
                val arrowCount = 3
                val baseSpacing = size.minDimension * 0.09f * pitchFactor

                (0 until arrowCount).forEach { i ->
                    // i=0: nah, i steigt → weiter entfernt
                    val depthFactor = 1f - (i / (arrowCount.toFloat() + 1.5f))
                    val scaleFactor = (0.92f + depthFactor * 0.48f)
                    val alpha = (0.28f + depthFactor * 0.72f).coerceIn(0f, 1f)

                    val forward = -(i + 1) * baseSpacing * 1.6f

                    // keine seitliche Variation → stabil und mittig
                    val lateral = 0f

                    translate(lateral, forward) {
                        // Stärker abflachen, damit sie "auf dem Boden" liegen
                        val sx = scaleFactor
                        val sy = scaleFactor * 0.42f
                        scale(sx, sy) {
                            drawGroundTriangle(
                                width = size.minDimension * 0.20f,
                                height = size.minDimension * 0.10f,
                                colorA = Color(0xFF00B8D4).copy(alpha = alpha), // Türkis-Verlauf
                                colorB = Color(0xFF26C6DA).copy(alpha = alpha * 0.85f)
                            )
                        }
                    }
                }

                // Dezente Spur unter den Pfeilen
                drawTrail(
                    length = baseSpacing * 6.5f,
                    width = size.minDimension * 0.030f,
                    color = Color(0xFF00B8D4).copy(alpha = 0.10f)
                )
            }
        }
    }
}

private fun DrawScope.drawGroundTriangle(
    width: Float,
    height: Float,
    colorA: Color,
    colorB: Color
) {
    val halfW = width / 2f

    // Einfaches Dreieck (nur Spitze), deutlicher Richtungspfeil
    val path = Path().apply {
        moveTo(0f, -height)           // Spitze oben
        lineTo(halfW, 0f)             // rechte Basis
        lineTo(-halfW, 0f)            // linke Basis
        close()
    }

    val brush = Brush.verticalGradient(
        colors = listOf(colorA, colorB),
        startY = -height,
        endY = 0f
    )

    drawPath(path = path, brush = brush)

    // Weicher Bodenschatten
    val shadowWidth = width * 0.7f
    val shadowHeight = height * 0.22f
    drawOval(
        color = Color.Black.copy(alpha = 0.08f),
        topLeft = Offset(-shadowWidth / 2f, shadowHeight * 0.35f),
        size = Size(shadowWidth, shadowHeight)
    )
}

private fun DrawScope.drawTrail(length: Float, width: Float, color: Color) {
    // Ein langes, leicht transparentes Rechteck als Wegspur
    drawRect(
        color = color,
        topLeft = Offset(-width / 2f, -length),
        size = Size(width, length)
    )
}

private fun directionToRotation(direction: String): Float = when (direction.lowercase()) {
    "links", "left" -> 270f // nach links
    "rechts", "right" -> 90f // nach rechts
    "zurück", "back" -> 180f // umdrehen
    // gerade/auf/ab projizieren wir als 0° (nach oben im Canvas-Koordinatensystem)
    else -> 0f
}

private fun lerpAngle(a: Float, b: Float, t: Float): Float {
    // shortest path interpolation in degrees
    var delta = ((b - a + 540f) % 360f) - 180f
    return a + delta * t
}
