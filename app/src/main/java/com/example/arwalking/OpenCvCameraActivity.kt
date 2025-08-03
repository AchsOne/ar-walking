package com.example.arwalking

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.opencv.android.*
import org.opencv.core.*
import org.opencv.android.Utils
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import com.example.arwalking.components.ARInfoIsland
import com.example.arwalking.components.ARScanStatus
import com.example.arwalking.components.ExpandedARInfoIsland
import com.example.arwalking.components.rememberARScanStatus
import com.example.arwalking.ui.theme.ARWalkingTheme


class OpenCvCameraActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var openCvCameraView: JavaCameraView
    
    // Feature-Mapping Integration
    private lateinit var routeViewModel: RouteViewModel
    private lateinit var matchInfoText: TextView
    private lateinit var captureButton: Button
    private lateinit var navInfoButton: Button
    private lateinit var arInfoComposeView: ComposeView
    private var currentFrame: Mat? = null
    
    // AR Status State
    private var isARInitialized by mutableStateOf(false)
    private var currentMatches by mutableStateOf<List<FeatureMatchResult>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initDebug()

        setContentView(R.layout.activity_opencv_camera)

        // Views initialisieren
        openCvCameraView = findViewById(R.id.camera_view)
        matchInfoText = findViewById(R.id.match_info_text)
        captureButton = findViewById(R.id.capture_button)
        navInfoButton = findViewById(R.id.nav_info_button)
        
        // AR Info Island ComposeView erstellen und hinzufügen
        arInfoComposeView = ComposeView(this).apply {
            setContent {
                ARWalkingTheme {
                    ARInfoIslandOverlay()
                }
            }
        }
        
        // ComposeView zum Layout hinzufügen
        val rootLayout = findViewById<android.widget.RelativeLayout>(android.R.id.content)
        val layoutParams = android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
            topMargin = 60 // Platz für Status Bar
        }
        rootLayout.addView(arInfoComposeView, layoutParams)
        
        openCvCameraView.visibility = SurfaceView.VISIBLE
        openCvCameraView.setCvCameraViewListener(this)
        openCvCameraView.enableView()

        // ViewModel initialisieren
        routeViewModel = ViewModelProvider(this)[RouteViewModel::class.java]
        
        // Feature-Mapping initialisieren
        routeViewModel.initializeStorage(this)
        
        // AR als initialisiert markieren nach kurzer Verzögerung
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000)
            isARInitialized = true
        }
        
        // Feature-Navigation für aktuelles Gebäude laden
        val building = intent.getStringExtra("building") ?: "default_building"
        val floor = intent.getIntExtra("floor", 0)
        routeViewModel.loadFeatureNavigationRoute(this, building, floor)
        
        // UI Setup
        setupUI()
        observeFeatureMatches()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {}

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame?): Mat {
        val frame = inputFrame!!.gray()
        currentFrame = frame.clone() // Für Feature-Mapping speichern
        
        // Feature-Mapping verarbeiten
        routeViewModel.processFrameForFeatureMatching(frame)
        
        // Nur Kamera-Frame zurückgeben
        return frame
    }

    override fun onDestroy() {
        super.onDestroy()
        openCvCameraView.disableView()
    }
    
    /**
     * UI Setup für Feature-Mapping
     */
    private fun setupUI() {
        // Training functionality removed
        
        navInfoButton.setOnClickListener {
            showNavigationInfo()
        }
    }
    
    /**
     * Beobachtet Feature-Matches vom ViewModel
     */
    private fun observeFeatureMatches() {
        lifecycleScope.launch {
            routeViewModel.currentMatches.collect { matches ->
                runOnUiThread {
                    updateMatchInfo(matches)
                }
            }
        }
    }
    
    /**
     * AR Info Island Overlay Composable
     */
    @Composable
    private fun ARInfoIslandOverlay() {
        val matches by routeViewModel.currentMatches.collectAsState()
        
        // Update current matches state
        LaunchedEffect(matches) {
            currentMatches = matches
        }
        
        val landmarkCount = matches.size
        val bestConfidence = matches.maxOfOrNull { match -> match.confidence } ?: 0f
        val isTracking = matches.isNotEmpty()
        
        // Automatischer AR-Status basierend auf aktuellen Bedingungen
        val arStatus = rememberARScanStatus(
            isInitialized = isARInitialized,
            landmarkCount = landmarkCount,
            bestConfidence = bestConfidence,
            isTracking = isTracking
        )
        
        // Erweiterte Info Island mit mehr Details
        ExpandedARInfoIsland(
            scanStatus = arStatus,
            landmarkCount = landmarkCount,
            confidence = bestConfidence,
            isVisible = true
        )
    }

    /**
     * Aktualisiert Match-Informationen in der UI
     */
    private fun updateMatchInfo(matches: List<FeatureMatchResult>) {
        // Verstecke die alte TextView, da wir jetzt die AR Info Island verwenden
        matchInfoText.visibility = View.GONE
        
        if (matches.isEmpty()) {
            Log.d("OpenCvCamera", "Keine Landmarks erkannt")
        } else {
            val bestMatch = matches.first()
            Log.d("OpenCvCamera", "Erkannt: ${bestMatch.landmark.name} (${(bestMatch.confidence * 100).toInt()}%)")
        }
    }
    

    
    /**
     * Konvertiert OpenCV Mat zu Android Bitmap
     */
    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
    
    /**
     * Zeigt Navigation-Informationen an
     */
    private fun showNavigationInfo() {
        lifecycleScope.launch {
            routeViewModel.featureNavigationRoute.value?.let { route ->
                val info = buildString {
                    append("Navigation Route:\n")
                    append("Gesamtlänge: ${route.totalLength.toInt()}m\n")
                    append("Schritte: ${route.steps.size}\n\n")
                    
                    route.steps.take(3).forEach { step ->
                        append("${step.stepNumber}. ${step.instruction}\n")
                    }
                    
                    if (route.steps.size > 3) {
                        append("... und ${route.steps.size - 3} weitere Schritte")
                    }
                }
                
                runOnUiThread {
                    matchInfoText.text = info
                }
            } ?: run {
                runOnUiThread {
                    matchInfoText.text = "Keine Navigation verfügbar"
                }
            }
        }
    }
}
