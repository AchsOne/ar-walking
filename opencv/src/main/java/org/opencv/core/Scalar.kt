package org.opencv.core

/**
 * OpenCV Scalar Stub für lokale Entwicklung
 * Repräsentiert einen 4-dimensionalen Vektor für Farb- oder andere Werte
 */
class Scalar {
    val values: DoubleArray
    
    constructor(v0: Double) {
        values = doubleArrayOf(v0, 0.0, 0.0, 0.0)
    }
    
    constructor(v0: Double, v1: Double) {
        values = doubleArrayOf(v0, v1, 0.0, 0.0)
    }
    
    constructor(v0: Double, v1: Double, v2: Double) {
        values = doubleArrayOf(v0, v1, v2, 0.0)
    }
    
    constructor(v0: Double, v1: Double, v2: Double, v3: Double) {
        values = doubleArrayOf(v0, v1, v2, v3)
    }
    
    constructor(values: DoubleArray) {
        this.values = values.copyOf(4)
        // Fill remaining values with 0.0 if array is smaller than 4
        for (i in values.size until 4) {
            this.values[i] = 0.0
        }
    }
    
    /**
     * Gets the value at the specified index
     */
    operator fun get(index: Int): Double {
        return if (index in 0..3) values[index] else 0.0
    }
    
    /**
     * Creates a scalar with all values set to the same value
     */
    companion object {
        fun all(value: Double): Scalar {
            return Scalar(value, value, value, value)
        }
        
        val BLACK = Scalar(0.0, 0.0, 0.0)
        val WHITE = Scalar(255.0, 255.0, 255.0)
        val RED = Scalar(0.0, 0.0, 255.0)
        val GREEN = Scalar(0.0, 255.0, 0.0)
        val BLUE = Scalar(255.0, 0.0, 0.0)
    }
    
    override fun toString(): String {
        return "Scalar(${values.joinToString(", ")})"
    }
}