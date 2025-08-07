package com.example.arwalking.components

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.arwalking.FeatureMatchResult
import kotlinx.coroutines.delay
import kotlin.math.*

/**
 * 3D-Pfeil Overlay für AR-Navigation (Snapchat-Style)
 * Zeigt einen 3D-Pfeil an, der auf erkannte Landmarks zeigt
 * Unterstützt GLB-Modelle für realistischere 3D-Darstellung
 */
@Composable
fun AR3DArrowOverlay(
    matches: List<FeatureMatchResult>,
    isFeatureMappingEnabled: Boolean,
    screenWidth: Float,
    screenHeight: Float,
    currentStep: Int = 1,
    totalSteps: Int = 3,
    useGLBModel: Boolean = false, // Standardmäßig 2D-Rendering verwenden
    currentInstruction: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var glbModel by remember { mutableStateOf<GLBArrowModel?>(null) }
    var isGLBLoaded by remember { mutableStateOf(false) }
    
    // GLB-Modell laden wenn gewünscht
    LaunchedEffect(useGLBModel) {
        if (useGLBModel) {
            val model = GLBArrowModel(context)
            isGLBLoaded = model.loadModel()
            glbModel = model
        }
    }
    
    // Nur den besten Match verwenden
    val bestMatch = matches.maxByOrNull { it.confidence }
    
    if (isFeatureMappingEnabled && bestMatch != null && bestMatch.confidence >= 0.7f) {
        
        // Berechne die Position des Pfeils basierend auf dem Landmark
        val arrowPosition = if (bestMatch.landmark != null) {
            calculateArrowPosition(
                landmark = convertToFeatureLandmark(bestMatch.landmark!!),
                screenPosition = bestMatch.screenPosition,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        } else {
            Offset(screenWidth / 2, screenHeight / 2)
        }
        
        // Berechne die Richtung des Pfeils mit Navigationsdaten
        val arrowDirection = if (bestMatch.landmark != null) {
            calculateArrowDirection(
                landmark = convertToFeatureLandmark(bestMatch.landmark!!),
                currentStep = currentStep,
                totalSteps = totalSteps,
                currentInstruction = currentInstruction
            )
        } else {
            0f
        }
        
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                draw3DArrow(
                    position = arrowPosition,
                    direction = arrowDirection,
                    confidence = bestMatch.confidence,
                    size = size
                )
            }
        }
    }
}

/**
 * Berechnet die Position des 3D-Pfeils auf dem Bildschirm
 */
private fun calculateArrowPosition(
    landmark: com.example.arwalking.FeatureLandmark,
    screenPosition: android.graphics.PointF?,
    screenWidth: Float,
    screenHeight: Float
): Offset {
    // Verwende die tatsächliche Bildschirmposition falls verfügbar
    if (screenPosition != null) {
        return Offset(
            x = screenPosition.x,
            y = screenPosition.y
        )
    }
    
    // Fallback: Verwende eine berechnete Position basierend auf Landmark-ID
    // Dies ist eine vereinfachte Berechnung - in einer echten AR-App würde man
    // die Kamera-Matrix und die 3D-Position des Landmarks verwenden
    
    // Generiere eine konsistente Position basierend auf der Landmark-ID
    val hashCode = (landmark.id ?: "default").hashCode()
    val normalizedX = (abs(hashCode) % 100) / 100f
    val normalizedY = (abs(hashCode / 100) % 100) / 100f
    
    return Offset(
        x = normalizedX * screenWidth,
        y = normalizedY * screenHeight
    )
}

/**
 * Berechnet die Richtung des Pfeils basierend auf der Navigation
 */
private fun calculateArrowDirection(
    landmark: com.example.arwalking.FeatureLandmark,
    currentStep: Int = 1,
    totalSteps: Int = 3,
    currentInstruction: String? = null
): Float {
    // Priorität: Verwende Navigationsanweisung wenn verfügbar
    val baseAngle = if (currentInstruction != null) {
        val lowerInstruction = currentInstruction.lowercase()
        when {
            lowerInstruction.contains("rechts") || lowerInstruction.contains("right") -> 90f // Nach rechts
            lowerInstruction.contains("links") || lowerInstruction.contains("left") -> 270f // Nach links
            lowerInstruction.contains("tür") || lowerInstruction.contains("door") || 
            lowerInstruction.contains("eingang") || lowerInstruction.contains("entrance") ||
            lowerInstruction.contains("durch") || lowerInstruction.contains("through") -> 0f // Geradeaus durch Tür
            else -> 0f // Standard: geradeaus
        }
    } else {
        // Fallback: Berechne Richtung basierend auf Landmark-Typ und Position
        when {
            // Prof. Ludwig Büro (PT-1-86) - Ausgang nach links
            landmark.id == "PT-1-86" -> 270f // Nach links
            
            // Türen/Eingänge - geradeaus durch (z.B. PT-1-566, PT-1-697)
            (landmark.id?.contains("PT-1-566") == true) || (landmark.id?.contains("PT-1-697") == true) -> 0f // Geradeaus
            
            // Allgemeine Türen basierend auf Typ
            landmark.name.contains("Tür", ignoreCase = true) || 
            landmark.name.contains("door", ignoreCase = true) ||
            landmark.name.contains("Entry", ignoreCase = true) -> 0f // Geradeaus
            
            // Büros - nach links
            landmark.name.contains("Prof.", ignoreCase = true) ||
            landmark.name.contains("Office", ignoreCase = true) -> 270f // Nach links
            
            // Treppen - nach oben/unten
            landmark.name.contains("stairs", ignoreCase = true) ||
            landmark.name.contains("Treppe", ignoreCase = true) -> 45f // Diagonal nach oben
            
            // Aufzüge - geradeaus
            landmark.name.contains("elevator", ignoreCase = true) ||
            landmark.name.contains("Aufzug", ignoreCase = true) -> 0f // Geradeaus
            
            else -> {
                // Dynamische Berechnung basierend auf Route-Fortschritt
                val progress = currentStep.toFloat() / totalSteps.toFloat()
                when {
                    progress < 0.33f -> 270f // Anfang: nach links
                    progress < 0.66f -> 0f   // Mitte: geradeaus
                    else -> 90f              // Ende: nach rechts
                }
            }
        }
    }
    
    // Füge leichte Variation basierend auf Landmark-ID hinzu
    val positionVariation = ((landmark.id ?: "default").hashCode() % 10).toFloat() * 2f - 10f
    
    return (baseAngle + positionVariation) % 360f
}

/**
 * Zeichnet einen 3D-Pfeil auf dem Canvas
 */
private fun DrawScope.draw3DArrow(
    position: Offset,
    direction: Float,
    confidence: Float,
    size: androidx.compose.ui.geometry.Size
) {
    val arrowSize = 60.dp.toPx() * confidence // Größe basierend auf Confidence
    val arrowColor = getArrowColor(confidence)
    
    drawIntoCanvas { canvas ->
        // Speichere den aktuellen Zustand
        canvas.save()
        
        // Verschiebe zum Pfeil-Zentrum
        canvas.translate(position.x, position.y)
        
        // Rotiere basierend auf der Richtung
        canvas.rotate(direction)
        
        // Zeichne den 3D-Pfeil
        draw3DArrowShape(canvas.nativeCanvas, arrowSize, arrowColor, confidence)
        
        // Stelle den ursprünglichen Zustand wieder her
        canvas.restore()
    }
}

/**
 * Zeichnet die 3D-Pfeil-Form
 */
private fun DrawScope.draw3DArrowShape(
    canvas: android.graphics.Canvas,
    size: Float,
    color: Color,
    confidence: Float
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    val strokePaint = Paint().apply {
        this.color = Color.White.copy(alpha = 0.8f).toArgb()
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    // Hauptkörper des Pfeils (3D-Effekt durch mehrere Schichten)
    val arrowPath = Path().apply {
        // Pfeilspitze
        moveTo(0f, -size * 0.5f)
        lineTo(size * 0.3f, -size * 0.2f)
        lineTo(size * 0.15f, -size * 0.2f)
        
        // Pfeilkörper
        lineTo(size * 0.15f, size * 0.3f)
        lineTo(-size * 0.15f, size * 0.3f)
        lineTo(-size * 0.15f, -size * 0.2f)
        lineTo(-size * 0.3f, -size * 0.2f)
        
        close()
    }
    
    // Schatten-Effekt (3D-Tiefe)
    val shadowPath = Path(arrowPath).apply {
        offset(size * 0.05f, size * 0.05f)
    }
    
    val shadowPaint = Paint().apply {
        this.color = Color.Black.copy(alpha = 0.3f).toArgb()
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    // Zeichne Schatten
    canvas.drawPath(shadowPath, shadowPaint)
    
    // Zeichne Hauptpfeil
    canvas.drawPath(arrowPath, paint)
    
    // Zeichne Umriss
    canvas.drawPath(arrowPath, strokePaint)
    
    // Zeichne Glanz-Effekt für 3D-Look
    val highlightPaint = Paint().apply {
        this.color = Color.White.copy(alpha = 0.4f * confidence).toArgb()
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    val highlightPath = Path().apply {
        moveTo(-size * 0.05f, -size * 0.4f)
        lineTo(size * 0.05f, -size * 0.3f)
        lineTo(size * 0.05f, -size * 0.1f)
        lineTo(-size * 0.05f, -size * 0.2f)
        close()
    }
    
    canvas.drawPath(highlightPath, highlightPaint)
}

/**
 * Bestimmt die Farbe des Pfeils basierend auf der Confidence
 */
private fun getArrowColor(confidence: Float): Color {
    return when {
        confidence >= 0.9f -> Color(0xFF4CAF50) // Grün - Sehr sicher
        confidence >= 0.8f -> Color(0xFF8BC34A) // Hellgrün - Sicher
        confidence >= 0.7f -> Color(0xFFFFEB3B) // Gelb - Okay
        else -> Color(0xFFFF9800) // Orange - Unsicher
    }
}

/**
 * Erweiterte 3D-Pfeil-Komponente mit Animation
 */
@Composable
fun Animated3DArrowOverlay(
    matches: List<FeatureMatchResult>,
    isFeatureMappingEnabled: Boolean,
    screenWidth: Float,
    screenHeight: Float,
    currentStep: Int = 1,
    totalSteps: Int = 3,
    modifier: Modifier = Modifier,
    currentInstruction: String? = null
) {
    val bestMatch = matches.maxByOrNull { it.confidence }
    var animationProgress by remember { mutableStateOf(0f) }
    
    // Animation für pulsierenden Effekt
    LaunchedEffect(bestMatch) {
        if (bestMatch != null && bestMatch.confidence >= 0.7f) {
            while (true) {
                animationProgress = (animationProgress + 0.02f) % 1f
                delay(16) // ~60 FPS
            }
        }
    }
    
    if (isFeatureMappingEnabled && bestMatch != null && bestMatch.confidence >= 0.7f) {
        val arrowPosition = if (bestMatch.landmark != null) {
            calculateArrowPosition(
                landmark = convertToFeatureLandmark(bestMatch.landmark!!),
                screenPosition = bestMatch.screenPosition,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        } else {
            Offset(screenWidth / 2, screenHeight / 2)
        }
        
        val arrowDirection = if (bestMatch.landmark != null) {
            calculateArrowDirection(
                landmark = convertToFeatureLandmark(bestMatch.landmark!!),
                currentStep = currentStep,
                totalSteps = totalSteps,
                currentInstruction = currentInstruction
            )
        } else {
            0f
        }
        
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawAnimated3DArrow(
                    position = arrowPosition,
                    direction = arrowDirection,
                    confidence = bestMatch.confidence,
                    animationProgress = animationProgress,
                    size = size
                )
            }
        }
    }
}

/**
 * Zeichnet einen animierten 3D-Pfeil
 */
private fun DrawScope.drawAnimated3DArrow(
    position: Offset,
    direction: Float,
    confidence: Float,
    animationProgress: Float,
    size: androidx.compose.ui.geometry.Size
) {
    val baseSize = 60.dp.toPx()
    val pulseScale = 1f + sin(animationProgress * 2 * PI).toFloat() * 0.1f
    val arrowSize = baseSize * confidence * pulseScale
    val arrowColor = getArrowColor(confidence)
    
    // Leichtes Schweben des Pfeils
    val hoverOffset = sin(animationProgress * 4 * PI).toFloat() * 5f
    val adjustedPosition = position.copy(y = position.y + hoverOffset)
    
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(adjustedPosition.x, adjustedPosition.y)
        canvas.rotate(direction + animationProgress * 2f) // Leichte Rotation
        
        draw3DArrowShape(canvas.nativeCanvas, arrowSize, arrowColor, confidence)
        
        canvas.restore()
    }
}

/**
 * GLB-Model Loader für 3D-Pfeil (Fallback auf 2D-Rendering)
 * Lädt das arrow.glb Modell aus den Assets oder verwendet 2D-Fallback
 */
class GLBArrowModel(private val context: Context) {
    private var isLoaded = false
    private var modelData: ByteArray? = null
    
    suspend fun loadModel(): Boolean {
        return try {
            val inputStream = context.assets.open("models/arrow.glb")
            modelData = inputStream.readBytes()
            inputStream.close()
            isLoaded = true
            android.util.Log.i("GLBArrowModel", "GLB-Modell erfolgreich geladen")
            true
        } catch (e: Exception) {
            android.util.Log.w("GLBArrowModel", "GLB-Modell nicht gefunden, verwende 2D-Fallback: ${e.message}")
            // Fallback: Verwende 2D-Rendering
            isLoaded = false
            false
        }
    }
    
    fun isModelLoaded(): Boolean = isLoaded
    
    fun getModelData(): ByteArray? = modelData
    
    fun hasGLBSupport(): Boolean = isLoaded && modelData != null
}





/**
 * Konvertiert ProcessedLandmark zu FeatureLandmark
 */
private fun convertToFeatureLandmark(processedLandmark: com.example.arwalking.ProcessedLandmark): com.example.arwalking.FeatureLandmark {
    return com.example.arwalking.FeatureLandmark(
        id = processedLandmark.id,
        name = processedLandmark.name,
        description = "Processed landmark",
        position = null, // Position wird aus screenPosition berechnet
        imageUrl = null
    )
}