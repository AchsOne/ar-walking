package com.example.arwalking

import android.util.Log

/**
 * AR-Tracking System für Positionierung und Orientierung
 */
class ARTrackingSystem {
    
    private val TAG = "ARTrackingSystem"
    
    init {
        Log.d(TAG, "ARTrackingSystem initialisiert")
    }
    
    /**
     * Setzt das Tracking zurück
     */
    fun resetTracking() {
        Log.d(TAG, "AR-Tracking zurückgesetzt")
    }
}