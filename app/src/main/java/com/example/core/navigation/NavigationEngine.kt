package com.example.core.navigation

import com.example.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

class NavigationEngine {

    private val _sessionState = MutableStateFlow(NavigationSessionState())
    val sessionState: StateFlow<NavigationSessionState> = _sessionState.asStateFlow()

    private val OFF_ROUTE_THRESHOLD_METERS = 35.0
    private val MANEUVER_NOW_THRESHOLD_METERS = 12.0
    private val APPROACHING_MANEUVER_THRESHOLD_METERS = 50.0
    private val ARRIVAL_THRESHOLD_METERS = 15.0

    fun prepareRoute(destination: Destination, route: NavigationRoute) {
        val firstManeuver = route.maneuvers.firstOrNull()
        val nextManeuver = if (route.maneuvers.size > 1) route.maneuvers[1] else null

        _sessionState.value = _sessionState.value.copy(
            navigationState = NavigationState.ROUTE_READY,
            activeDestination = destination,
            currentRoute = route,
            currentManeuverIndex = 0,
            currentManeuver = firstManeuver,
            nextManeuver = nextManeuver,
            totalRemainingDistanceMeters = route.totalDistanceMeters,
            etaSeconds = route.totalDurationSeconds,
            errorMessage = null
        )
    }

    fun startNavigation() {
        val current = _sessionState.value
        if (current.currentRoute != null && current.activeDestination != null) {
            _sessionState.value = current.copy(
                navigationState = NavigationState.NAVIGATING
            )
        }
    }

    fun updateLocation(newPoint: LatLngPoint, headingDegrees: Float) {
        val state = _sessionState.value
        val route = state.currentRoute ?: return
        val destination = state.activeDestination ?: return

        // 1. Check Arrival
        val distToDest = GeoUtils.distanceBetweenMeters(
            newPoint,
            LatLngPoint(destination.latitude, destination.longitude)
        )
        if (distToDest <= ARRIVAL_THRESHOLD_METERS) {
            _sessionState.value = state.copy(
                currentPosition = newPoint,
                deviceHeadingDegrees = headingDegrees,
                totalRemainingDistanceMeters = 0.0,
                etaSeconds = 0,
                navigationState = NavigationState.ARRIVED
            )
            return
        }

        // 2. Check Off-Route status
        val distFromRoute = GeoUtils.distanceToPolylineMeters(newPoint, route.polylinePoints)
        if (distFromRoute > OFF_ROUTE_THRESHOLD_METERS && state.navigationState != NavigationState.ARRIVED) {
            _sessionState.value = state.copy(
                currentPosition = newPoint,
                deviceHeadingDegrees = headingDegrees,
                offRouteDistanceMeters = distFromRoute,
                navigationState = NavigationState.OFF_ROUTE
            )
            return
        }

        // 3. Evaluate Maneuver Progress
        var maneuverIdx = state.currentManeuverIndex
        val maneuvers = route.maneuvers
        val currentManeuver = maneuvers.getOrNull(maneuverIdx)

        if (currentManeuver != null) {
            val maneuverPoint = LatLngPoint(currentManeuver.latitude, currentManeuver.longitude)
            val distToManeuver = GeoUtils.distanceBetweenMeters(newPoint, maneuverPoint)

            // Check if user completed this maneuver and should advance
            if (distToManeuver < 6.0 && maneuverIdx < maneuvers.size - 1) {
                maneuverIdx++
            }

            val activeManeuver = maneuvers.getOrNull(maneuverIdx)
            val subsequentManeuver = maneuvers.getOrNull(maneuverIdx + 1)
            val activeDistToManeuver = if (activeManeuver != null) {
                GeoUtils.distanceBetweenMeters(newPoint, LatLngPoint(activeManeuver.latitude, activeManeuver.longitude))
            } else 0.0

            // Determine maneuver sub-state
            val navState = when {
                activeDistToManeuver <= MANEUVER_NOW_THRESHOLD_METERS -> NavigationState.MANEUVER_NOW
                activeDistToManeuver <= APPROACHING_MANEUVER_THRESHOLD_METERS -> NavigationState.APPROACHING_MANEUVER
                else -> NavigationState.NAVIGATING
            }

            // Estimate remaining distance and ETA (walking at ~1.3 m/s)
            val estimatedRemainingDist = max(0.0, distToDest)
            val etaSecs = (estimatedRemainingDist / 1.3).roundToLong()

            _sessionState.value = state.copy(
                currentPosition = newPoint,
                deviceHeadingDegrees = headingDegrees,
                currentManeuverIndex = maneuverIdx,
                currentManeuver = activeManeuver,
                nextManeuver = subsequentManeuver,
                distanceToManeuverMeters = activeDistToManeuver,
                totalRemainingDistanceMeters = estimatedRemainingDist,
                etaSeconds = etaSecs,
                offRouteDistanceMeters = distFromRoute,
                navigationState = navState
            )
        }
    }

    fun triggerRecalculation(recalculatedRoute: NavigationRoute) {
        _sessionState.value = _sessionState.value.copy(
            navigationState = NavigationState.RECALCULATING
        )
        // Immediately apply new route
        prepareRoute(_sessionState.value.activeDestination!!, recalculatedRoute)
        startNavigation()
    }

    fun stopNavigation() {
        _sessionState.value = NavigationSessionState(
            navigationState = NavigationState.IDLE,
            currentPosition = _sessionState.value.currentPosition
        )
    }
}
