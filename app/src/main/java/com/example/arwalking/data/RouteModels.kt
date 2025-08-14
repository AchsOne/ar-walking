package com.example.arwalking.data

import com.google.gson.annotations.SerializedName

/**
 * Main route data structure matching the JSON format
 */
data class RouteData(
    val route: Route
)

data class Route(
    val path: List<PathElement>
)

data class PathElement(
    val xmlName: String,
    val levelInfo: LevelInfo,
    val xmlNameEn: String,
    val xmlNameDe: String,
    val xmlFile: String,
    val routeParts: List<RoutePart>
)

data class LevelInfo(
    val storeyNameEn: String,
    val storeyName: String,
    val width: String,
    val storeyNameDe: String,
    val id: String,
    val calculatedxratio: String,
    val storey: String,
    val height: String,
    val mapfile: String,
    val calculatedyratio: String,
    val transformationMatrix: TransformationMatrix
)

data class TransformationMatrix(
    @SerializedName("EPSG3857") val epsg3857: EPSG3857,
    val wlon: Double,
    val ylon: Double,
    val xlon: Double,
    val ylat: Double,
    val xlat: Double,
    val wlat: Double
)

data class EPSG3857(
    val wlon: Double,
    val ylon: Double,
    val xlon: Double,
    val ylat: Double,
    val xlat: Double,
    val wlat: Double
)

data class RoutePart(
    val iconID: String,
    val nodes: List<NodeElement>,
    val instruction: String,
    val instructionEn: String,
    val landmarks: List<Landmark>,
    val landmarkFromInstruction: String? = null,
    val instructionDe: String
)

data class NodeElement(
    val node: Node,
    val edge: Edge? = null
)

data class Node(
    val isdestination: String? = null,
    val name: String? = null,
    val x: String,
    val y: String,
    val id: String,
    val label: String,
    val type: String,
    val lsf: String? = null,
    val roomid: String? = null,
    val oldroomid: String? = null
)

data class Edge(
    val dx: String,
    val cx: String,
    val dy: String,
    val bx: String,
    val cy: String,
    val ax: String,
    val by: String,
    val lengthInMeters: String,
    val ay: String,
    val id: String,
    val type: String
)

data class Landmark(
    val nameDe: String,
    val x: String,
    val y: String,
    val nameEn: String,
    val id: String,
    val type: String,
    val lsf: String? = null
)

/**
 * Processed navigation step for the app
 */
data class NavigationStep(
    val id: String,
    val instruction: String,
    val instructionDe: String,
    val landmarkId: String?,
    val arrowDirection: ArrowDirection,
    val landmarks: List<Landmark>,
    val position: Position
)

data class Position(
    val x: Float,
    val y: Float
)

enum class ArrowDirection {
    FORWARD,
    LEFT,
    RIGHT,
    BACK,
    UP,
    DOWN,
    NONE;
    
    companion object {
        fun fromInstruction(instruction: String): ArrowDirection {
            val lowerInstruction = instruction.lowercase()
            return when {
                lowerInstruction.contains("links") || lowerInstruction.contains("left") -> LEFT
                lowerInstruction.contains("rechts") || lowerInstruction.contains("right") -> RIGHT
                lowerInstruction.contains("zurück") || lowerInstruction.contains("back") -> BACK
                lowerInstruction.contains("hoch") || lowerInstruction.contains("up") || 
                lowerInstruction.contains("treppe") || lowerInstruction.contains("stairs") -> UP
                lowerInstruction.contains("runter") || lowerInstruction.contains("down") -> DOWN
                lowerInstruction.contains("gerade") || lowerInstruction.contains("straight") ||
                lowerInstruction.contains("durch") || lowerInstruction.contains("through") -> FORWARD
                else -> FORWARD
            }
        }
    }
}

/**
 * Configuration for the AR navigation system
 */
data class ARNavigationConfig(
    val picturesDir: String = "images",
    val cacheDir: String = "feature_cache",
    val feature: FeatureConfig = FeatureConfig(),
    val matcher: MatcherConfig = MatcherConfig(),
    val ransac: RansacConfig = RansacConfig(),
    val topK: Int = 10,
    val thresholds: ThresholdConfig = ThresholdConfig(),
    val frameResizeWidth: Int = 960,
    val stabilization: StabilizationConfig = StabilizationConfig(),
    val arrow: ArrowConfig = ArrowConfig()
)

data class FeatureConfig(
    val type: String = "ORB",
    val nFeatures: Int = 2000,
    val scaleFactor: Float = 1.2f,
    val nLevels: Int = 8
)

data class MatcherConfig(
    val ratio: Float = 0.75f
)

data class RansacConfig(
    val reprojThreshold: Float = 3.0f,
    val maxIters: Int = 2000,
    val confidence: Float = 0.995f
)

data class ThresholdConfig(
    val match: Float = 0.35f,
    val promote: Float = 0.50f,
    val demote: Float = 0.25f
)

data class StabilizationConfig(
    val emaAlpha: Float = 0.2f,
    val minStableFrames: Int = 3
)

data class ArrowConfig(
    val scale: Float = 0.1f,
    val offset: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),
    val occlusion: Boolean = true,
    val instantPlacement: Boolean = true
)

/**
 * Status information for the navigation system
 */
data class NavigationStatus(
    val currentLandmarkId: String? = null,
    val currentStepId: String? = null,
    val matchConfidence: Float = 0f,
    val isTracking: Boolean = false,
    val trackingQuality: TrackingQuality = TrackingQuality.NONE
)

enum class TrackingQuality {
    NONE,
    INSUFFICIENT,
    SUFFICIENT,
    NORMAL
}

/**
 * Feature matching result
 */
data class MatchResult(
    val landmarkId: String,
    val confidence: Float,
    val inliers: Int,
    val keypoints: Int,
    val homography: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MatchResult

        if (landmarkId != other.landmarkId) return false
        if (confidence != other.confidence) return false
        if (inliers != other.inliers) return false
        if (keypoints != other.keypoints) return false
        if (homography != null) {
            if (other.homography == null) return false
            if (!homography.contentEquals(other.homography)) return false
        } else if (other.homography != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = landmarkId.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + inliers
        result = 31 * result + keypoints
        result = 31 * result + (homography?.contentHashCode() ?: 0)
        return result
    }
}