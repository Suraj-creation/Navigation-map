package com.example.core.location

import com.example.core.navigation.GeoUtils
import com.example.domain.model.LatLngPoint
import com.example.domain.model.NavigationRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin

class MockLocationEngine : LocationEngine {

    private val _locationFlow = MutableStateFlow(
        UserLocation(
            point = LatLngPoint(26.9634, 94.2155),
            accuracyMeters = 2.5f,
            speedMps = 1.4f,
            isMock = true
        )
    )
    override val locationFlow: StateFlow<UserLocation> = _locationFlow.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var currentRoute: NavigationRoute? = null
    private var currentSegmentIndex = 0
    private var segmentProgress = 0.0 // 0.0 to 1.0 along current polyline segment

    var speedMultiplier: Float = 1.0f
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun setRoute(route: NavigationRoute) {
        currentRoute = route
        currentSegmentIndex = 0
        segmentProgress = 0.0
        if (route.polylinePoints.isNotEmpty()) {
            val start = route.polylinePoints.first()
            val next = if (route.polylinePoints.size > 1) route.polylinePoints[1] else start
            val bearing = GeoUtils.bearingDegrees(start, next)
            _locationFlow.value = UserLocation(
                point = start,
                accuracyMeters = 2.0f,
                speedMps = 1.4f * speedMultiplier,
                bearingDegrees = bearing,
                isMock = true
            )
        }
    }

    override fun start() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        simulationJob?.cancel()
        simulationJob = scope.launch {
            while (isActive && _isPlaying.value) {
                delay(400L) // Update every 400ms for smooth walking motion
                stepForward(meters = (1.4 * 0.4 * speedMultiplier))
            }
        }
    }

    override fun stop() {
        _isPlaying.value = false
        simulationJob?.cancel()
    }

    fun stepOnce(meters: Double = 5.0) {
        stepForward(meters)
    }

    fun reset() {
        stop()
        currentSegmentIndex = 0
        segmentProgress = 0.0
        currentRoute?.let { setRoute(it) }
    }

    /**
     * Injects an intentional deviation from the route to test off-route recalculation.
     */
    fun injectOffRouteDrift(driftDistanceMeters: Double = 45.0) {
        val currentLoc = _locationFlow.value.point
        // Shift latitude / longitude by approximately driftDistanceMeters (~0.0004 deg for ~45m)
        val latOffset = (driftDistanceMeters / 111320.0)
        val newPoint = LatLngPoint(
            latitude = currentLoc.latitude + latOffset,
            longitude = currentLoc.longitude + latOffset * 0.5
        )
        _locationFlow.value = _locationFlow.value.copy(
            point = newPoint,
            isMock = true
        )
    }

    private fun stepForward(meters: Double) {
        val route = currentRoute ?: return
        val points = route.polylinePoints
        if (points.size < 2 || currentSegmentIndex >= points.size - 1) return

        val p1 = points[currentSegmentIndex]
        val p2 = points[currentSegmentIndex + 1]
        val segmentDist = GeoUtils.distanceBetweenMeters(p1, p2)

        if (segmentDist <= 0.1) {
            currentSegmentIndex++
            segmentProgress = 0.0
            return
        }

        val progressDelta = meters / segmentDist
        segmentProgress += progressDelta

        if (segmentProgress >= 1.0) {
            currentSegmentIndex++
            segmentProgress = 0.0
            if (currentSegmentIndex >= points.size - 1) {
                // Reached destination
                _locationFlow.value = _locationFlow.value.copy(
                    point = points.last(),
                    speedMps = 0f
                )
                stop()
                return
            }
        }

        val currP1 = points[currentSegmentIndex]
        val currP2 = points[currentSegmentIndex + 1]
        val interpolatedLat = currP1.latitude + (currP2.latitude - currP1.latitude) * segmentProgress
        val interpolatedLng = currP1.longitude + (currP2.longitude - currP1.longitude) * segmentProgress
        val bearing = GeoUtils.bearingDegrees(currP1, currP2)

        _locationFlow.value = UserLocation(
            point = LatLngPoint(interpolatedLat, interpolatedLng),
            accuracyMeters = 2.0f,
            speedMps = 1.4f * speedMultiplier,
            bearingDegrees = bearing,
            isMock = true
        )
    }
}
