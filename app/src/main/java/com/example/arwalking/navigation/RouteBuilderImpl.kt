package com.example.arwalking.navigation

import android.util.Log
import com.example.arwalking.RouteData
import com.example.arwalking.RoutePart
import kotlin.math.*

/**
 * Converts JSON route data to meter-based coordinate system
 */
class RouteBuilderImpl : RouteBuilder {
    
    companion object {
        private const val TAG = "RouteBuilderImpl"
    }
    
    override fun buildFromJson(jsonRoute: RouteData): RoutePath? {
        return try {
            Log.d(TAG, "Building route from JSON with ${jsonRoute.route.path.size} path items")
            
            if (jsonRoute.route.path.isEmpty()) {
                Log.e(TAG, "Empty route path")
                return null
            }
            val pathItem = jsonRoute.route.path[0]
            val routeParts = pathItem.routeParts
            
            Log.d(TAG, "Processing ${routeParts.size} route parts")
            
            // Extract vertices and distances from JSON nodes
            val vertices = mutableListOf<Vec2>()
            val distances = mutableListOf<Float>()
            val maneuvers = mutableListOf<RouteManeuver>()
            
            var cumulativeDistance = 0f
            
            routeParts.forEachIndexed { partIndex, part ->
                val partVertices = extractVerticesFromPart(part)
                val partDistances = extractDistancesFromPart(part)
                
                if (partVertices.isNotEmpty()) {
                    // Add vertices for this part
                    val startIndex = vertices.size
                    
                    // Add first vertex if this is the first part
                    if (partIndex == 0 && partVertices.isNotEmpty()) {
                        vertices.add(partVertices.first())
                        distances.add(cumulativeDistance)
                    }
                    
                    // Add remaining vertices and accumulate distances
                    for (i in 1 until partVertices.size) {
                        vertices.add(partVertices[i])
                        
                        val segmentDistance = if (i - 1 < partDistances.size) {
                            partDistances[i - 1]
                        } else {
                            // Fallback: calculate Euclidean distance
                            val prev = partVertices[i - 1]
                            val curr = partVertices[i]
                            sqrt((curr.x - prev.x).pow(2) + (curr.y - prev.y).pow(2))
                        }
                        
                        cumulativeDistance += segmentDistance
                        distances.add(cumulativeDistance)
                    }
                    
                    // Create maneuver for this instruction
                    val maneuverType = classifyInstruction(part.instructionDe)
                    val hasLandmark = part.landmarks.isNotEmpty()
                    
                    // Calculate turn angle
                    val turnAngle = calculateTurnAngle(vertices, startIndex, partVertices.size)
                    
                    // Determine trigger distance based on instruction type
                    val triggerDistance = when (maneuverType) {
                        ManeuverType.LEFT, ManeuverType.RIGHT -> 15f
                        ManeuverType.U_TURN -> 20f
                        ManeuverType.LANDMARK_ACTION -> 10f
                        ManeuverType.DESTINATION -> 5f
                        ManeuverType.STRAIGHT -> 25f
                    }
                    
                    if (vertices.isNotEmpty()) {
                        maneuvers.add(RouteManeuver(
                            vertexIndex = vertices.size - 1, // Point to end of this segment
                            instruction = part.instructionDe,
                            maneuverType = maneuverType,
                            angle = turnAngle,
                            triggerDistance = triggerDistance,
                            hasLandmark = hasLandmark
                        ))
                    }
                }
            }
            
            if (vertices.isEmpty() || distances.isEmpty()) {
                Log.e(TAG, "No valid vertices or distances extracted")
                return null
            }
            
            val routePath = RoutePath(
                vertices = vertices,
                cumulativeLengths = distances,
                maneuvers = maneuvers,
                totalLength = distances.lastOrNull() ?: 0f
            )
            
            Log.d(TAG, "Route built: ${vertices.size} vertices, ${distances.last()}m total, ${maneuvers.size} maneuvers")
            maneuvers.forEachIndexed { i, maneuver ->
                Log.d(TAG, "Maneuver $i: ${maneuver.maneuverType} at vertex ${maneuver.vertexIndex}, trigger=${maneuver.triggerDistance}m")
            }
            
            routePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build route", e)
            null
        }
    }
    
    private fun extractVerticesFromPart(part: RoutePart): List<Vec2> {
        val vertices = mutableListOf<Vec2>()
        Log.d(TAG, "Extracting vertices from part with ${part.nodes?.size ?: 0} nodes")
        
        part.nodes?.forEach { nodeWrapper ->
            val node = nodeWrapper.node
            val x = node.x?.toFloatOrNull()
            val y = node.y?.toFloatOrNull()
            
            if (x != null && y != null) {
                vertices.add(Vec2(x, y))
            }
        }
        
        return vertices
    }
    
    private fun extractDistancesFromPart(part: RoutePart): List<Float> {
        val distances = mutableListOf<Float>()
        
        // Extract distances from edge data
        part.nodes?.forEach { nodeWrapper ->
            val edge = nodeWrapper.edge
            if (edge?.lengthInMeters != null) {
                try {
                    val length = edge.lengthInMeters.toFloatOrNull()
                    if (length != null && length > 0) {
                        distances.add(length)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not parse distance: ${e.message}")
                }
            }
        }
        
        return distances
    }
    
    private fun classifyInstruction(instruction: String): ManeuverType {
        val lower = instruction.lowercase()
        return when {
            lower.contains("links") || lower.contains("left") -> ManeuverType.LEFT
            lower.contains("rechts") || lower.contains("right") -> ManeuverType.RIGHT
            lower.contains("zurück") || lower.contains("umdrehen") || 
            lower.contains("u-turn") || lower.contains("uturn") -> ManeuverType.U_TURN
            lower.contains("gerade") || lower.contains("geradeaus") || 
            lower.contains("straight") -> ManeuverType.STRAIGHT
            lower.contains("tür") || lower.contains("door") || 
            lower.contains("durch") || lower.contains("through") -> ManeuverType.LANDMARK_ACTION
            lower.contains("ziel") || lower.contains("destination") || 
            lower.contains("liegt vor ihnen") -> ManeuverType.DESTINATION
            else -> ManeuverType.STRAIGHT
        }
    }
    
    private fun calculateTurnAngle(vertices: List<Vec2>, startIndex: Int, segmentLength: Int): Float {
        if (vertices.size < 3 || startIndex < 1 || startIndex + segmentLength >= vertices.size) {
            return 0f
        }
        
        try {
            // Get vectors for before and after this maneuver point
            val beforeStart = maxOf(0, startIndex - 1)
            val afterEnd = minOf(vertices.size - 1, startIndex + segmentLength)
            
            val beforeVec = vertices[startIndex] - vertices[beforeStart]
            val afterVec = vertices[afterEnd] - vertices[startIndex]
            
            if (beforeVec.length() == 0f || afterVec.length() == 0f) return 0f
            
            // Calculate angle between vectors using dot product
            val beforeNorm = beforeVec.normalize()
            val afterNorm = afterVec.normalize()
            
            val dotProduct = beforeNorm.x * afterNorm.x + beforeNorm.y * afterNorm.y
            val crossProduct = beforeNorm.x * afterNorm.y - beforeNorm.y * afterNorm.x
            
            val angle = atan2(crossProduct, dotProduct) * 180f / PI.toFloat()
            
            return angle
        } catch (e: Exception) {
            Log.w(TAG, "Could not calculate turn angle: ${e.message}")
            return 0f
        }
    }
}