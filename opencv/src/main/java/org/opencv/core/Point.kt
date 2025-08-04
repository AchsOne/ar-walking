package org.opencv.core

/**
 * OpenCV Point Stub
 * Represents a 2D point with double precision coordinates
 */
data class Point(
    var x: Double = 0.0,
    var y: Double = 0.0
) {
    
    constructor(x: Float, y: Float) : this(x.toDouble(), y.toDouble())
    
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())
    
    /**
     * Calculates the dot product of two points
     */
    fun dot(p: Point): Double {
        return x * p.x + y * p.y
    }
    
    /**
     * Checks if the point is inside a rectangle
     */
    fun inside(rect: Rect): Boolean {
        return x >= rect.x && y >= rect.y && 
               x < rect.x + rect.width && y < rect.y + rect.height
    }
    
    /**
     * Creates a copy of the point
     */
    fun clone(): Point {
        return Point(x, y)
    }
    
    /**
     * Sets the coordinates of the point
     */
    fun set(vals: DoubleArray) {
        if (vals.size >= 2) {
            x = vals[0]
            y = vals[1]
        }
    }
    
    override fun toString(): String {
        return "{$x, $y}"
    }
}