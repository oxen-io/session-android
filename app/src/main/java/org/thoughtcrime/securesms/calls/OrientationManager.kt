package org.thoughtcrime.securesms.calls

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.core.content.ContextCompat.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity.SENSOR_SERVICE
import org.thoughtcrime.securesms.webrtc.Orientation
import kotlin.math.asin

class OrientationManager(private val context: Context): SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var rotationVectorSensor: Sensor? = null

    private val _orientation = MutableStateFlow(Orientation.UNKNOWN)
    val orientation: StateFlow<Orientation> = _orientation

    fun startOrientationListener(){
        // create the sensor manager if it's still null
        if(sensorManager == null) {
            sensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
        }

        if(rotationVectorSensor == null) {
            rotationVectorSensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }

        sensorManager?.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopOrientationListener(){
        sensorManager?.unregisterListener(this)
    }

    fun destroy(){
        stopOrientationListener()
        sensorManager = null
        rotationVectorSensor = null
        _orientation.value = Orientation.UNKNOWN
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            // if auto-rotate is off, bail and send UNKNOWN
            if (!isAutoRotateOn()) {
                _orientation.value = Orientation.UNKNOWN
                return
            }

            // Get the quaternion from the rotation vector sensor
            val quaternion = FloatArray(4)
            SensorManager.getQuaternionFromVector(quaternion, event.values)

            // Calculate Euler angles from the quaternion
            val pitch = asin(2.0 * (quaternion[0] * quaternion[2] - quaternion[3] * quaternion[1]))

            // Convert radians to degrees
            val pitchDegrees = Math.toDegrees(pitch).toFloat()

            // Determine the device's orientation based on the pitch and roll values
            val currentOrientation = when {
                pitchDegrees > 45  -> Orientation.LANDSCAPE
                pitchDegrees < -45 -> Orientation.REVERSED_LANDSCAPE
                else -> Orientation.PORTRAIT
            }

            if (currentOrientation != _orientation.value) {
                _orientation.value = currentOrientation
            }
        }
    }

    //Function to check if Android System Auto-rotate is on or off
    private fun isAutoRotateOn(): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}