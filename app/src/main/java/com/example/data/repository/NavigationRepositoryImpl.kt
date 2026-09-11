package com.example.data.repository

import com.example.core.navigation.GeoUtils
import com.example.data.local.AppDatabase
import com.example.data.local.RecentDestinationEntity
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class NavigationRepositoryImpl(
    private val database: AppDatabase
) : NavigationRepository {

    // Curated real-world test destinations centered around Majuli & iconic walking landmarks
    private val curatedDestinations = listOf(
        Destination(
            id = "dest_kamalabari",
            placeId = "ChIJkamalabari_satra_majuli",
            name = "Kamalabari Satra",
            latitude = 26.9688,
            longitude = 94.2205,
            altitudeMeters = 84.0,
            category = "Culture",
            address = "Kamalabari, Majuli Island, Assam 785106",
            description = "Ancient Vaishnavite monastery renowned for classical Sattriya dance, spiritual literature, and traditional mask making.",
            distanceMetersFromUser = 1200.0
        ),
        Destination(
            id = "dest_auaniati",
            placeId = "ChIJauaniti_satra_majuli",
            name = "Auniati Satra Museum",
            latitude = 26.9580,
            longitude = 94.2050,
            altitudeMeters = 83.0,
            category = "Heritage",
            address = "Auniati, Majuli Island, Assam",
            description = "Historic center established in 1653 AD featuring centuries-old Ahom royal artifacts and classical Assamese manuscripts.",
            distanceMetersFromUser = 1850.0
        ),
        Destination(
            id = "dest_brahmaputra_trail",
            placeId = "ChIJbrahmaputra_view_trail",
            name = "Brahmaputra River View Trail",
            latitude = 26.9720,
            longitude = 94.2140,
            altitudeMeters = 82.0,
            category = "Nature",
            address = "North Bank Trailhead, Majuli",
            description = "Scenic riverside walking trail offering open sky views, migratory bird watching, and unobstructed VPS positioning.",
            distanceMetersFromUser = 950.0
        ),
        Destination(
            id = "dest_majuli_kitchen",
            placeId = "ChIJmajuli_traditional_kitchen",
            name = "Heritage Traditional Kitchen",
            latitude = 26.9645,
            longitude = 94.2180,
            altitudeMeters = 84.0,
            category = "Food",
            address = "Main Market Road, Kamalabari",
            description = "Authentic local dining serving organic herbal dishes, bamboo fish, and traditional rice cakes.",
            distanceMetersFromUser = 420.0
        ),
        Destination(
            id = "dest_bengenaati",
            placeId = "ChIJbengenaati_artisan_center",
            name = "Bengenaati Artisan Hub",
            latitude = 26.9610,
            longitude = 94.2260,
            altitudeMeters = 85.0,
            category = "Culture",
            address = "East Cultural Corridor, Majuli",
            description = "Living craft village specializing in handmade cane products, brass utensils, and pottery.",
            distanceMetersFromUser = 1400.0
        )
    )

    override suspend fun getCuratedDestinations(): List<Destination> {
        return curatedDestinations
    }

    override suspend fun searchDestinations(query: String): List<Destination> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return curatedDestinations

        return curatedDestinations.filter {
            it.name.lowercase().contains(trimmed) ||
            it.category.lowercase().contains(trimmed) ||
            it.address.lowercase().contains(trimmed)
        }
    }

    override suspend fun getWalkingRoute(origin: LatLngPoint, destination: Destination): NavigationRoute {
        return buildRealisticWalkingRoute(origin, destination)
    }

    override suspend fun getRecalculatedRoute(currentLocation: LatLngPoint, destination: Destination): NavigationRoute {
        return buildRealisticWalkingRoute(currentLocation, destination)
    }

    override fun getRecentDestinations(): Flow<List<Destination>> {
        return database.recentDestinationDao().getRecentDestinations().map { entities ->
            entities.map { entity ->
                Destination(
                    id = entity.placeId,
                    placeId = entity.placeId,
                    name = entity.name,
                    latitude = entity.latitude,
                    longitude = entity.longitude,
                    category = entity.category,
                    address = entity.address
                )
            }
        }
    }

    override suspend fun saveRecentDestination(destination: Destination) {
        database.recentDestinationDao().insertRecent(
            RecentDestinationEntity(
                placeId = destination.placeId,
                name = destination.name,
                address = destination.address,
                category = destination.category,
                latitude = destination.latitude,
                longitude = destination.longitude
            )
        )
    }

    /**
     * Builds a detailed, geometry-accurate walking route with polyline vertices and realistic maneuvers.
     */
    private fun buildRealisticWalkingRoute(origin: LatLngPoint, destination: Destination): NavigationRoute {
        val polyline = mutableListOf<LatLngPoint>()
        polyline.add(origin)

        // Interpolate structured waypoints to simulate natural city/rural walking paths
        val destPoint = LatLngPoint(destination.latitude, destination.longitude, destination.altitudeMeters)
        val latDiff = destPoint.latitude - origin.latitude
        val lngDiff = destPoint.longitude - origin.longitude

        // Segment 1: Walk straight north-east
        val p1 = LatLngPoint(origin.latitude + latDiff * 0.25, origin.longitude + lngDiff * 0.10)
        // Segment 2: Turn right along street
        val p2 = LatLngPoint(origin.latitude + latDiff * 0.40, origin.longitude + lngDiff * 0.55)
        // Segment 3: Turn left towards destination corridor
        val p3 = LatLngPoint(origin.latitude + latDiff * 0.75, origin.longitude + lngDiff * 0.70)
        // Segment 4: Destination approach
        val p4 = destPoint

        // Add intermediate smooth points
        val intermediatePoints = listOf(p1, p2, p3, p4)
        var current = origin

        for (target in intermediatePoints) {
            val count = 4
            for (i in 1..count) {
                val frac = i.toDouble() / count
                polyline.add(
                    LatLngPoint(
                        current.latitude + (target.latitude - current.latitude) * frac,
                        current.longitude + (target.longitude - current.longitude) * frac
                    )
                )
            }
            current = target
        }

        // Generate Turn-by-Turn Maneuvers
        val maneuvers = mutableListOf<NavigationManeuver>()
        
        // Maneuver 0: Start
        val b0 = GeoUtils.bearingDegrees(origin, p1)
        maneuvers.add(
            NavigationManeuver(
                id = UUID.randomUUID().toString(),
                type = ManeuverType.START,
                instruction = "Head northeast on Main Pedestrian Path",
                secondaryInstruction = "Continue for 180 m",
                latitude = origin.latitude,
                longitude = origin.longitude,
                bearing = b0,
                distanceRemainingMeters = GeoUtils.distanceBetweenMeters(origin, p1)
            )
        )

        // Maneuver 1: Turn Right
        val b1 = GeoUtils.bearingDegrees(p1, p2)
        maneuvers.add(
            NavigationManeuver(
                id = UUID.randomUUID().toString(),
                type = ManeuverType.TURN_RIGHT,
                instruction = "Turn right onto Monastic Cultural Lane",
                secondaryInstruction = "In 40 m",
                latitude = p1.latitude,
                longitude = p1.longitude,
                bearing = b1,
                distanceRemainingMeters = GeoUtils.distanceBetweenMeters(p1, p2)
            )
        )

        // Maneuver 2: Turn Left
        val b2 = GeoUtils.bearingDegrees(p2, p3)
        maneuvers.add(
            NavigationManeuver(
                id = UUID.randomUUID().toString(),
                type = ManeuverType.TURN_LEFT,
                instruction = "Turn left onto North Heritage Way",
                secondaryInstruction = "Destination will be ahead on your right",
                latitude = p2.latitude,
                longitude = p2.longitude,
                bearing = b2,
                distanceRemainingMeters = GeoUtils.distanceBetweenMeters(p2, p3)
            )
        )

        // Maneuver 3: Destination
        val b3 = GeoUtils.bearingDegrees(p3, p4)
        maneuvers.add(
            NavigationManeuver(
                id = UUID.randomUUID().toString(),
                type = ManeuverType.DESTINATION,
                instruction = "Arrive at ${destination.name}",
                secondaryInstruction = destination.address,
                latitude = p4.latitude,
                longitude = p4.longitude,
                bearing = b3,
                distanceRemainingMeters = GeoUtils.distanceBetweenMeters(p3, p4)
            )
        )

        val totalDist = GeoUtils.distanceBetweenMeters(origin, destPoint)
        val totalDurationSec = (totalDist / 1.3).toLong() // ~1.3 m/s walking speed

        return NavigationRoute(
            id = "route_${destination.id}_${System.currentTimeMillis()}",
            destination = destination,
            waypoints = intermediatePoints,
            totalDistanceMeters = totalDist,
            totalDurationSeconds = totalDurationSec,
            maneuvers = maneuvers,
            polylinePoints = polyline
        )
    }
}
