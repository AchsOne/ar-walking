package com.example.arwalking.navigation

import org.opencv.core.Mat

/**
 * Provides camera pose information for route-based navigation
 */
interface PoseProvider {
    data class Pose(
        val positionWorld: Vec3 = Vec3(0f, 0f, 0f), // World position in meters
        val yaw: Float = 0f,                         // Heading in degrees (0 = north)
        val velocity: Float = 0f,                    // Speed in m/s
        val quality: Float = 0f                      // Tracking quality [0.0-1.0]
    )
    
    fun start()
    fun stop()
    fun current(): Pose
}

/**
 * Projects user position onto route and calculates progress
 */
interface MapMatcher {
    data class Match(
        val s: Float,                    // Progress along route (0.0-1.0)
        val closestPoint: Vec2,          // Closest point on route polyline
        val distanceToRoute: Float,      // Perpendicular distance to route [m]
        val distanceToNextManeuver: Float, // Distance to next turn/landmark [m]
        val currentSegmentIndex: Int     // Current route segment index
    )
    
    fun project(worldPosition: Vec3): Match
    fun getTotalRouteLength(): Float
}

/**
 * Controls arrow visibility and direction based on route progress
 */
interface ArrowController {
    data class ArrowState(
        val visible: Boolean = false,
        val directionYaw: Float = 0f,    // Arrow direction in degrees
        val confidence: Float = 0f,      // Display confidence [0.0-1.0]
        val style: ArrowStyle = ArrowStyle.DIRECTION,
        val distanceToTrigger: Float = 0f,
        // Step-based cueing (computed from remaining distance and stride)
        val remainingSteps: Int = -1,
        val cueStage: CueStage = CueStage.NONE,
        val shouldVibrate: Boolean = false
    )
    
    enum class ArrowStyle {
        DIRECTION,    // Normal directional arrow
        CONFIRMATION, // Arrow shown after passing trigger point
        LANDMARK      // Arrow shown when landmark is recognized
    }
    
    enum class CueStage { NONE, EARLY, MID, LATE, URGENT }
    
    fun update(
        match: MapMatcher.Match,
        userYaw: Float,
        currentInstruction: String,
        hasLandmarkMatch: Boolean,
        matchedLandmarkIds: List<String>,
        speed: Float
    ): ArrowState
}

/**
 * Converts JSON route to meter-based coordinate system
 */
interface RouteBuilder {
    fun buildFromJson(jsonRoute: com.example.arwalking.RouteData): RoutePath?
}

/**
 * 3D vector for positions
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vec3(x * scalar, y * scalar, z * scalar)
    fun length() = kotlin.math.sqrt(x*x + y*y + z*z)
    fun normalize(): Vec3 {
        val len = length()
        return if (len > 0) Vec3(x/len, y/len, z/len) else Vec3(0f, 0f, 0f)
    }
}

/**
 * 2D vector for route coordinates
 */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    fun length() = kotlin.math.sqrt(x*x + y*y)
    fun normalize(): Vec2 {
        val len = length()
        return if (len > 0) Vec2(x/len, y/len) else Vec2(0f, 0f)
    }
}

/**
 * Route represented as polyline with meter distances
 */
data class RoutePath(
    val vertices: List<Vec2>,           // Route vertices in building coordinates
    val cumulativeLengths: List<Float>, // Cumulative distance at each vertex [m]
    val maneuvers: List<RouteManeuver>, // Turn points with instructions
    val totalLength: Float              // Total route length [m]
) {
    fun getProgressAtDistance(distance: Float): Float {
        return (distance / totalLength).coerceIn(0f, 1f)
    }
    
    fun getDistanceAtProgress(progress: Float): Float {
        return (progress * totalLength).coerceIn(0f, totalLength)
    }
}

/**
 * Maneuver/turn point along route
 */
data class RouteManeuver(
    val vertexIndex: Int,              // Index in route vertices
    val instruction: String,           // Turn instruction
    val maneuverType: ManeuverType,    // Type of maneuver
    val angle: Float,                  // Turn angle in degrees (positive = right)
    val triggerDistance: Float = 15f,  // Distance before maneuver to show arrow [m]
    val hasLandmark: Boolean = false   // Whether this maneuver has an associated landmark
)

enum class ManeuverType {
    STRAIGHT,
    LEFT,
    RIGHT,
    U_TURN,
    DESTINATION,
    LANDMARK_ACTION // For "Go through door", etc.
}