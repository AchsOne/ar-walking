package org.opencv.core

/**
 * OpenCV CvType Stub
 * Contains constants for OpenCV data types
 */
object CvType {
    
    // 8-bit unsigned single channel
    const val CV_8UC1 = 0
    
    // 8-bit unsigned 3 channels
    const val CV_8UC3 = 16
    
    // 8-bit unsigned 4 channels
    const val CV_8UC4 = 24
    
    // 32-bit float single channel
    const val CV_32FC1 = 5
    
    // 32-bit float 3 channels
    const val CV_32FC3 = 21
    
    // 64-bit float single channel
    const val CV_64FC1 = 6
    
    // 64-bit float 3 channels
    const val CV_64FC3 = 22
    
    /**
     * Creates a type from depth and channels
     */
    fun makeType(depth: Int, channels: Int): Int {
        return depth + (channels - 1) * 8
    }
    
    /**
     * Gets the depth from a type
     */
    fun depth(type: Int): Int {
        return type and 7
    }
    
    /**
     * Gets the number of channels from a type
     */
    fun channels(type: Int): Int {
        return (type shr 3) + 1
    }
    
    /**
     * Gets the element size in bytes
     */
    fun elemSize(type: Int): Int {
        return when (depth(type)) {
            0 -> 1 // CV_8U
            1 -> 1 // CV_8S
            2 -> 2 // CV_16U
            3 -> 2 // CV_16S
            4 -> 4 // CV_32S
            5 -> 4 // CV_32F
            6 -> 8 // CV_64F
            else -> 1
        } * channels(type)
    }
}