package org.opencv.imgproc

import org.opencv.core.Mat

/**
 * OpenCV Imgproc Stub
 */
object Imgproc {
    const val COLOR_RGBA2GRAY = 11
    const val COLOR_BGR2GRAY = 6
    const val COLOR_RGB2GRAY = 7
    const val COLOR_GRAY2RGBA = 8
    
    fun cvtColor(src: Mat, dst: Mat, code: Int) {
        // Simuliere Farbkonvertierung
        when (code) {
            COLOR_RGBA2GRAY, COLOR_BGR2GRAY, COLOR_RGB2GRAY -> {
                dst.width = src.width
                dst.height = src.height
                dst.channels = 1 // Graustufen
            }
            COLOR_GRAY2RGBA -> {
                dst.width = src.width
                dst.height = src.height
                dst.channels = 4 // RGBA
            }
            else -> {
                dst.width = src.width
                dst.height = src.height
                dst.channels = src.channels
            }
        }
    }
    
    fun resize(src: Mat, dst: Mat, dsize: Any, fx: Double = 0.0, fy: Double = 0.0, interpolation: Int = 1) {
        // Simuliere Größenänderung
        dst.width = (src.width * if (fx > 0) fx else 1.0).toInt()
        dst.height = (src.height * if (fy > 0) fy else 1.0).toInt()
        dst.channels = src.channels
    }
}