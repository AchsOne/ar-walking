package org.opencv.core

/**
 * OpenCV Size Stub
 * Represents a size with double precision dimensions
 */
data class Size(
    var width: Double = 0.0,
    var height: Double = 0.0
) {
    
    constructor(width: Float, height: Float) : this(width.toDouble(), height.toDouble())
    
    constructor(width: Int, height: Int) : this(width.toDouble(), height.toDouble())
    
    /**
     * Gets the area of the size
     */
    fun area(): Double {
        return width * height
    }
    
    /**
     * Checks if the size is empty
     */
    fun empty(): Boolean {
        return width <= 0.0 || height <= 0.0
    }
    
    /**
     * Creates a copy of the size
     */
    fun clone(): Size {
        return Size(width, height)
    }
    
    /**
     * Sets the dimensions of the size
     */
    fun set(vals: DoubleArray) {
        if (vals.size >= 2) {
            width = vals[0]
            height = vals[1]
        }
    }
    
    override fun toString(): String {
        return "${width}x$height"
    }
}