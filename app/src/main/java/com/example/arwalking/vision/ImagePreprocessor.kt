package com.example.arwalking.vision

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.CLAHE

/**
 * Erweiterte Bildvorverarbeitung für bessere Feature-Erkennung
 * Optimiert Bilder bevor sie für Landmark-Matching verwendet werden
 */
object ImagePreprocessor {
    
    private const val TAG = "ImagePreprocessor"
    
    // CLAHE für Kontrast-Enhancement
    private val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
    
    /**
     * Erweiterte Vorverarbeitung für bessere Feature-Erkennung
     * 
     * @param inputBitmap Das Eingangsbild
     * @param targetSize Zielgröße (null für Original-Größe)
     * @return Vorverarbeitetes Graustufenbild als Mat
     */
    fun preprocessForFeatureExtraction(
        inputBitmap: Bitmap,
        targetSize: Size? = Size(800.0, 600.0) // Optimale Größe für mobile Geräte
    ): Mat? {
        return try {
            // 1. Bitmap zu Mat konvertieren
            val colorMat = Mat()
            Utils.bitmapToMat(inputBitmap, colorMat)
            
            // 2. Größe anpassen (wichtig für Performance und Konsistenz)
            val resizedMat = Mat()
            if (targetSize != null) {
                Imgproc.resize(colorMat, resizedMat, targetSize, 0.0, 0.0, Imgproc.INTER_AREA)
                colorMat.release()
            } else {
                resizedMat.copyTo(colorMat)
                colorMat.release()
            }
            
            // 3. Zu Graustufen konvertieren
            val grayMat = Mat()
            Imgproc.cvtColor(resizedMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            resizedMat.release()
            
            // 4. Gaussian Blur um Rauschen zu reduzieren
            val blurredMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(3.0, 3.0), 0.0)
            grayMat.release()
            
            // 5. CLAHE für Kontrast-Enhancement (wichtig bei verschiedenen Lichtverhältnissen)
            val enhancedMat = Mat()
            clahe.apply(blurredMat, enhancedMat)
            blurredMat.release()
            
            Log.d(TAG, "Bild vorverarbeitet: ${enhancedMat.cols()}x${enhancedMat.rows()}")
            enhancedMat
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Bildvorverarbeitung: ${e.message}")
            null
        }
    }
    
    /**
     * Spezielle Vorverarbeitung für Kamera-Frames
     * Berücksichtigt typische Probleme von Live-Kamera-Bildern
     */
    fun preprocessCameraFrame(inputBitmap: Bitmap): Mat? {
        return try {
            val colorMat = Mat()
            Utils.bitmapToMat(inputBitmap, colorMat)
            
            // 1. Stabilisierung durch leichte Größenreduzierung
            val stabilizedMat = Mat()
            val targetSize = Size(
                (colorMat.cols() * 0.8).toInt().toDouble(), 
                (colorMat.rows() * 0.8).toInt().toDouble()
            )
            Imgproc.resize(colorMat, stabilizedMat, targetSize, 0.0, 0.0, Imgproc.INTER_AREA)
            colorMat.release()
            
            // 2. Graustufen
            val grayMat = Mat()
            Imgproc.cvtColor(stabilizedMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            stabilizedMat.release()
            
            // 3. Aggressive Rauschunterdrückung für Kamera-Frames
            val denoisedMat = Mat()
            Imgproc.bilateralFilter(grayMat, denoisedMat, 9, 75.0, 75.0)
            grayMat.release()
            
            // 4. Starke Kontrast-Enhancement (Kamera-Frames oft zu dunkel/hell)
            val strongClahe = Imgproc.createCLAHE(4.0, Size(8.0, 8.0))
            val enhancedMat = Mat()
            strongClahe.apply(denoisedMat, enhancedMat)
            denoisedMat.release()
            
            // 5. Schärfung durch Unsharp Masking
            val sharpenedMat = applySharpeningFilter(enhancedMat)
            enhancedMat.release()
            
            Log.d(TAG, "Kamera-Frame vorverarbeitet: ${sharpenedMat.cols()}x${sharpenedMat.rows()}")
            sharpenedMat
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei Kamera-Frame Vorverarbeitung: ${e.message}")
            null
        }
    }
    
    /**
     * Anwendung eines Schärfungsfilters (Unsharp Masking)
     */
    private fun applySharpeningFilter(inputMat: Mat): Mat {
        val blurred = Mat()
        val sharpened = Mat()
        val mask = Mat()
        
        try {
            // 1. Gaussian Blur
            Imgproc.GaussianBlur(inputMat, blurred, Size(5.0, 5.0), 0.0)
            
            // 2. Unsharp Mask erstellen
            org.opencv.core.Core.subtract(inputMat, blurred, mask)
            
            // 3. Schärfung anwenden
            org.opencv.core.Core.addWeighted(inputMat, 1.5, mask, 0.5, 0.0, sharpened)
            
            return sharpened
            
        } finally {
            blurred.release()
            mask.release()
        }
    }
    
    /**
     * Normalisiert ein Bild für konsistente Feature-Erkennung
     */
    fun normalizeImage(inputMat: Mat): Mat {
        val normalized = Mat()
        org.opencv.core.Core.normalize(inputMat, normalized, 0.0, 255.0, org.opencv.core.Core.NORM_MINMAX)
        return normalized
    }
    
    /**
     * Berechnet Bildqualitäts-Metriken
     */
    fun analyzeImageQuality(inputMat: Mat): ImageQualityMetrics {
        // Einfache Qualitäts-Metriken ohne komplexe OpenCV-Calls
        val mean = org.opencv.core.Core.mean(inputMat)
        
        // Berechne Kontrast als Standardabweichung
        val meanMat = Mat.zeros(inputMat.size(), inputMat.type())
        meanMat.setTo(mean)
        val diff = Mat()
        org.opencv.core.Core.subtract(inputMat, meanMat, diff)
        org.opencv.core.Core.multiply(diff, diff, diff)
        val variance = org.opencv.core.Core.mean(diff)
        val contrast = Math.sqrt(variance.`val`[0])
        
        // Einfache Schärfe-Messung
        val laplacian = Mat()
        Imgproc.Laplacian(inputMat, laplacian, org.opencv.core.CvType.CV_64F)
        // Absolute Werte für Schärfe-Berechnung
        val absLaplacian = Mat()
        org.opencv.core.Core.convertScaleAbs(laplacian, absLaplacian)
        val sharpnessValue = org.opencv.core.Core.mean(absLaplacian)
        absLaplacian.release()
        val sharpness = sharpnessValue.`val`[0]
        
        // Cleanup
        meanMat.release()
        diff.release()
        laplacian.release()
        
        return ImageQualityMetrics(
            brightness = mean.`val`[0],
            contrast = contrast,
            sharpness = sharpness
        )
    }
    
    data class ImageQualityMetrics(
        val brightness: Double,  // 0-255
        val contrast: Double,    // Höher = mehr Kontrast
        val sharpness: Double    // Höher = schärfer
    ) {
        fun isGoodQuality(): Boolean {
            return brightness in 50.0..200.0 &&  // Nicht zu dunkel/hell
                   contrast > 30.0 &&             // Ausreichend Kontrast
                   sharpness > 100.0              // Ausreichend scharf
        }
        
        override fun toString(): String {
            return "Helligkeit: ${brightness.toInt()}, Kontrast: ${contrast.toInt()}, Schärfe: ${sharpness.toInt()}"
        }
    }
}