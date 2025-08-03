package org.opencv.core

/**
 * OpenCV KeyPoint Stub
 */
data class KeyPoint(
    val x: Float,
    val y: Float,
    val size: Float,
    val angle: Float = -1f,
    val response: Float = 0f,
    val octave: Int = 0,
    val classId: Int = -1
) {
    constructor(x: Float, y: Float, size: Float) : this(x, y, size, -1f, 0f, 0, -1)
}