package org.opencv.android

import android.graphics.Bitmap
import org.opencv.core.Mat

/**
 * OpenCV Utils Stub für lokale Entwicklung
 */
object Utils {
    
    fun bitmapToMat(bitmap: Bitmap, mat: Mat) {
        // Simuliere Bitmap zu Mat Konvertierung
        mat.width = bitmap.width
        mat.height = bitmap.height
        mat.channels = 4 // RGBA
    }
    
    fun matToBitmap(mat: Mat, bitmap: Bitmap) {
        // Simuliere Mat zu Bitmap Konvertierung
        // In echter Implementierung würde hier die Konvertierung stattfinden
    }
    
    fun matToBitmap(mat: Mat): Bitmap {
        // Erstelle ein einfaches Bitmap als Platzhalter
        return Bitmap.createBitmap(
            mat.width.coerceAtLeast(1), 
            mat.height.coerceAtLeast(1), 
            Bitmap.Config.ARGB_8888
        )
    }
}