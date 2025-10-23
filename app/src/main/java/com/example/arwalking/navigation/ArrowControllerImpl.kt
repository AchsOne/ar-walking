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
        private const val HYSTERESIS_METERS = 2f
        private const val DEFAULT_STRIDE_M = 0.75f
    }
    
    private var currentState = ArrowController.ArrowState()
    private var confirmedManeuvers = mutableSetOf<Int>() // Maneuvers that have been confirmed
    private var triggerTime = 0L
    private var lastManeuverIndex = -1
    private var lastVisible = false
    
    override fun update(
        match: MapMatcher.Match,
        userYaw: Float,
        currentInstruction: String,
        hasLandmarkMatch: Boolean,
        matchedLandmarkIds: List<String>,
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
        
        // Calculate dynamic trigger distance based on speed and base trigger per maneuver type
        val baseTrigger = baseTriggerForType(nextManeuver)
        val dynamicTriggerDistance = calculateDynamicTriggerDistance(speed, baseTrigger)
        
        val distanceToManeuver = match.distanceToNextManeuver
        val isInTriggerWindow = inTriggerWithHysteresis(distanceToManeuver, dynamicTriggerDistance)
        val isConfirmationMode = confirmedManeuvers.contains(getManeuverIndex(nextManeuver))
        
        val distanceStr = String.format("%.1f", distanceToManeuver)
        val triggerStr = String.format("%.1f", dynamicTriggerDistance)
        Log.d(TAG, "Update: maneuver=${nextManeuver.maneuverType}, distance=${distanceStr}m, " +
                "trigger=${triggerStr}m, inWindow=$isInTriggerWindow, confirmed=$isConfirmationMode, speed=${String.format("%.2f", speed)}m/s")
        
        // Handle state transitions
        val newState = when {
            // Case 0: Special naming-based landmark for turning:
            // - *_L -> show left arrow (270°)
            // - (Future) *_R -> would show right arrow (90°) if needed
            hasLandmarkMatch && matchedLandmarkIds.any { it.endsWith("_L", ignoreCase = true) } -> {
                ArrowController.ArrowState(
                    visible = true,
                    directionYaw = 270f, // left
                    confidence = 0.9f,
                    style = ArrowController.ArrowStyle.LANDMARK,
                    distanceToTrigger = 0f
                )
            }
            
            // Case 1: Landmark-based instruction with active landmark match (non *_L) -> straight
            nextManeuver.hasLandmark && hasLandmarkMatch -> {
                ArrowController.ArrowState(
                    visible = true,
                    directionYaw = 0f,
                    confidence = 0.9f,
                    style = ArrowController.ArrowStyle.LANDMARK,
                    distanceToTrigger = 0f
                )
            }
            
            // Case 2: Distance-based instruction in trigger window -> straight
            isInTriggerWindow && !nextManeuver.hasLandmark -> {
                handleDistanceBasedArrowStraight(userYaw, distanceToManeuver, dynamicTriggerDistance, speed)
            }
            
            // Case 3: Confirmation mode after passing trigger point -> straight
            isConfirmationMode && (now - triggerTime) < CONFIRMATION_TIMEOUT_MS -> {
                ArrowController.ArrowState(
                    visible = true,
                    directionYaw = 0f,
                    confidence = 0.8f,
                    style = ArrowController.ArrowStyle.CONFIRMATION,
                    distanceToTrigger = 0f
                )
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
    
    private fun baseTriggerForType(maneuver: RouteManeuver): Float {
        return when (maneuver.maneuverType) {
            ManeuverType.LEFT, ManeuverType.RIGHT -> 15f
            ManeuverType.U_TURN -> 20f
            ManeuverType.LANDMARK_ACTION -> 10f
            ManeuverType.DESTINATION -> 5f
            ManeuverType.STRAIGHT -> 25f
        }
    }
    
    private fun inTriggerWithHysteresis(distanceToManeuver: Float, triggerDistance: Float): Boolean {
        val showThreshold = triggerDistance - HYSTERESIS_METERS
        val hideThreshold = triggerDistance + HYSTERESIS_METERS
        return if (lastVisible) {
            val stayVisible = distanceToManeuver <= hideThreshold
            lastVisible = stayVisible
            stayVisible
        } else {
            val becomeVisible = distanceToManeuver <= showThreshold
            if (becomeVisible) lastVisible = true
            becomeVisible
        }
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
    
    private fun handleDistanceBasedArrowStraight(
        userYaw: Float,
        distanceToManeuver: Float,
        triggerDistance: Float,
        speed: Float
    ): ArrowController.ArrowState {
        val directionYaw = 0f
        val angleDiff = kotlin.math.abs(normalizeAngle(directionYaw - userYaw))
        
        // Only show arrow if user is roughly facing the right direction
        val facingOk = angleDiff <= ANGLE_THRESHOLD
        val shouldShow = facingOk // direction check; window handled by caller via hysteresis
        
        if (shouldShow && triggerTime == 0L) {
            triggerTime = System.currentTimeMillis()
        }
        
        val confidence = calculateDistanceBasedConfidence(distanceToManeuver, triggerDistance, angleDiff)
        
        // Step-basierte Hinweise deaktiviert
        // val stride = if (speed > 0f) max(DEFAULT_STRIDE_M, speed / max(0.3f, speed / DEFAULT_STRIDE_M)) else DEFAULT_STRIDE_M
        // val remainingSteps = if (stride > 0f) ceil(distanceToManeuver / stride).toInt() else -1
        // val cue = when {
        //     remainingSteps in 2..3 -> ArrowController.CueStage.URGENT
        //     remainingSteps in 4..5 -> ArrowController.CueStage.LATE
        //     remainingSteps in 6..9 -> ArrowController.CueStage.MID
        //     remainingSteps in 10..14 -> ArrowController.CueStage.EARLY
        //     else -> ArrowController.CueStage.NONE
        // }
        // val vibrate = remainingSteps in 2..3
        
        return ArrowController.ArrowState(
            visible = shouldShow,
            directionYaw = directionYaw,
            confidence = confidence,
            style = ArrowController.ArrowStyle.DIRECTION,
            distanceToTrigger = distanceToManeuver
            // , remainingSteps = remainingSteps,
            // cueStage = cue,
            // shouldVibrate = vibrate
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
