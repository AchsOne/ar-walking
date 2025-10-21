package com.example.arwalking.navigation

import android.util.Log
import kotlin.math.*

/**
 * Controls arrow visibility and direction based on route progress and trigger windows
 */
class ArrowControllerImpl(
    private val routePath: RoutePath
) : ArrowController {
    
    companion object {
        private const val TAG = "ArrowControllerImpl"
        private const val CONFIRMATION_TIMEOUT_MS = 10000L // 10 seconds
        private const val SPEED_FACTOR = 2.5f // Dynamic trigger distance multiplier
        private const val MIN_TRIGGER_DISTANCE = 8f // Minimum trigger distance
        private const val MAX_TRIGGER_DISTANCE = 25f // Maximum trigger distance
        private const val ANGLE_THRESHOLD = 120f // Angle threshold for showing arrow (increased for testing)
    }
    
    private var currentState = ArrowController.ArrowState()
    private var confirmedManeuvers = mutableSetOf<Int>() // Maneuvers that have been confirmed
    private var triggerTime = 0L
    private var lastManeuverIndex = -1
    
    override fun update(
        match: MapMatcher.Match,
        userYaw: Float,
        currentInstruction: String,
        hasLandmarkMatch: Boolean,
        speed: Float
    ): ArrowController.ArrowState {
        val now = System.currentTimeMillis()
        
        // Find current and next maneuver
        val currentManeuver = findCurrentManeuver(match)
        val nextManeuver = findNextManeuver(match)
        
        if (nextManeuver == null) {
            // No more maneuvers - hide arrow or show destination
            currentState = ArrowController.ArrowState(
                visible = false,
                directionYaw = 0f,
                confidence = 0f
            )
            return currentState
        }
        
        // Calculate dynamic trigger distance based on speed
        val dynamicTriggerDistance = calculateDynamicTriggerDistance(speed, nextManeuver.triggerDistance)
        
        val distanceToManeuver = match.distanceToNextManeuver
        val isInTriggerWindow = distanceToManeuver <= dynamicTriggerDistance
        val isConfirmationMode = confirmedManeuvers.contains(getManeuverIndex(nextManeuver))
        
        val distanceStr = String.format("%.1f", distanceToManeuver)
        val triggerStr = String.format("%.1f", dynamicTriggerDistance)
        Log.d(TAG, "Update: maneuver=${nextManeuver.maneuverType}, distance=${distanceStr}m, " +
                "trigger=${triggerStr}m, inWindow=$isInTriggerWindow, confirmed=$isConfirmationMode")
        
        // Handle state transitions
        val newState = when {
            // Case 1: Landmark-based instruction with active landmark match
            nextManeuver.hasLandmark && hasLandmarkMatch -> {
                handleLandmarkBasedArrow(nextManeuver, userYaw, hasLandmarkMatch)
            }
            
            // Case 2: Distance-based instruction in trigger window
            isInTriggerWindow && !nextManeuver.hasLandmark -> {
                handleDistanceBasedArrow(nextManeuver, userYaw, distanceToManeuver, dynamicTriggerDistance, speed)
            }
            
            // Case 3: Confirmation mode after passing trigger point
            isConfirmationMode && (now - triggerTime) < CONFIRMATION_TIMEOUT_MS -> {
                handleConfirmationArrow(nextManeuver, userYaw)
            }
            
            // Case 4: Hide arrow
            else -> {
                ArrowController.ArrowState(
                    visible = false,
                    directionYaw = 0f,
                    confidence = 0f
                )
            }
        }
        
        // Check for maneuver confirmation (user passed the maneuver point)
        checkManeuverConfirmation(match, nextManeuver, now)
        
        currentState = newState
        return newState
    }
    
    private fun findCurrentManeuver(match: MapMatcher.Match): RouteManeuver? {
        val currentDistance = match.s * routePath.totalLength
        
        return routePath.maneuvers.findLast { maneuver ->
            val maneuverDistance = if (maneuver.vertexIndex < routePath.cumulativeLengths.size) {
                routePath.cumulativeLengths[maneuver.vertexIndex]
            } else {
                routePath.totalLength
            }
            maneuverDistance <= currentDistance
        }
    }
    
    private fun findNextManeuver(match: MapMatcher.Match): RouteManeuver? {
        val currentDistance = match.s * routePath.totalLength
        
        return routePath.maneuvers.find { maneuver ->
            val maneuverDistance = if (maneuver.vertexIndex < routePath.cumulativeLengths.size) {
                routePath.cumulativeLengths[maneuver.vertexIndex]
            } else {
                routePath.totalLength
            }
            
            val maneuverIndex = getManeuverIndex(maneuver)
            maneuverDistance > currentDistance && !confirmedManeuvers.contains(maneuverIndex)
        }
    }
    
    private fun calculateDynamicTriggerDistance(speed: Float, baseTriggerDistance: Float): Float {
        val speedBasedDistance = speed * SPEED_FACTOR
        val dynamicDistance = maxOf(baseTriggerDistance, speedBasedDistance)
        return dynamicDistance.coerceIn(MIN_TRIGGER_DISTANCE, MAX_TRIGGER_DISTANCE)
    }
    
    private fun handleLandmarkBasedArrow(
        maneuver: RouteManeuver,
        userYaw: Float,
        hasLandmarkMatch: Boolean
    ): ArrowController.ArrowState {
        val directionYaw = calculateManeuverDirection(maneuver)
        val confidence = if (hasLandmarkMatch) 0.9f else 0.6f
        
        return ArrowController.ArrowState(
            visible = true,
            directionYaw = directionYaw,
            confidence = confidence,
            style = ArrowController.ArrowStyle.LANDMARK,
            distanceToTrigger = 0f
        )
    }
    
    private fun handleDistanceBasedArrow(
        maneuver: RouteManeuver,
        userYaw: Float,
        distanceToManeuver: Float,
        triggerDistance: Float,
        speed: Float
    ): ArrowController.ArrowState {
        val directionYaw = calculateManeuverDirection(maneuver)
        val angleDiff = kotlin.math.abs(normalizeAngle(directionYaw - userYaw))
        
        // Only show arrow if user is roughly facing the right direction
        val shouldShow = angleDiff <= ANGLE_THRESHOLD
        
        if (shouldShow && triggerTime == 0L) {
            triggerTime = System.currentTimeMillis()
        }
        
        val confidence = calculateDistanceBasedConfidence(distanceToManeuver, triggerDistance, angleDiff)
        
        return ArrowController.ArrowState(
            visible = shouldShow,
            directionYaw = directionYaw,
            confidence = confidence,
            style = ArrowController.ArrowStyle.DIRECTION,
            distanceToTrigger = distanceToManeuver
        )
    }
    
    private fun handleConfirmationArrow(
        maneuver: RouteManeuver,
        userYaw: Float
    ): ArrowController.ArrowState {
        val directionYaw = calculateManeuverDirection(maneuver)
        
        return ArrowController.ArrowState(
            visible = true,
            directionYaw = directionYaw,
            confidence = 0.8f,
            style = ArrowController.ArrowStyle.CONFIRMATION,
            distanceToTrigger = 0f
        )
    }
    
    private fun calculateManeuverDirection(maneuver: RouteManeuver): Float {
        return when (maneuver.maneuverType) {
            ManeuverType.LEFT -> 270f
            ManeuverType.RIGHT -> 90f
            ManeuverType.U_TURN -> 180f
            ManeuverType.STRAIGHT -> 0f
            ManeuverType.DESTINATION -> 0f
            ManeuverType.LANDMARK_ACTION -> 0f // For "go through door", point straight
        }
    }
    
    private fun calculateDistanceBasedConfidence(
        distanceToManeuver: Float,
        triggerDistance: Float,
        angleDiff: Float
    ): Float {
        // Confidence decreases with distance and angle difference
        val distanceConfidence = 1f - (distanceToManeuver / triggerDistance).coerceIn(0f, 1f)
        val angleConfidence = 1f - (angleDiff / ANGLE_THRESHOLD).coerceIn(0f, 1f)
        
        return (distanceConfidence * 0.6f + angleConfidence * 0.4f).coerceIn(0f, 1f)
    }
    
    private fun checkManeuverConfirmation(
        match: MapMatcher.Match,
        nextManeuver: RouteManeuver,
        now: Long
    ) {
        val maneuverIndex = getManeuverIndex(nextManeuver)
        val currentDistance = match.s * routePath.totalLength
        
        val maneuverDistance = if (nextManeuver.vertexIndex < routePath.cumulativeLengths.size) {
            routePath.cumulativeLengths[nextManeuver.vertexIndex]
        } else {
            routePath.totalLength
        }
        
        // Check if user has passed the maneuver point
        if (currentDistance > maneuverDistance && !confirmedManeuvers.contains(maneuverIndex)) {
            confirmedManeuvers.add(maneuverIndex)
            triggerTime = now // Start confirmation timer
            lastManeuverIndex = maneuverIndex
            
            Log.d(TAG, "Maneuver confirmed: ${nextManeuver.maneuverType} at index $maneuverIndex")
        }
        
        // Clean up old confirmations
        if (maneuverIndex != lastManeuverIndex && (now - triggerTime) > CONFIRMATION_TIMEOUT_MS) {
            triggerTime = 0L
        }
    }
    
    private fun getManeuverIndex(maneuver: RouteManeuver): Int {
        return routePath.maneuvers.indexOf(maneuver)
    }
    
    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized < 0) normalized += 360f
        if (normalized > 180f) normalized -= 360f
        return normalized
    }
}
