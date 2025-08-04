package org.opencv.core

/**
 * OpenCV Rect Stub
 * Represents a rectangle with integer coordinates
 */
data class Rect(
    var x: Int = 0,
    var y: Int = 0,
    var width: Int = 0,
    var height: Int = 0
) {
    
    constructor(point: Point, size: Size) : this(
        point.x.toInt(),
        point.y.toInt(),
        size.width.toInt(),
        size.height.toInt()
    )
    
    /**
     * Gets the top-left corner of the rectangle
     */
    fun tl(): Point {
        return Point(x.toDouble(), y.toDouble())
    }
    
    /**
     * Gets the bottom-right corner of the rectangle
     */
    fun br(): Point {
        return Point((x + width).toDouble(), (y + height).toDouble())
    }
    
    /**
     * Gets the size of the rectangle
     */
    fun size(): Size {
        return Size(width.toDouble(), height.toDouble())
    }
    
    /**
     * Gets the area of the rectangle
     */
    fun area(): Double {
        return width.toDouble() * height.toDouble()
    }
    
    /**
     * Checks if the rectangle is empty
     */
    fun empty(): Boolean {
        return width <= 0 || height <= 0
    }
    
    /**
     * Checks if a point is inside the rectangle
     */
    fun contains(point: Point): Boolean {
        return point.x >= x && point.y >= y && 
               point.x < x + width && point.y < y + height
    }
    
    /**
     * Creates a copy of the rectangle
     */
    fun clone(): Rect {
        return Rect(x, y, width, height)
    }
    
    override fun toString(): String {
        return "{$x, $y, ${width}x$height}"
    }
}