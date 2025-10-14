package com.example.arwalking.ar

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.arwalking.RouteViewModel
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Hybrid AR Tracker
 * 
 * Kombiniert das Beste aus beiden Welten:
 * - ARCore: 6DOF Kamera-Pose-Tracking für räumliche Stabilität
 * - AKAZE: Landmark-spezifische Feature-Erkennung für Navigation
 * 
 * Das Resultat: Stabile AR-Overlays mit präziser Landmark-Navigation
 */
class HybridARTracker(
    private val context: Context,
    private val routeViewModel: RouteViewModel,
    private val sessionManager: ARCoreSessionManager,
    private val renderer3D: AR3DRenderer
) {

    companion object {
        private const val TAG = "HybridARTracker"
        
        // Kalibrierungs-Parameter
        private const val CONFIDENCE_THRESHOLD = 0.7f // Mindest-AKAZE-Confidence für 3D-Platzierung
        private const val POSE_STABILITY_THRESHOLD = 0.1f // Meter für stabile Pose-Erkennung
        private const val LANDMARK_DISTANCE_ESTIMATE = 2.0f // Standard-Entfernung für Landmarks (2m)
        private const val TRACKING_FUSION_WEIGHT = 0.3f // Gewichtung ARCore vs AKAZE (30% AKAZE, 70% ARCore)
    }

    // Hybrid-Tracking State
    private val _hybridTrackingState = MutableStateFlow(HybridTrackingState.INITIALIZING)
    val hybridTrackingState: StateFlow<HybridTrackingState> = _hybridTrackingState.asStateFlow()
    
    // Kombinierte Pose-Daten (ARCore + AKAZE-Korrektur)
    private val _fusedPose = MutableStateFlow<FusedPoseData?>(null)
    val fusedPose: StateFlow<FusedPoseData?> = _fusedPose.asStateFlow()
    
    // Erkannte Landmarks mit 3D-Positionen
    private val _trackedLandmarks = MutableStateFlow<List<TrackedLandmark>>(emptyList())
    val trackedLandmarks: StateFlow<List<TrackedLandmark>> = _trackedLandmarks.asStateFlow()
    
    // Performance-Metriken
    private val _trackingMetrics = MutableStateFlow(TrackingMetrics())
    val trackingMetrics: StateFlow<TrackingMetrics> = _trackingMetrics.asStateFlow()
    
    // Last known state für Fusion
    private var lastARCorePose: Pose? = null
    private var lastValidFusion: FusedPoseData? = null
    private var trackingHistory = mutableListOf<TrackingDataPoint>()
    
    /**
     * Startet Hybrid-Tracking
     */
    fun startHybridTracking() {
        Log.i(TAG, "🚀 Starte Hybrid AR-Tracking (ARCore + AKAZE)")
        
        routeViewModel.viewModelScope.launch {
            try {
                // Kombiniere ARCore und AKAZE Datenströme
                combine(
                    sessionManager.cameraPose,
                    sessionManager.trackingState,
                    routeViewModel.currentMatches
                ) { arCorePose, trackingState, akazeMatches ->
                    
                    // Nur bei aktivem ARCore-Tracking
                    if (trackingState == TrackingState.TRACKING && arCorePose != null) {
                        processHybridTrackingUpdate(arCorePose, akazeMatches)
                    } else {
                        handleTrackingLost(trackingState)
                    }
                    
                }.collect { /* Collect um Flow aktiv zu halten */ }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fehler beim Hybrid-Tracking", e)
                _hybridTrackingState.value = HybridTrackingState.ERROR
            }
        }
    }
    
    /**
     * Verarbeitet kombinierte ARCore + AKAZE Updates
     */
    private suspend fun processHybridTrackingUpdate(
        arCorePose: Pose,
        akazeMatches: List<com.example.arwalking.RouteViewModel.LandmarkMatch>
    ) = withContext(Dispatchers.Default) {
        
        val currentTime = System.currentTimeMillis()
        
        // 1. ARCore Pose-Stabilität prüfen
        val poseStability = calculatePoseStability(arCorePose)
        
        // 2. AKAZE Landmarks zu 3D-Positionen konvertieren
        val landmarks3D = convertAKAZELandmarksTo3D(akazeMatches, arCorePose)
        
        // 3. Pose-Fusion: ARCore + AKAZE-Korrektur
        val fusedPose = fusePoses(arCorePose, landmarks3D, poseStability)
        
        // 4. 3D-Objekte platzieren basierend auf Fusion
        placeLandmarkObjects(landmarks3D, fusedPose)
        
        // 5. DEMO: Platziere immer mindestens einen Test-Pfeil für GLB-Demo
        placeDemoArrowForTesting(arCorePose, currentTime)
        
        // 6. State und Metriken updaten
        updateTrackingState(fusedPose, landmarks3D, currentTime)
        
        // 7. History für Stabilisierung
        updateTrackingHistory(arCorePose, akazeMatches.size, currentTime)
        
        Log.d(TAG, "🔄 Hybrid-Update: ARCore stable=${poseStability.isStable}, AKAZE landmarks=${landmarks3D.size}")
    }
    
    /**
     * Konvertiert AKAZE 2D-Landmark-Matches zu 3D-Weltpositionen
     * Nutzt ARCore Camera-Pose für 3D-Rekonstruktion
     */
    private fun convertAKAZELandmarksTo3D(
        akazeMatches: List<com.example.arwalking.RouteViewModel.LandmarkMatch>,
        arCorePose: Pose
    ): List<TrackedLandmark> {
        
        return akazeMatches
            .filter { it.confidence >= CONFIDENCE_THRESHOLD }
            .mapNotNull { match ->
                try {
                    // Geschätzte 3D-Position basierend auf Kamera-Pose und 2D-Position
                    val estimated3DPosition = estimate3DPositionFromCamera(
                        cameraPose = arCorePose,
                        landmarkMatch = match,
                        estimatedDistance = LANDMARK_DISTANCE_ESTIMATE
                    )
                    
                    TrackedLandmark(
                        id = match.landmark.id,
                        landmarkData = match.landmark,
                        worldPosition = estimated3DPosition,
                        confidence = match.confidence,
                        akazeMatchCount = match.matchCount,
                        distance = match.distance,
                        lastSeen = System.currentTimeMillis(),
                        isStable = isLandmarkPositionStable(match.landmark.id, estimated3DPosition)
                    )
                    
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Fehler bei 3D-Konvertierung für ${match.landmark.id}: ${e.message}")
                    null
                }
            }
    }
    
    /**
     * Schätzt 3D-Weltposition basierend auf Kamera-Pose und 2D-Landmark-Position
     * Vereinfachte Rekonstruktion für Demo (echte App würde Triangulation nutzen)
     */
    private fun estimate3DPositionFromCamera(
        cameraPose: Pose,
        landmarkMatch: com.example.arwalking.RouteViewModel.LandmarkMatch,
        estimatedDistance: Float
    ): FloatArray {
        
        // Kamera-Position und -orientierung
        val camX = cameraPose.tx()
        val camY = cameraPose.ty()
        val camZ = cameraPose.tz()
        
        // Vereinfachte Berechnung: Landmark vor der Kamera platzieren
        // In echter App: Nutze 2D-Bildschirmposition für präzise Richtung
        
        // Kamera-Blickrichtung (negative Z-Achse in Kamera-Koordinaten)
        val forwardX = 0f // TODO: Aus Kamera-Rotation berechnen
        val forwardY = 0f
        val forwardZ = -1f // Kamera schaut in negative Z-Richtung
        
        // Landmark-Position = Kamera + Richtung * Entfernung
        return floatArrayOf(
            camX + forwardX * estimatedDistance,
            camY + forwardY * estimatedDistance,
            camZ + forwardZ * estimatedDistance
        )
    }
    
    /**
     * Fusioniert ARCore-Pose mit AKAZE-basierten Korrekturen
     */
    private fun fusePoses(
        arCorePose: Pose,
        landmarks3D: List<TrackedLandmark>,
        poseStability: PoseStability
    ): FusedPoseData {
        
        // Basis: ARCore Pose (vertrauenswürdig für räumliches Tracking)
        var fusedPosition = floatArrayOf(arCorePose.tx(), arCorePose.ty(), arCorePose.tz())
        var fusedRotation = floatArrayOf(arCorePose.qx(), arCorePose.qy(), arCorePose.qz(), arCorePose.qw())
        
        // AKAZE-Korrektur nur bei stabilen und vertrauenswürdigen Landmarks
        val stableLandmarks = landmarks3D.filter { it.isStable && it.confidence >= CONFIDENCE_THRESHOLD }
        
        if (stableLandmarks.isNotEmpty() && poseStability.isStable) {
            
            // Gewichtete Korrektur basierend auf AKAZE-Landmarks
            val correction = calculateLandmarkBasedCorrection(stableLandmarks, arCorePose)
            
            // Sanfte Fusion mit konfigurierbarer Gewichtung
            fusedPosition = blendPositions(fusedPosition, correction.position, TRACKING_FUSION_WEIGHT)
            // Rotation bleibt meist bei ARCore (stabiler für 6DOF)
        }
        
        return FusedPoseData(
            position = fusedPosition,
            rotation = fusedRotation,
            arCoreConfidence = if (poseStability.isStable) 0.9f else 0.6f,
            akazeConfidence = if (stableLandmarks.isNotEmpty()) 
                stableLandmarks.map { it.confidence }.average().toFloat() else 0f,
            fusionWeight = TRACKING_FUSION_WEIGHT,
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Platziert 3D-Objekte basierend auf erkannten Landmarks
     */
    private fun placeLandmarkObjects(landmarks3D: List<TrackedLandmark>, fusedPose: FusedPoseData) {
        
        // Entferne alte 3D-Objekte
        renderer3D.clearAnchoredObjects()
        
        // Platziere 3D-Pfeile bei stabilen, vertrauenswürdigen Landmarks
        landmarks3D
            .filter { it.isStable && it.confidence >= CONFIDENCE_THRESHOLD }
            .forEach { landmark ->
                
                // Berechne Navigationsrichtung für dieses Landmark
                val navigationDirection = calculateNavigationDirection(landmark, fusedPose)
                
                // 3D-Pfeil mit ARCore Anchor platzieren (stabil im Raum!)
                val anchor = renderer3D.addARCoreAnchoredObject(
                    worldPosition = landmark.worldPosition,
                    navigationType = AR3DRenderer.NavigationType.ARROW,
                    scale = 0.15f * landmark.confidence, // Größe basierend auf Confidence
                    rotationY = navigationDirection,
                    alpha = 0.6f + (landmark.confidence * 0.4f) // 60-100% Alpha je nach Confidence
                )
                
                if (anchor != null) {
                    Log.d(TAG, "🎯 ARCore Anchor erstellt für Landmark ${landmark.id} - STABIL IM RAUM!")
                } else {
                    Log.w(TAG, "⚠️ ARCore Anchor konnte nicht erstellt werden für ${landmark.id}")
                }
                
                Log.d(TAG, "📍 3D-Pfeil platziert bei ${landmark.id} (${String.format("%.2f", landmark.confidence)})")
            }
    }
    
    /**
     * Berechnet Navigationsrichtung basierend auf Route und aktueller Position
     */
    private fun calculateNavigationDirection(landmark: TrackedLandmark, currentPose: FusedPoseData): Float {
        
        // Vereinfachte Berechnung für Demo
        // In echter App: Nutze Routen-Geometrie und nächsten Wegpunkt
        
        val currentRoute = routeViewModel.currentRoute.value
        val currentStep = routeViewModel.currentNavigationStep.value
        
        return when {
            // Spezifische Landmark-Richtungen
            landmark.id.contains("entrance") || landmark.id.contains("door") -> 0f    // Geradeaus
            landmark.id.contains("stairs") -> 45f                                      // Diagonal
            landmark.id.contains("elevator") -> 90f                                    // Rechts
            landmark.id.contains("exit") -> 270f                                       // Links
            
            // Route-basierte Richtung
            currentRoute != null && currentStep < currentRoute.steps.size -> {
                // Berechne Richtung zum nächsten Schritt
                val progress = currentStep.toFloat() / currentRoute.steps.size
                progress * 360f // Vereinfacht
            }
            
            else -> 0f // Standard: Geradeaus
        }
    }
    
    // ==================== HILFSMETHODEN ====================
    
    private fun calculatePoseStability(pose: Pose): PoseStability {
        val lastPose = lastARCorePose
        
        if (lastPose == null) {
            lastARCorePose = pose
            return PoseStability(isStable = false, translationDelta = Float.MAX_VALUE, rotationDelta = Float.MAX_VALUE)
        }
        
        // Translationsunterschied
        val deltaX = pose.tx() - lastPose.tx()
        val deltaY = pose.ty() - lastPose.ty()
        val deltaZ = pose.tz() - lastPose.tz()
        val translationDelta = sqrt(deltaX*deltaX + deltaY*deltaY + deltaZ*deltaZ)
        
        // Rotationsunterschied (vereinfacht)
        val rotationDelta = abs(pose.qw() - lastPose.qw()) // Vereinfacht, nur W-Komponente
        
        lastARCorePose = pose
        
        return PoseStability(
            isStable = translationDelta < POSE_STABILITY_THRESHOLD && rotationDelta < 0.1f,
            translationDelta = translationDelta,
            rotationDelta = rotationDelta
        )
    }
    
    private fun calculateLandmarkBasedCorrection(
        landmarks: List<TrackedLandmark>, 
        arCorePose: Pose
    ): PoseCorrection {
        // Vereinfachte Implementierung
        // In echter App: Nutze bekannte Landmark-Positionen für präzise Korrektur
        
        return PoseCorrection(
            position = floatArrayOf(0f, 0f, 0f), // Keine Korrektur für Demo
            confidence = landmarks.map { it.confidence }.average().toFloat()
        )
    }
    
    private fun blendPositions(pos1: FloatArray, pos2: FloatArray, weight: Float): FloatArray {
        return floatArrayOf(
            pos1[0] * (1f - weight) + pos2[0] * weight,
            pos1[1] * (1f - weight) + pos2[1] * weight,
            pos1[2] * (1f - weight) + pos2[2] * weight
        )
    }
    
    private fun isLandmarkPositionStable(landmarkId: String, position: FloatArray): Boolean {
        // Vereinfacht: Prüfe ob Landmark-Position über Zeit stabil ist
        // In echter App: Nutze Positions-History
        return true // Für Demo
    }
    
    private fun updateTrackingState(fusedPose: FusedPoseData, landmarks: List<TrackedLandmark>, timestamp: Long) {
        _fusedPose.value = fusedPose
        _trackedLandmarks.value = landmarks
        
        // Update Tracking-State basierend auf Qualität
        _hybridTrackingState.value = when {
            fusedPose.arCoreConfidence > 0.8f && landmarks.size >= 2 -> HybridTrackingState.TRACKING_EXCELLENT
            fusedPose.arCoreConfidence > 0.6f && landmarks.size >= 1 -> HybridTrackingState.TRACKING_GOOD
            fusedPose.arCoreConfidence > 0.4f -> HybridTrackingState.TRACKING_LIMITED
            else -> HybridTrackingState.TRACKING_LOST
        }
        
        // Update Performance-Metriken
        val metrics = _trackingMetrics.value
        _trackingMetrics.value = metrics.copy(
            totalFrames = metrics.totalFrames + 1,
            akazeDetections = metrics.akazeDetections + landmarks.size,
            averageConfidence = (metrics.averageConfidence + fusedPose.arCoreConfidence) / 2f,
            lastUpdateTime = timestamp
        )
    }
    
    private fun updateTrackingHistory(pose: Pose, akazeCount: Int, timestamp: Long) {
        val dataPoint = TrackingDataPoint(
            arCorePose = pose,
            akazeCount = akazeCount,
            timestamp = timestamp
        )
        
        trackingHistory.add(dataPoint)
        
        // Behalte nur letzte 100 Frames für Performance
        if (trackingHistory.size > 100) {
            trackingHistory.removeAt(0)
        }
    }
    
    /**
     * DEMO: Platziert einen Test-Pfeil für GLB-Model-Demo
     * Wird alle 10 Sekunden aufgerufen um sicherzustellen dass ein Pfeil sichtbar ist
     */
    private fun placeDemoArrowForTesting(arCorePose: Pose, currentTime: Long) {
        // Nur alle 10 Sekunden einen Demo-Pfeil platzieren
        if (currentTime % 10000 < 100) { // ca. alle 10 Sekunden
            try {
                // Platziere Pfeil 2 Meter vor der Kamera
                val demoPosition = floatArrayOf(
                    arCorePose.tx(),
                    arCorePose.ty(),
                    arCorePose.tz() - 2.0f // 2 Meter in negative Z-Richtung (vor Kamera)
                )
                
                // Erstelle ARCore Anchor für stabilen Demo-Pfeil
                val anchor = renderer3D.addARCoreAnchoredObject(
                    worldPosition = demoPosition,
                    navigationType = AR3DRenderer.NavigationType.ARROW,
                    scale = 0.2f, // 20cm groß
                    rotationY = 0f, // Zeigt geradeaus
                    alpha = 0.9f // Fast vollständig sichtbar
                )
                
                if (anchor != null) {
                    Log.i(TAG, "🎯 DEMO-Pfeil platziert: 2m vor Kamera (GLB-Model Test)")
                } else {
                    Log.w(TAG, "⚠️ Demo-Pfeil konnte nicht platziert werden")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Fehler beim Platzieren des Demo-Pfeils", e)
            }
        }
    }
    
    private fun handleTrackingLost(trackingState: TrackingState) {
        Log.w(TAG, "⚠️ ARCore Tracking verloren: $trackingState")
        
        _hybridTrackingState.value = when (trackingState) {
            TrackingState.PAUSED -> HybridTrackingState.TRACKING_PAUSED
            TrackingState.STOPPED -> HybridTrackingState.TRACKING_STOPPED
            else -> HybridTrackingState.TRACKING_LOST
        }
        
        // Verwende letzte bekannte Fusion für kurze Zeit
        lastValidFusion?.let { lastFusion ->
            val timeDiff = System.currentTimeMillis() - lastFusion.timestamp
            if (timeDiff < 2000) { // 2 Sekunden Toleranz
                _fusedPose.value = lastFusion.copy(
                    arCoreConfidence = 0.3f, // Reduzierte Confidence
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }
    
    // ==================== DATA CLASSES ====================
    
    enum class HybridTrackingState {
        INITIALIZING,
        TRACKING_EXCELLENT,  // ARCore + AKAZE beide stabil
        TRACKING_GOOD,       // ARCore stabil, AKAZE teilweise
        TRACKING_LIMITED,    // Nur ARCore, kein AKAZE
        TRACKING_PAUSED,
        TRACKING_STOPPED,
        TRACKING_LOST,
        ERROR
    }
    
    data class FusedPoseData(
        val position: FloatArray,        // [x, y, z] fusioniert
        val rotation: FloatArray,        // [qx, qy, qz, qw] meist ARCore
        val arCoreConfidence: Float,     // ARCore Tracking-Qualität
        val akazeConfidence: Float,      // AKAZE Detection-Qualität
        val fusionWeight: Float,         // Gewichtung der Fusion
        val timestamp: Long
    )
    
    data class TrackedLandmark(
        val id: String,
        val landmarkData: com.example.arwalking.RouteLandmarkData,
        val worldPosition: FloatArray,   // 3D-Position in ARCore-Koordinaten
        val confidence: Float,           // AKAZE Confidence
        val akazeMatchCount: Int,        // Anzahl Feature-Matches
        val distance: Float,             // Geschätzte Entfernung
        val lastSeen: Long,              // Letztes Erkennungszeit
        val isStable: Boolean            // Position über Zeit stabil
    )
    
    data class PoseStability(
        val isStable: Boolean,
        val translationDelta: Float,
        val rotationDelta: Float
    )
    
    data class PoseCorrection(
        val position: FloatArray,
        val confidence: Float
    )
    
    data class TrackingMetrics(
        val totalFrames: Long = 0,
        val akazeDetections: Long = 0,
        val averageConfidence: Float = 0f,
        val lastUpdateTime: Long = 0
    )
    
    data class TrackingDataPoint(
        val arCorePose: Pose,
        val akazeCount: Int,
        val timestamp: Long
    )
}