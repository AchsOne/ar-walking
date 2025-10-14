package com.example.arwalking.ar

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.StateFlow
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * AR 3D Renderer für 3D-Modell-Projektion
 * 
 * Arbeitet mit ARCoreSessionManager zusammen:
 * - Verwendet ARCore Camera-Pose für korrekte 3D-Projektion
 * - Rendert 3D-Modelle an realen Weltpositionen
 * - Kombiniert mit AKAZE Landmark-Erkennung für präzise Platzierung
 */
class AR3DRenderer(
    private val context: Context,
    private val sessionManager: ARCoreSessionManager
) {

    companion object {
        private const val TAG = "AR3DRenderer"
        
        // Shader-Programme
        private const val VERTEX_SHADER = """
            #version 300 es
            
            uniform mat4 u_ModelView;
            uniform mat4 u_Projection;
            uniform mat4 u_ModelViewProjection;
            
            in vec4 a_Position;
            in vec3 a_Normal;
            in vec2 a_TexCoord;
            
            out vec3 v_ViewPosition;
            out vec3 v_ViewNormal;
            out vec2 v_TexCoord;
            
            void main() {
                v_ViewPosition = (u_ModelView * a_Position).xyz;
                v_ViewNormal = normalize((u_ModelView * vec4(a_Normal, 0.0)).xyz);
                v_TexCoord = a_TexCoord;
                
                gl_Position = u_ModelViewProjection * a_Position;
            }
        """
        
        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            
            uniform sampler2D u_Texture;
            uniform vec3 u_LightingParameters;
            uniform vec4 u_MaterialParameters;
            
            in vec3 v_ViewPosition;
            in vec3 v_ViewNormal;
            in vec2 v_TexCoord;
            
            out vec4 fragColor;
            
            void main() {
                // Einfaches Phong-Lighting
                vec3 viewLightDirection = u_LightingParameters;
                
                float lightIntensity = dot(normalize(v_ViewNormal), normalize(viewLightDirection));
                lightIntensity = max(0.1, lightIntensity); // Ambient minimum
                
                vec4 objectColor = texture(u_Texture, v_TexCoord);
                fragColor = vec4(objectColor.rgb * lightIntensity, objectColor.a * u_MaterialParameters.a);
            }
        """
    }

    // OpenGL Rendering State
    private var shaderProgram: Int = 0
    private var isInitialized = false
    
    // Matrix-Berechnungen
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)
    
    // 3D-Arrow-Renderer (lädt euer GLB-Modell)
    private var arrowRenderer: ArrowGLBRenderer? = null
    
    // ARCore Anchors für stabile 3D-Platzierung
    private val arCoreAnchors = mutableListOf<Pair<Anchor, AnchoredObject>>()
    
    // Legacy Objects (ohne ARCore Anchors) - wird deprecated
    private val anchoredObjects = mutableListOf<AnchoredObject>()
    
    /**
     * Initialisiert OpenGL-Ressourcen (wird vom ARCoreSessionManager aufgerufen)
     */
    fun initializeGL() {
        Log.i(TAG, "🎨 3D Renderer initialisiert")
        
        // OpenGL initialisieren
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        
        // Shader-Programm kompilieren
        shaderProgram = createShaderProgram()
        if (shaderProgram != 0) {
            Log.i(TAG, "✅ Shader-Programm erfolgreich erstellt")
            
            // Arrow GLB Renderer initialisieren
            arrowRenderer = ArrowGLBRenderer(context).apply {
                if (!initialize()) {
                    Log.e(TAG, "❌ Arrow GLB Renderer konnte nicht initialisiert werden")
                }
            }
            
            isInitialized = true
        } else {
            Log.e(TAG, "❌ Fehler beim Erstellen des Shader-Programms")
        }
    }

    /**
     * Surface-Größe geändert (wird vom ARCoreSessionManager aufgerufen)
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        Log.i(TAG, "🎨 3D Renderer Surface geändert: ${width}x${height}")
        GLES30.glViewport(0, 0, width, height)
    }

    /**
     * Rendert 3D-Objekte (wird vom ARCoreSessionManager pro Frame aufgerufen)
     */
    fun render3DContent(currentFrame: Frame, cameraPose: Pose, trackingState: TrackingState) {
        if (!isInitialized) return
        
        // Nur rendern wenn Tracking aktiv
        if (trackingState != TrackingState.TRACKING) {
            return
        }
        
        // Kamera-Matrizen von ARCore holen
        updateCameraMatrices(currentFrame, cameraPose)
        
        // 3D-Objekte rendern
        render3DObjects()
    }
    
    /**
     * Aktualisiert Kamera-Matrizen von ARCore
     */
    private fun updateCameraMatrices(frame: Frame, pose: Pose) {
        try {
            // Projection Matrix von ARCore
            frame.camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
            
            // View Matrix von ARCore (inverse der Kamera-Pose)
            frame.camera.getViewMatrix(viewMatrix, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Aktualisieren der Kamera-Matrizen", e)
        }
    }
    
    /**
     * Rendert alle 3D-Objekte an ihren Ankerpositionen
     */
    private fun render3DObjects() {
        if (arrowRenderer == null) return
        
        GLES30.glUseProgram(shaderProgram)
        
        // 1. ARCore Anchors rendern (stabil im Raum verankert)
        renderARCoreAnchoredObjects()
        
        // 2. Legacy Objects rendern (für Rückwärtskompatibilität)
        renderLegacyObjects()
    }
    
    /**
     * Rendert ARCore-verankerte 3D-Objekte (stabil im Raum)
     */
    private fun renderARCoreAnchoredObjects() {
        if (arrowRenderer == null) return
        
        arCoreAnchors.forEach { (anchor, anchoredObject) ->
            // Nur rendern wenn Anchor getrackt wird
            if (anchor.trackingState == TrackingState.TRACKING) {
                
                // Model Matrix direkt vom Anchor (bereits in Weltkoordinaten)
                anchor.pose.toMatrix(modelMatrix, 0)
                
                // Skalierung und Rotation anwenden
                Matrix.scaleM(modelMatrix, 0, anchoredObject.scale, anchoredObject.scale, anchoredObject.scale)
                Matrix.rotateM(modelMatrix, 0, anchoredObject.rotationY, 0.0f, 1.0f, 0.0f)
                
                // Model-View Matrix
                Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
                
                // Model-View-Projection Matrix
                Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)
                
                // Shader Uniforms setzen
                setShaderUniforms(anchoredObject.alpha)
                
                // 3D-Arrow (GLB) zeichnen
                arrowRenderer?.drawArrow()
                
                // Debug-Log (nur gelegentlich)
                if (System.currentTimeMillis() % 2000 < 50) {
                    Log.d(TAG, "🎯 ARCore Anchor gerendert: ${anchoredObject.navigationType} (stable)")
                }
            } else {
                // Anchor nicht getrackt - überspringe Rendering
                Log.v(TAG, "⚠️ Anchor nicht getrackt: ${anchor.trackingState}")
            }
        }
    }
    
    /**
     * Rendert Legacy-Objekte (ohne ARCore Anchors)
     */
    private fun renderLegacyObjects() {
        // Legacy objects werden nicht mehr benötigt - alles geht über ARCore Anchors
        if (anchoredObjects.isNotEmpty()) {
            Log.w(TAG, "⚠️ Legacy objects erkannt - verwende stattdessen addARCoreAnchoredObject()")
        }
    }
    
    /**
     * Setzt Shader Uniforms (Hilfsmethode)
     */
    private fun setShaderUniforms(alpha: Float) {
        // Matrizen an Shader übergeben
        val modelViewHandle = GLES30.glGetUniformLocation(shaderProgram, "u_ModelView")
        val projectionHandle = GLES30.glGetUniformLocation(shaderProgram, "u_Projection")
        val mvpHandle = GLES30.glGetUniformLocation(shaderProgram, "u_ModelViewProjection")
        
        GLES30.glUniformMatrix4fv(modelViewHandle, 1, false, modelViewMatrix, 0)
        GLES30.glUniformMatrix4fv(projectionHandle, 1, false, projectionMatrix, 0)
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, modelViewProjectionMatrix, 0)
        
        // Lighting setzen (vereinfacht)
        val lightingHandle = GLES30.glGetUniformLocation(shaderProgram, "u_LightingParameters")
        GLES30.glUniform3f(lightingHandle, 0.0f, 1.0f, 0.0f) // Licht von oben
        
        // Material-Parameter
        val materialHandle = GLES30.glGetUniformLocation(shaderProgram, "u_MaterialParameters")
        GLES30.glUniform4f(materialHandle, 1.0f, 1.0f, 1.0f, alpha)
    }
    
    /**
     * Fügt ein ARCore-verankertes 3D-Objekt hinzu (EMPFOHLEN)
     * Objekt bleibt stabil im Raum verankert beim Bewegen
     */
    fun addARCoreAnchoredObject(
        worldPosition: FloatArray, // [x, y, z] in ARCore Weltkoordinaten
        navigationType: NavigationType = NavigationType.ARROW,
        scale: Float = 0.1f, // 10cm Standard-Größe
        rotationY: Float = 0f, // Rotation um Y-Achse (Richtung)
        alpha: Float = 0.8f
    ): Anchor? {
        return try {
            // ARCore Pose aus Weltposition erstellen
            val pose = Pose.makeTranslation(worldPosition[0], worldPosition[1], worldPosition[2])
            
            // ARCore Anchor erstellen (verankert im Raum!)
            // Session muss über sessionManager.session geholt werden (nicht über Frame)
            val anchor = createAnchorInSession(pose)
            
            if (anchor != null) {
                val anchoredObject = AnchoredObject(
                    id = System.currentTimeMillis().toString(),
                    worldPosition = worldPosition.clone(),
                    navigationType = navigationType,
                    scale = scale,
                    rotationY = rotationY,
                    alpha = alpha,
                    timestamp = System.currentTimeMillis()
                )
                
                arCoreAnchors.add(anchor to anchoredObject)
                
                Log.i(TAG, "➕ ARCore Anchor erstellt: ${navigationType} at (${String.format("%.2f", worldPosition[0])}, ${String.format("%.2f", worldPosition[1])}, ${String.format("%.2f", worldPosition[2])}) - STABIL IM RAUM")
                
                anchor
            } else {
                Log.e(TAG, "❌ ARCore Anchor konnte nicht erstellt werden")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Erstellen des ARCore Anchors", e)
            null
        }
    }
    
    /**
     * Legacy: Fügt ein 3D-Objekt an einer Weltposition hinzu (DEPRECATED)
     * Verwende stattdessen addARCoreAnchoredObject() für stabile Platzierung
     */
    @Deprecated("Verwende addARCoreAnchoredObject() für stabile 3D-Platzierung")
    fun addAnchoredObject(
        worldPosition: FloatArray, // [x, y, z] in ARCore Weltkoordinaten
        navigationType: NavigationType = NavigationType.ARROW,
        scale: Float = 0.1f, // 10cm Standard-Größe
        rotationY: Float = 0f, // Rotation um Y-Achse (Richtung)
        alpha: Float = 0.8f
    ) {
        val anchoredObject = AnchoredObject(
            id = System.currentTimeMillis().toString(),
            worldPosition = worldPosition.clone(),
            navigationType = navigationType,
            scale = scale,
            rotationY = rotationY,
            alpha = alpha,
            timestamp = System.currentTimeMillis()
        )
        
        anchoredObjects.add(anchoredObject)
        
        Log.i(TAG, "➕ 3D-Objekt hinzugefügt: ${navigationType} at (${String.format("%.2f", worldPosition[0])}, ${String.format("%.2f", worldPosition[1])}, ${String.format("%.2f", worldPosition[2])})")
    }
    
    /**
     * Entfernt alle ARCore Anchors und 3D-Objekte
     */
    fun clearAnchoredObjects() {
        // ARCore Anchors detachen (wichtig für Memory!)
        arCoreAnchors.forEach { (anchor, _) ->
            anchor.detach()
        }
        arCoreAnchors.clear()
        
        // Legacy Objects
        anchoredObjects.clear()
        
        Log.i(TAG, "🗑️ Alle 3D-Objekte und ARCore Anchors entfernt")
    }
    
    /**
     * Hybrid-Integration: Platziert 3D-Pfeil basierend auf AKAZE Landmark-Erkennung
     */
    fun placeArrowAtLandmark(
        landmarkWorldPosition: FloatArray, // Von AKAZE + ARCore Pose-Schätzung
        navigationDirection: Float, // Zielrichtung in Grad
        confidence: Float // AKAZE Confidence für Alpha-Wert
    ) {
        // Pfeil etwas über dem Landmark platzieren (0.5m höher)
        val arrowPosition = floatArrayOf(
            landmarkWorldPosition[0],
            landmarkWorldPosition[1] + 0.5f, // 50cm höher
            landmarkWorldPosition[2]
        )
        
        addAnchoredObject(
            worldPosition = arrowPosition,
            navigationType = NavigationType.ARROW,
            scale = 0.15f * confidence, // Größe basierend auf Confidence
            rotationY = navigationDirection,
            alpha = 0.6f + (confidence * 0.4f) // 60-100% Alpha je nach Confidence
        )
    }
    
    /**
     * Erstellt einen ARCore Anchor über den SessionManager
     */
    private fun createAnchorInSession(pose: Pose): Anchor? {
        return try {
            // Wir nutzen eine öffentliche Methode im SessionManager
            sessionManager.createAnchor(pose)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Erstellen des Anchors", e)
            null
        }
    }
    
    // ==================== SHADER MANAGEMENT ====================
    
    private fun createShaderProgram(): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        
        if (vertexShader == 0 || fragmentShader == 0) {
            return 0
        }
        
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        
        if (linkStatus[0] == 0) {
            Log.e(TAG, "❌ Shader-Programm-Link-Fehler: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            return 0
        }
        
        return program
    }
    
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            Log.e(TAG, "❌ Shader-Kompilier-Fehler: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    // ==================== DATA CLASSES ====================
    
    /**
     * 3D-Objekt verankert in der AR-Welt
     */
    data class AnchoredObject(
        val id: String,
        val worldPosition: FloatArray, // ARCore Weltkoordinaten
        val navigationType: NavigationType,
        val scale: Float = 0.1f,
        val rotationY: Float = 0f, // Richtung in Grad
        val alpha: Float = 0.8f,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class NavigationType {
        ARROW           // 3D-Navigationspfeil (aus GLB-Modell)
    }
}

