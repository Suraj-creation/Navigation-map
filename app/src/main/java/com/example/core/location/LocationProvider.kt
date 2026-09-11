package com.example.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.domain.model.LatLngPoint
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserLocation(
    val point: LatLngPoint,
    val accuracyMeters: Float = 5.0f,
    val speedMps: Float = 1.3f, // Average walking speed ~1.3-1.4 m/s
    val bearingDegrees: Float = 0f,
    val isMock: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

interface LocationEngine {
    val locationFlow: StateFlow<UserLocation>
    fun start()
    fun stop()
}

class FusedLocationEngine(context: Context) : LocationEngine {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationFlow = MutableStateFlow(
        UserLocation(point = LatLngPoint(26.9634, 94.2155), accuracyMeters = 8.0f)
    )
    override val locationFlow: StateFlow<UserLocation> = _locationFlow.asStateFlow()

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
        .setMinUpdateIntervalMillis(1000L)
        .setMinUpdateDistanceMeters(1.0f)
        .build()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc: Location = result.lastLocation ?: return
            _locationFlow.value = UserLocation(
                point = LatLngPoint(loc.latitude, loc.longitude, loc.altitude),
                accuracyMeters = loc.accuracy,
                speedMps = if (loc.hasSpeed()) loc.speed else 1.3f,
                bearingDegrees = if (loc.hasBearing()) loc.bearing else 0f,
                isMock = loc.isFromMockProvider,
                timestamp = loc.time
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        try {
            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    _locationFlow.value = UserLocation(
                        point = LatLngPoint(loc.latitude, loc.longitude, loc.altitude),
                        accuracyMeters = loc.accuracy,
                        speedMps = if (loc.hasSpeed()) loc.speed else 1.3f,
                        bearingDegrees = if (loc.hasBearing()) loc.bearing else 0f
                    )
                }
            }
        } catch (_: SecurityException) {
            // Handled gracefully when permission not yet granted
        }
    }

    override fun stop() {
        fusedClient.removeLocationUpdates(callback)
    }
}
