package com.example.domain.model

data class Destination(
    val id: String,
    val placeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0,
    val category: String,
    val address: String,
    val description: String = "",
    val distanceMetersFromUser: Double = 0.0
)

enum class ManeuverType {
    START,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    UTURN,
    DESTINATION
}

data class NavigationManeuver(
    val id: String,
    val type: ManeuverType,
    val instruction: String,
    val secondaryInstruction: String = "",
    val latitude: Double,
    val longitude: Double,
    val bearing: Float,
    val distanceRemainingMeters: Double,
    val isCompleted: Boolean = false
)

data class LatLngPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
)

data class NavigationRoute(
    val id: String,
    val destination: Destination,
    val waypoints: List<LatLngPoint>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Long,
    val maneuvers: List<NavigationManeuver>,
    val polylinePoints: List<LatLngPoint>
)
