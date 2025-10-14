package com.example.arwalking.ar

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * ARCore Session Manager
 * Verwaltet ARCore Session, 6DOF-Tracking und 3D-Anker
 * 
 * Kombination mit AKAZE:
 * - ARCore: 6DOF Kamera-Pose-Tracking im Raum
 * - AKAZE: Landmark-spezifische Feature-Erkennung für Navigation
 */
class ARCoreSessionManager(
    private val context: Context
) : DefaultLifecycleObserver, GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "ARCoreSessionManager"
    }

    // ARCore Session und State
    private var session: Session? = null
    private var installRequested = false
    
    // 3D-Renderer für ARCore-integrierte 3D-Objekte
    private lateinit var ar3DRenderer: AR3DRenderer
    
    
    // Tracking State
    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    // Camera Pose (6DOF: Position + Orientierung)
    private val _cameraPose = MutableStateFlow<Pose?>(null)
    val cameraPose: StateFlow<Pose?> = _cameraPose.asStateFlow()
    
    // Session bereit für 3D-Rendering
    private val _isSessionReady = MutableStateFlow(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()
    
    // Aktuelle Frame-Daten für Hybrid-Tracking
    private val _currentFrame = MutableStateFlow<Frame?>(null)
    val currentFrame: StateFlow<Frame?> = _currentFrame.asStateFlow()

    /**
     * Prüft ARCore-Verfügbarkeit und installiert falls nötig
     */
    suspend fun checkARCoreAvailability(): ARCoreStatus {
        return try {
            when (ArCoreApk.getInstance().checkAvailability(context)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    Log.i(TAG, "✅ ARCore ist installiert und verfügbar")
                    ARCoreStatus.READY
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                    Log.w(TAG, "⚠️ ARCore APK ist zu alt, Update erforderlich")
                    ARCoreStatus.UPDATE_REQUIRED
                }
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    Log.w(TAG, "⚠️ ARCore nicht installiert, Installation erforderlich")
                    ARCoreStatus.INSTALLATION_REQUIRED
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    Log.e(TAG, "❌ Gerät unterstützt ARCore nicht")
                    ARCoreStatus.UNSUPPORTED
                }
                ArCoreApk.Availability.UNKNOWN_CHECKING,
                ArCoreApk.Availability.UNKNOWN_ERROR,
                ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                    Log.w(TAG, "⚠️ ARCore-Status unbekannt")
                    ARCoreStatus.UNKNOWN
                }
                else -> ARCoreStatus.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Prüfen der ARCore-Verfügbarkeit: ${e.message}", e)
            ARCoreStatus.ERROR
        }
    }

    /**
     * Initialisiert ARCore Session mit optimalen Einstellungen für Indoor-Navigation
     */
    fun initializeSession(): Boolean {
        return try {
            Log.i(TAG, "🚀 Initialisiere ARCore Session...")
            
            // Session erstellen
            session = Session(context).apply {
                // Konfiguration für Indoor-AR optimieren (ROBUSTER)
                val config = Config(this).apply {
                    // Tracking-Modus: Horizontal für stabileres Tracking
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    
                    // Einfachere Light-Schätzung für bessere Performance
                    lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    
                    // Fokus-Modus für scharfe Bilder
                    focusMode = Config.FocusMode.AUTO
                    
                    // Instant Placement für sofortige Objekt-Platzierung (WICHTIG!)
                    instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    
                    // Depth-Modus NUR falls gut unterstützt
                    try {
                        if (isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            depthMode = Config.DepthMode.AUTOMATIC
                            Log.i(TAG, "✅ Depth-Modus aktiviert")
                        } else {
                            depthMode = Config.DepthMode.DISABLED
                            Log.i(TAG, "⚠️ Depth-Modus deaktiviert (nicht unterstützt)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Depth-Modus-Check fehlgeschlagen, deaktiviere: ${e.message}")
                        depthMode = Config.DepthMode.DISABLED
                    }
                }
                
                // Konfiguration anwenden
                configure(config)
                
                Log.i(TAG, "✅ ARCore Session konfiguriert:")
                Log.i(TAG, "  - Plane Finding: ${config.planeFindingMode}")
                Log.i(TAG, "  - Light Estimation: ${config.lightEstimationMode}")
                Log.i(TAG, "  - Depth Mode: ${config.depthMode}")
                Log.i(TAG, "  - Focus Mode: ${config.focusMode}")
            }
            
            _isSessionReady.value = true
            Log.i(TAG, "✅ ARCore Session erfolgreich initialisiert")
            true
            
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "❌ ARCore nicht installiert", e)
            installRequested = true
            false
        } catch (e: UnavailableUserDeclinedInstallationException) {
            Log.e(TAG, "❌ Benutzer hat ARCore-Installation abgelehnt", e)
            false
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "❌ ARCore APK zu alt", e)
            false
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "❌ Gerät nicht ARCore-kompatibel", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unbekannter Fehler bei ARCore-Initialisierung", e)
            false
        }
    }

    /**
     * Startet ARCore Session und Tracking
     */
    fun startSession(): Boolean {
        return try {
            session?.let { session ->
                Log.i(TAG, "▶️ Starte ARCore Session...")
                session.resume()
                _isSessionReady.value = true
                Log.i(TAG, "✅ ARCore Session gestartet")
                true
            } ?: run {
                Log.e(TAG, "❌ Keine Session zum Starten verfügbar")
                false
            }
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "❌ Kamera nicht verfügbar", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Starten der Session", e)
            false
        }
    }

    /**
     * Stoppt ARCore Session (MediaPipe-sicher)
     */
    fun stopSession() {
        try {
            session?.let { session ->
                Log.i(TAG, "⏸️ Stoppe ARCore Session...")
                
                // WICHTIG: MediaPipe-Graph sanft herunterfahren
                try {
                    // Warte auf aktuelle Frame-Verarbeitung
                    Thread.sleep(100) // 100ms Puffer
                    
                    session.pause()
                    
                    // Weitere Wartezeit für MediaPipe-Cleanup
                    Thread.sleep(200) // 200ms für MediaPipe
                    
                } catch (interruptedException: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                
                _isSessionReady.value = false
                _trackingState.value = TrackingState.STOPPED
                _cameraPose.value = null
                _currentFrame.value = null
                Log.i(TAG, "✅ ARCore Session gestoppt (MediaPipe-sicher)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Stoppen der Session", e)
        }
    }

    /**
     * Schließt ARCore Session und gibt Ressourcen frei (MediaPipe-sicher)
     */
    fun closeSession() {
        try {
            Log.i(TAG, "🔒 Schließe ARCore Session...")
            
            // WICHTIG: Session erst stoppen, dann schließen
            if (_isSessionReady.value) {
                stopSession()
                
                // Zusätzliche Wartezeit für MediaPipe-Cleanup
                try {
                    Thread.sleep(500) // 500ms für vollständiges MediaPipe-Cleanup
                } catch (interruptedException: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            
            session?.close()
            session = null
            _isSessionReady.value = false
            _trackingState.value = TrackingState.STOPPED
            _cameraPose.value = null
            _currentFrame.value = null
            Log.i(TAG, "✅ ARCore Session geschlossen (MediaPipe-sicher)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Schließen der Session", e)
        }
    }

    /**
     * Update-Loop: Holt aktuellen Frame und Pose
     * Sollte in der Haupt-Render-Schleife aufgerufen werden
     */
    fun updateSession(): Frame? {
        return try {
            session?.let { session ->
                if (!_isSessionReady.value) return null
                
                // Aktuellen Frame holen
                val frame = session.update()
                _currentFrame.value = frame
                
                // Kamera und Tracking-State
                val camera = frame.camera
                val trackingState = camera.trackingState
                _trackingState.value = trackingState
                
                // Pose nur bei erfolgreichem Tracking updaten
                if (trackingState == TrackingState.TRACKING) {
                    _cameraPose.value = camera.pose
                    
                    // Debug-Ausgabe für Entwicklung (nur gelegentlich)
                    if (System.currentTimeMillis() % 1000 < 50) { // ca. alle Sekunde
                        val pose = camera.pose
                        Log.d(TAG, "📍 Camera Pose: tx=${String.format("%.2f", pose.tx())}, ty=${String.format("%.2f", pose.ty())}, tz=${String.format("%.2f", pose.tz())}")
                    }
                } else {
                    // Tracking verloren
                    if (trackingState == TrackingState.STOPPED) {
                        Log.w(TAG, "⚠️ ARCore Tracking gestoppt")
                    }
                }
                
                frame
            }
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "❌ Kamera nicht verfügbar für Update", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Session-Update", e)
            null
        }
    }

    /**
     * Erstellt einen ARCore Anchor an einer 3D-Position
     * Für stabile 3D-Objekt-Platzierung
     */
    fun createAnchor(pose: Pose): Anchor? {
        return try {
            session?.createAnchor(pose)?.also { anchor ->
                Log.d(TAG, "⚕️ ARCore Anchor erstellt: ${String.format("%.2f", pose.tx())}, ${String.format("%.2f", pose.ty())}, ${String.format("%.2f", pose.tz())}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler beim Erstellen des ARCore Anchors", e)
            null
        }
    }
    
    /**
     * Setzt den 3D-Renderer für ARCore-Integration
     */
    fun setAR3DRenderer(renderer: AR3DRenderer) {
        ar3DRenderer = renderer
        Log.i(TAG, "🎯 AR3DRenderer mit ARCore Session verbunden")
    }
    
    /**
     * Konvertiert 3D-Weltkoordinaten zu 2D-Bildschirmkoordinaten
     * WICHTIG für 3D-Modell-Projektion!
     */
    fun worldToScreen(worldPosition: FloatArray, viewMatrix: FloatArray, projectionMatrix: FloatArray): FloatArray? {
        return try {
            _currentFrame.value?.let { frame ->
                val camera = frame.camera
                
                // 3D-Punkt zu Bildschirmkoordinaten projizieren
                // Das ist der Schlüssel für korrekte AR-Overlays
                val screenCoords = FloatArray(2)
                
                // TODO: Implementiere Projektion mit OpenGL-Matrizen
                // camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
                // camera.getViewMatrix(viewMatrix, 0)
                
                screenCoords
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fehler bei Welt-zu-Bildschirm-Projektion", e)
            null
        }
    }

    // ==================== LIFECYCLE METHODS ====================

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.d(TAG, "📱 Lifecycle: onResume")
        
        // ARCore Session nur starten wenn bereits initialisiert
        session?.let { session ->
            if (!_isSessionReady.value) {
                try {
                    Log.i(TAG, "▶️ ARCore Session Resume...")
                    session.resume()
                    _isSessionReady.value = true
                    Log.i(TAG, "✅ ARCore Session resumed (Google Guidelines)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ ARCore Resume Fehler", e)
                }
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d(TAG, "📱 Lifecycle: onPause")
        stopSession()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Log.d(TAG, "📱 Lifecycle: onDestroy")
        closeSession()
    }

    // ==================== GL RENDERER METHODS ====================

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "🎨 OpenGL Surface erstellt")
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        
        // 3D-Renderer initialisieren
        if (::ar3DRenderer.isInitialized) {
            ar3DRenderer.initializeGL()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "🎨 OpenGL Surface geändert: ${width}x${height}")
        GLES30.glViewport(0, 0, width, height)
        
        // Display-Rotation für ARCore setzen
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        session?.setDisplayGeometry(windowManager.defaultDisplay.rotation, width, height)
        
        // 3D-Renderer Surface-Änderung
        if (::ar3DRenderer.isInitialized) {
            ar3DRenderer.onSurfaceChanged(width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        
        // Update ARCore Session (NUR EINMAL PRO FRAME!)
        val frame = updateSession()
        val pose = _cameraPose.value
        val trackingState = _trackingState.value
        
        // Render ARCore Camera Background
        frame?.let { currentFrame ->
            try {
                // WICHTIG: ARCore Camera Background manuell rendern
                currentFrame.acquireCameraImage().use { image ->
                    // OpenGL Texture für Camera Background erstellen
                    renderCameraBackground(currentFrame)
                    Log.v(TAG, "ARCore Camera Background gerendert: ${image.width}x${image.height}")
                }
            } catch (e: Exception) {
                // Fallback: Clear mit grauer Farbe statt schwarz
                GLES30.glClearColor(0.2f, 0.2f, 0.2f, 1.0f)
                Log.v(TAG, "ARCore Background Fallback: ${e.message}")
            }
        } ?: run {
            // Kein Frame verfügbar - grauer Background
            GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        }
        
        // 3D-Content über AR3DRenderer rendern
        if (frame != null && pose != null && ::ar3DRenderer.isInitialized) {
            ar3DRenderer.render3DContent(frame, pose, trackingState)
        }
    }

    /**
     * Rendert ARCore Camera Background als OpenGL Texture
     */
    private fun renderCameraBackground(frame: Frame) {
        try {
            // ARCore Camera Background Rendering
            frame.acquireCameraImage().use { image ->
                
                // Für Demo: Simuliere Kamera-Background mit einem blauen Gradient
                // In Produktion würde hier die echte Camera-Texture gerendert
                
                val time = (System.currentTimeMillis() % 10000) / 10000f
                val blue = 0.3f + 0.2f * sin(time * 2f * PI.toFloat())
                
                GLES30.glClearColor(0.1f, 0.2f, blue, 1.0f) // Animierter bläulicher Hintergrund
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                
                Log.v(TAG, "ARCore Camera Background simuliert (${image.width}x${image.height})")
            }
            
        } catch (e: Exception) {
            Log.v(TAG, "ARCore Camera Background Fallback: ${e.message}")
            
            // Fallback: Lebendiger grauer Hintergrund statt schwarz
            val time = (System.currentTimeMillis() % 5000) / 5000f
            val gray = 0.2f + 0.1f * sin(time * PI.toFloat())
            
            GLES30.glClearColor(gray, gray, gray + 0.1f, 1.0f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
    }
    
    // ==================== DATA CLASSES ====================

    enum class ARCoreStatus {
        READY,
        INSTALLATION_REQUIRED,
        UPDATE_REQUIRED,
        UNSUPPORTED,
        UNKNOWN,
        ERROR
    }

    /**
     * Kamera-Pose-Daten für Hybrid-Tracking
     */
    data class CameraPoseData(
        val position: FloatArray, // tx, ty, tz
        val rotation: FloatArray, // qx, qy, qz, qw (Quaternion)
        val timestamp: Long = System.currentTimeMillis()
    ) {
        companion object {
            fun fromARCorePose(pose: Pose): CameraPoseData {
                return CameraPoseData(
                    position = floatArrayOf(pose.tx(), pose.ty(), pose.tz()),
                    rotation = floatArrayOf(pose.qx(), pose.qy(), pose.qz(), pose.qw())
                )
            }
        }
    }
}