package com.example.core.ar

import android.content.Context
import android.util.Log
import com.example.domain.model.ARConfidence
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpsStatus {
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    ERROR
}

data class GeospatialPoseData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val headingDegrees: Float = 0f,
    val horizontalAccuracyMeters: Float = 999f,
    val verticalAccuracyMeters: Float = 999f,
    val headingAccuracyDegrees: Float = 999f,
    val isTracking: Boolean = false
)

class ARSessionManager(private val context: Context) {

    private val TAG = "ARSessionManager"

    private var session: Session? = null

    private val _isArCoreSupported = MutableStateFlow<Boolean?>(null)
    val isArCoreSupported: StateFlow<Boolean?> = _isArCoreSupported.asStateFlow()

    private val _isGeospatialSupported = MutableStateFlow(false)
    val isGeospatialSupported: StateFlow<Boolean> = _isGeospatialSupported.asStateFlow()

    private val _vpsStatus = MutableStateFlow(VpsStatus.CHECKING)
    val vpsStatus: StateFlow<VpsStatus> = _vpsStatus.asStateFlow()

    private val _geospatialPose = MutableStateFlow(GeospatialPoseData())
    val geospatialPose: StateFlow<GeospatialPoseData> = _geospatialPose.asStateFlow()

    private val _trackingState = MutableStateFlow(TrackingState.STOPPED)
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    fun checkAvailability(onResult: (Boolean) -> Unit) {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            // Re-check if transient
            _isArCoreSupported.value = false
            onResult(false)
        } else {
            val supported = availability.isSupported
            _isArCoreSupported.value = supported
            onResult(supported)
        }
    }

    /**
     * Initializes the ARCore Session with Geospatial mode enabled.
     */
    fun createSession(): Session? {
        try {
            val s = Session(context)
            val config = Config(s)

            if (s.isGeospatialModeSupported(Config.GeospatialMode.ENABLED)) {
                config.geospatialMode = Config.GeospatialMode.ENABLED
                _isGeospatialSupported.value = true
            } else {
                config.geospatialMode = Config.GeospatialMode.DISABLED
                _isGeospatialSupported.value = false
                Log.w(TAG, "Geospatial mode is NOT supported on this device/hardware.")
            }

            // Set focus mode to auto for clearer camera feed
            config.focusMode = Config.FocusMode.AUTO
            s.configure(config)
            session = s
            return s
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ARCore session: ${e.message}", e)
            return null
        }
    }

    fun checkVpsAvailability(latitude: Double, longitude: Double) {
        val s = session ?: return
        if (!_isGeospatialSupported.value) {
            _vpsStatus.value = VpsStatus.UNAVAILABLE
            return
        }

        _vpsStatus.value = VpsStatus.CHECKING
        try {
            s.checkVpsAvailabilityAsync(latitude, longitude) { availability ->
                _vpsStatus.value = when (availability) {
                    VpsAvailability.AVAILABLE -> VpsStatus.AVAILABLE
                    VpsAvailability.UNAVAILABLE -> VpsStatus.UNAVAILABLE
                    else -> VpsStatus.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VPS availability: ${e.message}")
            _vpsStatus.value = VpsStatus.UNAVAILABLE
        }
    }

    /**
     * Updates frame-by-frame geospatial pose from the ARCore Earth tracking system.
     */
    fun updateFrame(frame: Frame) {
        val s = session ?: return
        val earth = s.earth
        if (earth != null) {
            val earthTracking = earth.trackingState
            _trackingState.value = earthTracking

            if (earthTracking == TrackingState.TRACKING) {
                val pose = earth.cameraGeospatialPose
                _geospatialPose.value = GeospatialPoseData(
                    latitude = pose.latitude,
                    longitude = pose.longitude,
                    altitudeMeters = pose.altitude,
                    headingDegrees = pose.heading.toFloat(),
                    horizontalAccuracyMeters = pose.horizontalAccuracy.toFloat(),
                    verticalAccuracyMeters = pose.verticalAccuracy.toFloat(),
                    headingAccuracyDegrees = pose.headingAccuracy.toFloat(),
                    isTracking = true
                )
            } else {
                _geospatialPose.value = _geospatialPose.value.copy(isTracking = false)
            }
        }
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available on resume: ${e.message}")
        }
    }

    fun pause() {
        session?.pause()
    }

    fun destroy() {
        session?.close()
        session = null
    }
}
