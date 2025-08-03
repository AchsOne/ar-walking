package org.opencv.core

/**
 * OpenCV DMatch Stub
 */
data class DMatch(
    val queryIdx: Int,
    val trainIdx: Int,
    val imgIdx: Int,
    val distance: Float
) {
    constructor(queryIdx: Int, trainIdx: Int, distance: Float) : this(queryIdx, trainIdx, 0, distance)
}