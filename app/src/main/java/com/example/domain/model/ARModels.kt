package com.example.domain.model

enum class ARConfidence {
    HIGH,        // ARCore Earth TRACKING, VPS available, GPS < 5m, Orientation stable
    MEDIUM,      // GPS < 12m, Stabilizing, VPS calibrating
    LOW,         // GPS > 15m or high magnetic noise, recommending map fallback
    UNAVAILABLE  // ARCore unsupported or sensor failure
}

enum class NavigationState {
    IDLE,
    PREPARING,
    ROUTE_READY,
    NAVIGATING,
    APPROACHING_MANEUVER,
    MANEUVER_NOW,
    OFF_ROUTE,
    RECALCULATING,
    ARRIVED,
    ERROR
}

data class ARGuidance(
    val targetLocation: LatLngPoint,
    val targetBearingDegrees: Float,
    val deviceHeadingDegrees: Float,
    val relativeAngleDegrees: Float,     // Target bearing minus device heading [-180..180]
    val distanceMeters: Double,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val ribbonPoints: List<LatLngPoint> = emptyList(),
    val confidence: ARConfidence = ARConfidence.MEDIUM,
    val isVisible: Boolean = true,
    val isDestinationBeaconVisible: Boolean = false,
    val destinationBeaconDistance: Double = 0.0
)

data class NavigationSessionState(
    val navigationState: NavigationState = NavigationState.IDLE,
    val activeDestination: Destination? = null,
    val currentRoute: NavigationRoute? = null,
    val currentPosition: LatLngPoint = LatLngPoint(26.9634, 94.2155), // Default Kamalabari, Majuli
    val deviceHeadingDegrees: Float = 0f,
    val currentManeuverIndex: Int = 0,
    val currentManeuver: NavigationManeuver? = null,
    val nextManeuver: NavigationManeuver? = null,
    val distanceToManeuverMeters: Double = 0.0,
    val totalRemainingDistanceMeters: Double = 0.0,
    val etaSeconds: Long = 0,
    val offRouteDistanceMeters: Double = 0.0,
    val arConfidence: ARConfidence = ARConfidence.MEDIUM,
    val arGuidance: ARGuidance? = null,
    val isSimulating: Boolean = false,
    val errorMessage: String? = null
)
