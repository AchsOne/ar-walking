package com.example.arwalking

import android.graphics.PointF
import android.graphics.RectF
import com.example.arwalking.ar.SimpleARRenderer

/**
 * Ergebnis eines Feature-Matching-Vorgangs mit AR-Pose-Informationen
 */
data class FeatureMatchResult(
    val landmarkId: String,
    val confidence: Float,
    val matchCount: Int,
    val position: PointF,
    val boundingBox: RectF? = null,
    val distance: Float? = null,
    val arPose: SimpleARRenderer.SimpleARPose? = null,  // 3D-Pose für AR-Tracking
    val arObject: SimpleARRenderer.SimpleAR3DObject? = null  // 3D-Objekt-Position
)