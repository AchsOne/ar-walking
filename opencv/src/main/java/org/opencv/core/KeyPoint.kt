package org.opencv.core

/**
 * OpenCV KeyPoint Stub
 */
class KeyPoint {
    val pt: Point = Point()
    var size: Float = 0f
    var angle: Float = -1f
    var response: Float = 0f
    var octave: Int = 0
    var class_id: Int = -1
    
    constructor()
    
    constructor(x: Double, y: Double, size: Float, angle: Float = -1f, response: Float = 0f, octave: Int = 0, classId: Int = -1) {
        pt.x = x
        pt.y = y
        this.size = size
        this.angle = angle
        this.response = response
        this.octave = octave
        this.class_id = classId
    }
}