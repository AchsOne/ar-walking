package com.example.arwalking.components

import android.content.Context
import android.graphics.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.arwalking.FeatureMatchResult
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
    useGLBModel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Nur den besten Match verwenden
    val bestMatch = matches.maxByOrNull { it.confidence }
    
    if (isFeatureMappingEnabled && bestMatch != null && bestMatch.confidence >= 0.7f) {
        
        // Berechne die Position des Pfeils basierend auf dem Landmark
        val arrowPosition = calculateArrowPosition(
            landmark = bestMatch.landmark,
            screenPosition = bestMatch.screenPosition,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        
        // Berechne die Richtung des Pfeils mit Navigationsdaten
        val arrowDirection = calculateArrowDirection(
            landmark = bestMatch.landmark,
            currentStep = currentStep,
            totalSteps = totalSteps
        )
        
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
    
    // Fallback: Verwende die Position des Landmarks, um die Bildschirmposition zu berechnen
    // Dies ist eine vereinfachte Berechnung - in einer echten AR-App würde man
    // die Kamera-Matrix und die 3D-Position des Landmarks verwenden
    
    val x = landmark.position.x.toFloat()
    val y = landmark.position.y.toFloat()
    
    // Normalisiere die Position auf Bildschirmkoordinaten
    val normalizedX = (x % 100) / 100f // Vereinfachte Normalisierung
    val normalizedY = (y % 100) / 100f
    
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
    totalSteps: Int = 3
): Float {
    // Berechne Richtung basierend auf Landmark-Typ und Position
    val baseAngle = when {
        // Prof. Ludwig Büro - Ausgang nach links
        landmark.id == "prof_ludwig_office" -> 270f // Nach links
        
        // Türen/Eingänge - geradeaus durch
        landmark.id.contains("entrance") || landmark.id.contains("door") -> 0f // Geradeaus
        
        // Treppen - nach oben/unten
        landmark.id.contains("stairs") -> 45f // Diagonal nach oben
        
        // Aufzüge - geradeaus
        landmark.id.contains("elevator") -> 0f // Geradeaus
        
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
    
    // Füge leichte Variation basierend auf Position hinzu
    val positionVariation = (landmark.position.x % 10).toFloat() * 2f - 10f
    
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
        draw3DArrowShape(canvas as Canvas, arrowSize, arrowColor, confidence)
        
        // Stelle den ursprünglichen Zustand wieder her
        canvas.restore()
    }
}

/**
 * Zeichnet die 3D-Pfeil-Form
 */
private fun DrawScope.draw3DArrowShape(
    canvas: Canvas,
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
    modifier: Modifier = Modifier
) {
    val bestMatch = matches.maxByOrNull { it.confidence }
    var animationProgress by remember { mutableStateOf(0f) }
    
    // Animation für pulsierenden Effekt
    LaunchedEffect(bestMatch) {
        if (bestMatch != null && bestMatch.confidence >= 0.7f) {
            while (true) {
                animationProgress = (animationProgress + 0.02f) % 1f
                kotlinx.coroutines.delay(16) // ~60 FPS
            }
        }
    }
    
    if (isFeatureMappingEnabled && bestMatch != null && bestMatch.confidence >= 0.7f) {
        val arrowPosition = calculateArrowPosition(
            landmark = bestMatch.landmark,
            screenPosition = bestMatch.screenPosition,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        
        val arrowDirection = calculateArrowDirection(
            landmark = bestMatch.landmark,
            currentStep = currentStep,
            totalSteps = totalSteps
        )

        Box(modifier = modifier.fillMaxSize()) {
            // Canvas(modifier = Modifier.fillMaxSize()) {
            //     drawAnimated3DArrow(
            //         position = arrowPosition,
            //         direction = arrowDirection,
            //         confidence = bestMatch.confidence,
            //         animationProgress = animationProgress,
            //         size = size
            //     )
            // }
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
        
        draw3DArrowShape(canvas as Canvas, arrowSize, arrowColor, confidence)
        
        canvas.restore()
    }
}

/**
 * GLB-Model Loader für 3D-Pfeil
 * Lädt das arrow.glb Modell aus den Assets
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
            true
        } catch (e: Exception) {
            android.util.Log.e("GLBArrowModel", "Fehler beim Laden des GLB-Modells: ${e.message}")
            false
        }
    }
    
    fun isModelLoaded(): Boolean = isLoaded
    
    fun getModelData(): ByteArray? = modelData
}

/**
 * Snapchat-Style AR Arrow mit verbesserter 3D-Positionierung
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
    val context = LocalContext.current
    val bestMatch = matches.maxByOrNull { it.confidence }
    
    // GLB Model State
    var glbModel by remember { mutableStateOf<GLBArrowModel?>(null) }
    var isModelLoaded by remember { mutableStateOf(false) }
    
    // Lade GLB-Modell
    LaunchedEffect(Unit) {
        val model = GLBArrowModel(context)
        if (model.loadModel()) {
            glbModel = model
            isModelLoaded = true
        }
    }
    
    if (isFeatureMappingEnabled && bestMatch != null && bestMatch.confidence >= 0.7f) {
        
        // Berechne stabilisierte 3D-Position (Snapchat-Style)
        val stabilizedPosition = calculateStabilized3DPosition(
            landmark = bestMatch.landmark,
            screenPosition = bestMatch.screenPosition,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            cameraRotation = cameraRotation,
            deviceOrientation = deviceOrientation
        )
        
        // Berechne Pfeil-Orientierung mit Smooth-Tracking
        val arrowOrientation = calculateSmoothArrowOrientation(
            landmark = bestMatch.landmark,
            confidence = bestMatch.confidence,
            cameraRotation = cameraRotation
        )
        
        Box(modifier = modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (isModelLoaded) {
                    // Verwende GLB-Modell falls verfügbar
                    drawGLBArrow(
                        position = stabilizedPosition,
                        orientation = arrowOrientation,
                        confidence = bestMatch.confidence,
                        size = size
                    )
                } else {
                    // Fallback auf verbesserte 2D-Darstellung
                    drawEnhanced3DArrow(
                        position = stabilizedPosition,
                        orientation = arrowOrientation,
                        confidence = bestMatch.confidence,
                        size = size
                    )
                }
            }
        }
    }
}

/**
 * Berechnet stabilisierte 3D-Position für Snapchat-Style AR
 */
private fun calculateStabilized3DPosition(
    landmark: com.example.arwalking.FeatureLandmark,
    screenPosition: PointF?,
    screenWidth: Float,
    screenHeight: Float,
    cameraRotation: Float,
    deviceOrientation: Float
): Offset {
    
    if (screenPosition != null) {
        // Kompensiere Kamera- und Gerätebewegung für stabilere Positionierung
        val stabilizedX = screenPosition.x + 
            sin(cameraRotation * PI / 180f).toFloat() * 10f
        val stabilizedY = screenPosition.y + 
            cos(deviceOrientation * PI / 180f).toFloat() * 5f
        
        return Offset(
            x = stabilizedX.coerceIn(0f, screenWidth),
            y = stabilizedY.coerceIn(0f, screenHeight)
        )
    }
    
    // Fallback: Verwende Landmark-Position mit verbesserter Berechnung
    val normalizedX = (landmark.position.x.toFloat() % 100) / 100f
    val normalizedY = (landmark.position.y.toFloat() % 100) / 100f
    
    return Offset(
        x = normalizedX * screenWidth,
        y = normalizedY * screenHeight
    )
}

/**
 * Berechnet sanfte Pfeil-Orientierung mit Smooth-Tracking
 */
private fun calculateSmoothArrowOrientation(
    landmark: com.example.arwalking.FeatureLandmark,
    confidence: Float,
    cameraRotation: Float
): Float {
    
    // Basis-Richtung basierend auf Landmark-Typ
    val baseDirection = when {
        landmark.id.contains("entrance") -> 0f    // Geradeaus
        landmark.id.contains("stairs") -> 45f     // Diagonal nach oben
        landmark.id.contains("elevator") -> 0f    // Geradeaus
        landmark.id.contains("office") -> 270f    // Nach links
        else -> 0f
    }
    
    // Kompensiere Kamerabewegung für stabilere Orientierung
    val stabilizedDirection = baseDirection - cameraRotation
    
    // Füge Confidence-basierte Variation hinzu
    val confidenceVariation = (1f - confidence) * 10f * sin(System.currentTimeMillis() / 1000f)
    
    return (stabilizedDirection + confidenceVariation) % 360f
}

/**
 * Zeichnet GLB-basierte 3D-Pfeil (Placeholder für echte GLB-Rendering)
 */
private fun DrawScope.drawGLBArrow(
    position: Offset,
    orientation: Float,
    confidence: Float,
    size: androidx.compose.ui.geometry.Size
) {
    // Placeholder: In einer echten Implementierung würde hier
    // das GLB-Modell mit einer 3D-Rendering-Engine gerendert
    
    // Für jetzt verwenden wir eine verbesserte 2D-Darstellung
    drawEnhanced3DArrow(position, orientation, confidence, size)
}

/**
 * Verbesserte 3D-Pfeil-Darstellung mit realistischeren Effekten
 */
private fun DrawScope.drawEnhanced3DArrow(
    position: Offset,
    orientation: Float,
    confidence: Float,
    size: androidx.compose.ui.geometry.Size
) {
    val arrowSize = 80.dp.toPx() * confidence
    val arrowColor = getArrowColor(confidence)
    
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(position.x, position.y)
        canvas.rotate(orientation)
        
        // Zeichne mehrschichtigen 3D-Effekt
        drawLayered3DArrow(canvas as Canvas, arrowSize, arrowColor, confidence)
        
        canvas.restore()
    }
}

/**
 * Zeichnet mehrschichtigen 3D-Pfeil für realistischeren Effekt
 */
private fun DrawScope.drawLayered3DArrow(
    canvas: Canvas,
    size: Float,
    color: Color,
    confidence: Float
) {
    // Basis-Schatten (tiefste Schicht)
    drawArrowLayer(canvas, size * 1.1f, Color.Black.copy(alpha = 0.3f), 
                  offsetX = size * 0.08f, offsetY = size * 0.08f)
    
    // Mittlerer Schatten
    drawArrowLayer(canvas, size * 1.05f, Color.Black.copy(alpha = 0.2f),
                  offsetX = size * 0.04f, offsetY = size * 0.04f)
    
    // Hauptkörper
    drawArrowLayer(canvas, size, color)
    
    // Glanz-Highlight (oberste Schicht)
    drawArrowLayer(canvas, size * 0.8f, Color.White.copy(alpha = 0.3f * confidence),
                  offsetX = -size * 0.02f, offsetY = -size * 0.02f)
}

/**
 * Zeichnet eine einzelne Pfeil-Schicht
 */
private fun drawArrowLayer(
    canvas: Canvas,
    size: Float,
    color: Color,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val paint = Paint().apply {
        this.color = color.toArgb()
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    val arrowPath = Path().apply {
        // Pfeilspitze
        moveTo(offsetX, offsetY - size * 0.5f)
        lineTo(offsetX + size * 0.3f, offsetY - size * 0.2f)
        lineTo(offsetX + size * 0.15f, offsetY - size * 0.2f)
        
        // Pfeilkörper
        lineTo(offsetX + size * 0.15f, offsetY + size * 0.3f)
        lineTo(offsetX - size * 0.15f, offsetY + size * 0.3f)
        lineTo(offsetX - size * 0.15f, offsetY - size * 0.2f)
        lineTo(offsetX - size * 0.3f, offsetY - size * 0.2f)
        
        close()
    }
    
    canvas.drawPath(arrowPath, paint)
}