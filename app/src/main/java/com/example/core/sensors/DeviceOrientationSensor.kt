package com.example.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.core.navigation.GeoUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.roundToInt

data class DeviceOrientation(
    val azimuthDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val accuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
)

class DeviceOrientationSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _orientationFlow = MutableStateFlow(DeviceOrientation())
    val orientationFlow: StateFlow<DeviceOrientation> = _orientationFlow.asStateFlow()

    private val rotationVectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var gravityValues: FloatArray? = null
    private var geomagneticValues: FloatArray? = null

    // Low-pass filter smoothing alpha
    private val alpha = 0.25f
    private var smoothedAzimuth = 0f

    fun start() {
        if (sensorManager == null) return
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                computeOrientation(event.accuracy)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravityValues = event.values.clone()
                checkFallbackOrientation(event.accuracy)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                geomagneticValues = event.values.clone()
                checkFallbackOrientation(event.accuracy)
            }
        }
    }

    private fun checkFallbackOrientation(accuracy: Int) {
        val grav = gravityValues
        val geo = geomagneticValues
        if (grav != null && geo != null) {
            val success = SensorManager.getRotationMatrix(rotationMatrix, null, grav, geo)
            if (success) {
                computeOrientation(accuracy)
            }
        }
    }

    private fun computeOrientation(accuracy: Int) {
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // orientationAngles[0] = azimuth in radians (-pi to pi)
        // orientationAngles[1] = pitch in radians (-pi/2 to pi/2)
        // orientationAngles[2] = roll in radians (-pi to pi)

        val rawAzimuthDeg = GeoUtils.normalizeDegrees360(Math.toDegrees(orientationAngles[0].toDouble()).toFloat())
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // Circular smoothing for azimuth to handle 359 -> 0 wrap-around cleanly
        val diff = GeoUtils.relativeAngleDegrees(rawAzimuthDeg, smoothedAzimuth)
        smoothedAzimuth = GeoUtils.normalizeDegrees360(smoothedAzimuth + alpha * diff)

        _orientationFlow.value = DeviceOrientation(
            azimuthDegrees = smoothedAzimuth,
            pitchDegrees = pitchDeg,
            rollDegrees = rollDeg,
            accuracy = accuracy
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Can be monitored for ARConfidence
    }
}
