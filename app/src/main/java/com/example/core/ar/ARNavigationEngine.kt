package com.example.core.ar

import com.example.core.navigation.GeoUtils
import com.example.core.sensors.DeviceOrientation
import com.example.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

class ARNavigationEngine {

    private val _guidanceFlow = MutableStateFlow<ARGuidance?>(null)
    val guidanceFlow: StateFlow<ARGuidance?> = _guidanceFlow.asStateFlow()

    private val _confidenceFlow = MutableStateFlow(ARConfidence.MEDIUM)
    val confidenceFlow: StateFlow<ARConfidence> = _confidenceFlow.asStateFlow()

    /**
     * Updates guidance calculations based on latest location, orientation, and navigation state.
     */
    fun update(
        currentLocation: LatLngPoint,
        orientation: DeviceOrientation,
        currentManeuver: NavigationManeuver?,
        nextManeuver: NavigationManeuver?,
        destination: Destination?,
        routePolyline: List<LatLngPoint>,
        geospatialPose: GeospatialPoseData?,
        vpsStatus: VpsStatus
    ) {
        if (destination == null || currentManeuver == null) {
            _guidanceFlow.value = null
            return
        }

        // Determine effective heading: prefer Geospatial heading if high accuracy, else sensor fusion
        val effectiveHeading = if (geospatialPose?.isTracking == true && geospatialPose.headingAccuracyDegrees < 15f) {
            geospatialPose.headingDegrees
        } else {
            orientation.azimuthDegrees
        }

        // Maneuver target
        val targetPoint = LatLngPoint(currentManeuver.latitude, currentManeuver.longitude)
        val distanceToManeuver = GeoUtils.distanceBetweenMeters(currentLocation, targetPoint)
        val targetBearing = GeoUtils.bearingDegrees(currentLocation, targetPoint)
        val relativeAngle = GeoUtils.relativeAngleDegrees(targetBearing, effectiveHeading)

        // Evaluate AR Confidence
        val confidence = evaluateConfidence(geospatialPose, vpsStatus, orientation)
        _confidenceFlow.value = confidence

        // Calculate upcoming ribbon path (50 - 100 meters ahead)
        val ribbonPoints = extractUpcomingRibbonSegment(currentLocation, routePolyline, maxDistanceMeters = 75.0)

        // Destination beacon active within 300 meters
        val distToDest = GeoUtils.distanceBetweenMeters(currentLocation, LatLngPoint(destination.latitude, destination.longitude))
        val showBeacon = distToDest <= 300.0

        _guidanceFlow.value = ARGuidance(
            targetLocation = targetPoint,
            targetBearingDegrees = targetBearing,
            deviceHeadingDegrees = effectiveHeading,
            relativeAngleDegrees = relativeAngle,
            distanceMeters = distanceToManeuver,
            pitchDegrees = orientation.pitchDegrees,
            rollDegrees = orientation.rollDegrees,
            ribbonPoints = ribbonPoints,
            confidence = confidence,
            isVisible = true,
            isDestinationBeaconVisible = showBeacon,
            destinationBeaconDistance = distToDest
        )
    }

    private fun evaluateConfidence(
        geospatialPose: GeospatialPoseData?,
        vpsStatus: VpsStatus,
        orientation: DeviceOrientation
    ): ARConfidence {
        if (geospatialPose?.isTracking == true) {
            return if (vpsStatus == VpsStatus.AVAILABLE && geospatialPose.horizontalAccuracyMeters <= 6.0f) {
                ARConfidence.HIGH
            } else if (geospatialPose.horizontalAccuracyMeters <= 12.0f) {
                ARConfidence.MEDIUM
            } else {
                ARConfidence.LOW
            }
        }

        // Fallback evaluation based on orientation sensor accuracy
        return when {
            orientation.accuracy >= 2 -> ARConfidence.MEDIUM
            orientation.accuracy == 1 -> ARConfidence.LOW
            else -> ARConfidence.LOW
        }
    }

    private fun extractUpcomingRibbonSegment(
        currentLocation: LatLngPoint,
        polyline: List<LatLngPoint>,
        maxDistanceMeters: Double
    ): List<LatLngPoint> {
        if (polyline.isEmpty()) return emptyList()

        // Find closest point index in polyline
        var closestIdx = 0
        var minDist = Double.MAX_VALUE
        for (i in polyline.indices) {
            val d = GeoUtils.distanceBetweenMeters(currentLocation, polyline[i])
            if (d < minDist) {
                minDist = d
                closestIdx = i
            }
        }

        val result = mutableListOf<LatLngPoint>()
        result.add(currentLocation)

        var accumulatedDist = 0.0
        var prevPoint = currentLocation

        for (i in closestIdx until polyline.size) {
            val p = polyline[i]
            val stepDist = GeoUtils.distanceBetweenMeters(prevPoint, p)
            if (accumulatedDist + stepDist > maxDistanceMeters) {
                // Add interpolated end point
                val remaining = maxDistanceMeters - accumulatedDist
                val frac = if (stepDist > 0) remaining / stepDist else 0.0
                val endLat = prevPoint.latitude + (p.latitude - prevPoint.latitude) * frac
                val endLng = prevPoint.longitude + (p.longitude - prevPoint.longitude) * frac
                result.add(LatLngPoint(endLat, endLng))
                break
            }
            accumulatedDist += stepDist
            result.add(p)
            prevPoint = p
        }

        return result
    }

    fun recenter() {
        // Triggers recalibration of visual alignment
        _guidanceFlow.value = _guidanceFlow.value?.copy(
            confidence = ARConfidence.MEDIUM
        )
    }
}
