package com.example.arwalking.navigation

import android.util.Log
import kotlin.math.*

/**
 * Projects user position onto route polyline and calculates progress
 */
class MapMatcherImpl(
    private val routePath: RoutePath
) : MapMatcher {
    
    companion object {
        private const val TAG = "MapMatcherImpl"
    }
    
    private var lastSegmentIndex = 0 // Cache for optimization
    
    override fun project(worldPosition: Vec3): MapMatcher.Match {
        if (routePath.vertices.isEmpty()) {
            return MapMatcher.Match(
                s = 0f,
                closestPoint = Vec2(0f, 0f),
                distanceToRoute = Float.MAX_VALUE,
                distanceToNextManeuver = Float.MAX_VALUE,
                currentSegmentIndex = -1
            )
        }
        
        val pos2d = Vec2(worldPosition.x, worldPosition.z) // Use x,z for horizontal plane
        
        // Find closest point on polyline
        val projection = projectOntoPolyline(pos2d)
        
        // Calculate progress along route
        val progressDistance = projection.distanceAlongRoute
        val progress = routePath.getProgressAtDistance(progressDistance)
        
        // Find distance to next maneuver
        val distanceToNextManeuver = calculateDistanceToNextManeuver(progressDistance)
        
        return MapMatcher.Match(
            s = progress,
            closestPoint = projection.closestPoint,
            distanceToRoute = projection.distanceToClosest,
            distanceToNextManeuver = distanceToNextManeuver,
            currentSegmentIndex = projection.segmentIndex
        )
    }
    
    override fun getTotalRouteLength(): Float = routePath.totalLength
    
    private data class PolylineProjection(
        val closestPoint: Vec2,
        val distanceToClosest: Float,
        val distanceAlongRoute: Float,
        val segmentIndex: Int,
        val segmentProgress: Float // Progress within segment [0.0-1.0]
    )
    
    private fun projectOntoPolyline(position: Vec2): PolylineProjection {
        var bestProjection: PolylineProjection? = null
        var minDistance = Float.MAX_VALUE
        
        // Start search from last known segment for efficiency
        val searchOrder = getSearchOrder(lastSegmentIndex, routePath.vertices.size - 1)
        
        for (i in searchOrder) {
            if (i >= routePath.vertices.size - 1) continue
            
            val segmentStart = routePath.vertices[i]
            val segmentEnd = routePath.vertices[i + 1]
            
            val projection = projectOntoLineSegment(position, segmentStart, segmentEnd)
            
            if (projection.distance < minDistance) {
                minDistance = projection.distance
                
                // Calculate distance along entire route to this projection point
                val baseDistance = routePath.cumulativeLengths[i]
                val segmentLength = if (i < routePath.cumulativeLengths.size - 1) {
                    routePath.cumulativeLengths[i + 1] - routePath.cumulativeLengths[i]
                } else {
                    (segmentEnd - segmentStart).length()
                }
                
                val distanceAlongRoute = baseDistance + (projection.segmentProgress * segmentLength)
                
                bestProjection = PolylineProjection(
                    closestPoint = projection.point,
                    distanceToClosest = projection.distance,
                    distanceAlongRoute = distanceAlongRoute,
                    segmentIndex = i,
                    segmentProgress = projection.segmentProgress
                )
                
                lastSegmentIndex = i // Update cache
            }
        }
        
        return bestProjection ?: PolylineProjection(
            closestPoint = routePath.vertices[0],
            distanceToClosest = (position - routePath.vertices[0]).length(),
            distanceAlongRoute = 0f,
            segmentIndex = 0,
            segmentProgress = 0f
        )
    }
    
    private data class SegmentProjection(
        val point: Vec2,
        val distance: Float,
        val segmentProgress: Float
    )
    
    private fun projectOntoLineSegment(
        point: Vec2,
        segmentStart: Vec2,
        segmentEnd: Vec2
    ): SegmentProjection {
        val segmentVector = segmentEnd - segmentStart
        val segmentLength = segmentVector.length()
        
        if (segmentLength < 1e-6f) {
            // Degenerate segment
            return SegmentProjection(
                point = segmentStart,
                distance = (point - segmentStart).length(),
                segmentProgress = 0f
            )
        }
        
        val segmentDirection = segmentVector.normalize()
        val toPoint = point - segmentStart
        
        // Project point onto line segment
        val projectionLength = toPoint.x * segmentDirection.x + toPoint.y * segmentDirection.y
        val clampedLength = projectionLength.coerceIn(0f, segmentLength)
        
        val projectionPoint = segmentStart + (segmentDirection * clampedLength)
        val distance = (point - projectionPoint).length()
        val segmentProgress = if (segmentLength > 0) clampedLength / segmentLength else 0f
        
        return SegmentProjection(
            point = projectionPoint,
            distance = distance,
            segmentProgress = segmentProgress
        )
    }
    
    private fun getSearchOrder(lastIndex: Int, maxIndex: Int): List<Int> {
        val order = mutableListOf<Int>()
        
        // Start from last known position and search outward
        order.add(lastIndex)
        
        var radius = 1
        while (order.size <= maxIndex) {
            // Add segments within radius
            val before = lastIndex - radius
            val after = lastIndex + radius
            
            if (before >= 0) order.add(before)
            if (after <= maxIndex) order.add(after)
            
            if (before < 0 && after > maxIndex) break
            radius++
        }
        
        return order.distinct().filter { it >= 0 && it <= maxIndex }
    }
    
    private fun calculateDistanceToNextManeuver(currentDistanceAlongRoute: Float): Float {
        // Find next maneuver ahead of current position
        val nextManeuver = routePath.maneuvers.find { maneuver ->
            val maneuverDistance = if (maneuver.vertexIndex < routePath.cumulativeLengths.size) {
                routePath.cumulativeLengths[maneuver.vertexIndex]
            } else {
                routePath.totalLength
            }
            
            maneuverDistance > currentDistanceAlongRoute
        }
        
        return if (nextManeuver != null) {
            val maneuverDistance = if (nextManeuver.vertexIndex < routePath.cumulativeLengths.size) {
                routePath.cumulativeLengths[nextManeuver.vertexIndex]
            } else {
                routePath.totalLength
            }
            
            val distance = maneuverDistance - currentDistanceAlongRoute
            maxOf(0f, distance)
        } else {
            // No more maneuvers, distance to route end
            maxOf(0f, routePath.totalLength - currentDistanceAlongRoute)
        }
    }
}