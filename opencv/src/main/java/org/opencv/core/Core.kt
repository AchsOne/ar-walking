package org.opencv.core

/**
 * OpenCV Core Stub
 */
object Core {
    const val NORM_HAMMING = 2
    const val NORM_L2 = 4
    
    fun norm(src1: Mat, src2: Mat, normType: Int): Double {
        return Math.random() * 100.0
    }
}