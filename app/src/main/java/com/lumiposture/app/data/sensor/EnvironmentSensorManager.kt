package com.lumiposture.app.data.sensor

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.SystemClock
import android.provider.Settings
import com.lumiposture.app.domain.model.EnvironmentTelemetry
import com.lumiposture.app.domain.model.DataStatus
import com.lumiposture.app.domain.model.SensorCapabilities
import com.lumiposture.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class SensorToggleConfig(
    val enableLight: Boolean,
    val enableProximity: Boolean,
    val enableAccelerometer: Boolean,
    val enableGyroscope: Boolean,
)

@Singleton
class EnvironmentSensorManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    fun getSensorCapabilities(): SensorCapabilities {
        val pm = context.packageManager
        val hasFrontCamera = runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.any { id ->
                cm.getCameraCharacteristics(id)[CameraCharacteristics.LENS_FACING] ==
                    CameraCharacteristics.LENS_FACING_FRONT
            }
        }.getOrDefault(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT))

        return SensorCapabilities(
            hasFrontCamera = hasFrontCamera,
            hasLightSensor = lightSensor != null,
            hasProximitySensor = proximitySensor != null,
            hasAccelerometer = accelerometer != null,
            hasGyroscope = gyroscope != null,
            hasRotationVector = rotationVectorSensor != null,
            hasMicrophone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeEnvironmentTelemetry(preferencesFlow: Flow<UserPreferences>): Flow<EnvironmentTelemetry> {
        return preferencesFlow
            .map { prefs ->
                SensorToggleConfig(
                    enableLight = prefs.enableLightSensor,
                    enableProximity = prefs.enableProximitySensor,
                    enableAccelerometer = prefs.enableAccelerometer,
                    enableGyroscope = prefs.enableGyroscope,
                )
            }
            .distinctUntilChanged()
            .flatMapLatest { cfg ->
                observeWithConfig(cfg)
            }
    }

    fun observeEnvironmentTelemetry(): Flow<EnvironmentTelemetry> = observeWithConfig(
        SensorToggleConfig(
            enableLight = true,
            enableProximity = true,
            enableAccelerometer = true,
            enableGyroscope = true,
        ),
    )

    private fun observeWithConfig(cfg: SensorToggleConfig): Flow<EnvironmentTelemetry> = callbackFlow {
        var ambientLux = 0f
        var isProximityNear = false
        var proximityCm = 100f
        var devicePitchDeg = 0f
        var lightTimestamp = 0L
        var proximityTimestamp = 0L
        var motionTimestamp = 0L
        var lightStatus = initialStatus(cfg.enableLight, lightSensor)
        var proximityStatus = initialStatus(cfg.enableProximity, proximitySensor)
        var motionStatus = initialStatus(cfg.enableGyroscope || cfg.enableAccelerometer,
            if (cfg.enableGyroscope && rotationVectorSensor != null) rotationVectorSensor else accelerometer.takeIf { cfg.enableAccelerometer })
        var isLyingDown = false
        var lastBrightnessCheckMs = 0L
        var cachedBrightnessPct = readScreenBrightnessPct()

        val usingRotationVector = cfg.enableGyroscope && (rotationVectorSensor != null)
        val rotationMatrix = FloatArray(9)
        var filteredAx = 0f
        var filteredAy = 9.81f
        var filteredAz = 0f
        var accelInitialized = false

        fun emitCurrent() {
            val nowMs = SystemClock.elapsedRealtime()
            if ((nowMs - lastBrightnessCheckMs) >= 500L) {
                cachedBrightnessPct = readScreenBrightnessPct()
                lastBrightnessCheckMs = nowMs
            }
            trySend(
                EnvironmentTelemetry(
                    ambientLux = ambientLux,
                    lightStatus = lightStatus,
                    proximityStatus = proximityStatus,
                    motionStatus = motionStatus,
                    lightTimestampMs = lightTimestamp,
                    proximityTimestampMs = proximityTimestamp,
                    motionTimestampMs = motionTimestamp,
                    lightReportsOnChange = lightSensor?.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE,
                    proximityReportsOnChange = proximitySensor?.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE,
                    hasLightSensor = lightSensor != null,
                    isProximityNear = isProximityNear,
                    proximityCm = proximityCm,
                    hasProximitySensor = proximitySensor != null,
                    devicePitchDeg = devicePitchDeg,
                    isLyingDown = isLyingDown,
                    usingRotationVector = usingRotationVector,
                    screenBrightnessPct = cachedBrightnessPct,
                ),
            )
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        if (cfg.enableLight) {
                            ambientLux = event.values[0].coerceAtLeast(0f)
                            lightTimestamp = System.currentTimeMillis()
                            lightStatus = if (ambientLux.isFinite()) DataStatus.AVAILABLE else DataStatus.FAILED
                        }
                    }

                    Sensor.TYPE_PROXIMITY -> {
                        if (cfg.enableProximity) {
                            val value = event.values[0]
                            val maxRange = proximitySensor?.maximumRange ?: 5f
                            proximityCm = value
                            isProximityNear = (value < maxRange) && (value < 10f)
                            proximityTimestamp = System.currentTimeMillis()
                            proximityStatus = if (value.isFinite()) DataStatus.AVAILABLE else DataStatus.FAILED
                        }
                    }

                    Sensor.TYPE_ROTATION_VECTOR -> {
                        if (usingRotationVector) {
                            motionTimestamp = System.currentTimeMillis()
                            motionStatus = DataStatus.AVAILABLE
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            val gx = rotationMatrix[6]
                            val gy = rotationMatrix[7]
                            val gz = rotationMatrix[8]
                            val horiz = hypot(gx.toDouble(), gy.toDouble())
                            val rawPitchDeg = Math.toDegrees(atan2(gz.toDouble(), horiz)).toFloat()
                            isLyingDown = cfg.enableAccelerometer && (gz < -0.45f)
                            devicePitchDeg = if (rawPitchDeg < -20f) {
                                abs(rawPitchDeg).coerceIn(0f, 90f)
                            } else {
                                rawPitchDeg.coerceIn(0f, 90f)
                            }
                        }
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        if (!usingRotationVector && cfg.enableAccelerometer) {
                            motionTimestamp = System.currentTimeMillis()
                            motionStatus = DataStatus.AVAILABLE
                            val alpha = 0.2f
                            if (!accelInitialized) {
                                filteredAx = event.values[0]
                                filteredAy = event.values[1]
                                filteredAz = event.values[2]
                                accelInitialized = true
                            } else {
                                filteredAx = (alpha * event.values[0]) + ((1f - alpha) * filteredAx)
                                filteredAy = (alpha * event.values[1]) + ((1f - alpha) * filteredAy)
                                filteredAz = (alpha * event.values[2]) + ((1f - alpha) * filteredAz)
                            }
                            val horiz = hypot(filteredAx.toDouble(), filteredAy.toDouble())
                            val rawPitchDeg = Math.toDegrees(atan2(filteredAz.toDouble(), horiz)).toFloat()
                            isLyingDown = filteredAz < -4.2f
                            devicePitchDeg = if (rawPitchDeg < -20f) {
                                abs(rawPitchDeg).coerceIn(0f, 90f)
                            } else {
                                rawPitchDeg.coerceIn(0f, 90f)
                            }
                        }
                    }
                }
                emitCurrent()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun register(sensor: Sensor?): Boolean = sensor != null && runCatching {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }.getOrDefault(false)
        if (cfg.enableLight && lightSensor != null && !register(lightSensor)) lightStatus = DataStatus.FAILED
        if (cfg.enableProximity && proximitySensor != null && !register(proximitySensor)) proximityStatus = DataStatus.FAILED
        if (usingRotationVector) {
            if (!register(rotationVectorSensor)) motionStatus = DataStatus.FAILED
        } else if (cfg.enableAccelerometer) {
            if (accelerometer != null && !register(accelerometer)) motionStatus = DataStatus.FAILED
        }

        emitCurrent()

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }.conflate()

    private fun initialStatus(enabled: Boolean, sensor: Sensor?): DataStatus = when {
        !enabled -> DataStatus.DISABLED
        sensor == null -> DataStatus.UNSUPPORTED
        else -> DataStatus.WAITING
    }

    @Suppress("DiscouragedApi")
    private fun readScreenBrightnessPct(): Int? {
        return runCatching {
            val raw = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
            val maxId = Resources.getSystem().getIdentifier(
                "config_screenBrightnessSettingMaximum",
                "integer",
                "android",
            )
            val maxVal = if (maxId != 0) {
                Resources.getSystem().getInteger(maxId).coerceAtLeast(255)
            } else {
                255
            }
            ((raw.toFloat() / maxVal.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        }.getOrNull()
    }
}
