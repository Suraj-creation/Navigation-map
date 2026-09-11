package com.example.core.navigation

import com.example.domain.model.LatLngPoint
import kotlin.math.*

object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates great-circle distance between two coordinates in meters using the Haversine formula.
     */
    fun distanceBetweenMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double {
        val dLat = Math.toRadians(endLat - startLat)
        val dLng = Math.toRadians(endLng - startLng)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun distanceBetweenMeters(p1: LatLngPoint, p2: LatLngPoint): Double {
        return distanceBetweenMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
    }

    /**
     * Calculates the initial bearing from point 1 to point 2 in degrees in range [0, 360).
     */
    fun bearingDegrees(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Float {
        val lat1 = Math.toRadians(startLat)
        val lat2 = Math.toRadians(endLat)
        val dLng = Math.toRadians(endLng - startLng)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        val initialBearing = Math.toDegrees(atan2(y, x))
        return normalizeDegrees360(initialBearing.toFloat())
    }

    fun bearingDegrees(p1: LatLngPoint, p2: LatLngPoint): Float {
        return bearingDegrees(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
    }

    /**
     * Calculates relative angle = targetBearing - deviceHeading normalized to [-180, 180].
     * Negative values indicate target is to the LEFT, positive to the RIGHT.
     */
    fun relativeAngleDegrees(targetBearing: Float, deviceHeading: Float): Float {
        var diff = (targetBearing - deviceHeading) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    fun normalizeDegrees360(degrees: Float): Float {
        var normalized = degrees % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    /**
     * Calculates the minimum perpendicular distance from a point to a line segment in meters.
     */
    fun distanceToSegmentMeters(
        point: LatLngPoint,
        segStart: LatLngPoint,
        segEnd: LatLngPoint
    ): Double {
        val l2 = distanceBetweenMeters(segStart, segEnd).pow(2)
        if (l2 == 0.0) return distanceBetweenMeters(point, segStart)

        // Projection fraction t along segment
        val dLat = segEnd.latitude - segStart.latitude
        val dLng = segEnd.longitude - segStart.longitude
        val t = max(0.0, min(1.0, ((point.latitude - segStart.latitude) * dLat + (point.longitude - segStart.longitude) * dLng) / (dLat * dLat + dLng * dLng)))

        val projection = LatLngPoint(
            latitude = segStart.latitude + t * dLat,
            longitude = segStart.longitude + t * dLng
        )
        return distanceBetweenMeters(point, projection)
    }

    /**
     * Finds the minimum distance from point to any segment in a polyline.
     */
    fun distanceToPolylineMeters(point: LatLngPoint, polyline: List<LatLngPoint>): Double {
        if (polyline.isEmpty()) return 0.0
        if (polyline.size == 1) return distanceBetweenMeters(point, polyline[0])

        var minDistance = Double.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val dist = distanceToSegmentMeters(point, polyline[i], polyline[i + 1])
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }
}
