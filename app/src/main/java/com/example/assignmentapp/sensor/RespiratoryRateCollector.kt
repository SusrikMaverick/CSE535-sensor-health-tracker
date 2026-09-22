package com.example.assignmentapp.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.delay
import kotlin.math.min

const val RESPIRATORY_COLLECTION_DURATION_MILLIS = 45_000L

private const val DEFAULT_PROGRESS_UPDATE_INTERVAL_MILLIS = 250L

/**
 * Provides accelerometer samples without tying collection logic to Android's sensor APIs.
 */
interface AccelerometerSampleSource {
    val isAvailable: Boolean

    /** Returns false when the listener could not be registered. */
    fun start(onSample: (AccelerometerSample) -> Unit): Boolean

    /** Stops delivering samples. Implementations must allow repeated calls. */
    fun stop()
}

/** Android accelerometer source used by the respiratory-rate measurement flow. */
class AndroidAccelerometerSampleSource(context: Context) : AccelerometerSampleSource {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    @Volatile
    private var activeListener: SensorEventListener? = null

    override val isAvailable: Boolean
        get() = accelerometer != null

    @Synchronized
    override fun start(onSample: (AccelerometerSample) -> Unit): Boolean {
        val manager = sensorManager ?: return false
        val sensor = accelerometer ?: return false
        if (activeListener != null) return false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (
                    activeListener !== this ||
                    event.sensor.type != Sensor.TYPE_ACCELEROMETER ||
                    event.values.size < 3
                ) {
                    return
                }
                onSample(
                    AccelerometerSample(
                        timestampNanos = event.timestamp,
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2]
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        activeListener = listener
        val registered = try {
            manager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } catch (_: RuntimeException) {
            false
        }

        if (!registered) {
            activeListener = null
            manager.unregisterListener(listener)
        }
        return registered
    }

    @Synchronized
    override fun stop() {
        val listener = activeListener ?: return
        activeListener = null
        sensorManager?.unregisterListener(listener)
    }
}

open class RespiratoryRateCollectionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class AccelerometerUnavailableException : RespiratoryRateCollectionException(
    "An accelerometer is not available on this device."
)

class AccelerometerRegistrationException(cause: Throwable? = null) :
    RespiratoryRateCollectionException(
        "Accelerometer data collection couldn't start.",
        cause
    )

/**
 * Collects accelerometer data for 45 seconds by default.
 *
 * [onProgress] receives values from `0f` through `1f`. The source is always stopped before
 * this function returns or propagates an exception, including coroutine cancellation.
 */
suspend fun collectRespiratorySamples(
    sampleSource: AccelerometerSampleSource,
    collectionDurationMillis: Long = RESPIRATORY_COLLECTION_DURATION_MILLIS,
    progressUpdateIntervalMillis: Long = DEFAULT_PROGRESS_UPDATE_INTERVAL_MILLIS,
    onProgress: (Float) -> Unit = {}
): List<AccelerometerSample> {
    require(collectionDurationMillis > 0) {
        "Collection duration must be greater than zero."
    }
    require(progressUpdateIntervalMillis > 0) {
        "Progress update interval must be greater than zero."
    }

    val sampleLock = Any()
    val samples = mutableListOf<AccelerometerSample>()

    try {
        if (!sampleSource.isAvailable) {
            throw AccelerometerUnavailableException()
        }

        val registered = try {
            sampleSource.start { sample ->
                synchronized(sampleLock) {
                    samples += sample
                }
            }
        } catch (error: RuntimeException) {
            throw AccelerometerRegistrationException(error)
        }
        if (!registered) {
            throw AccelerometerRegistrationException()
        }

        onProgress(0f)
        var elapsedMillis = 0L
        while (elapsedMillis < collectionDurationMillis) {
            val intervalMillis = min(
                progressUpdateIntervalMillis,
                collectionDurationMillis - elapsedMillis
            )
            delay(intervalMillis)
            elapsedMillis += intervalMillis
            onProgress(elapsedMillis.toFloat() / collectionDurationMillis.toFloat())
        }
    } finally {
        sampleSource.stop()
    }

    return synchronized(sampleLock) { samples.toList() }
}

fun canStartRespiratoryCollection(hasActiveCollection: Boolean): Boolean =
    !hasActiveCollection
