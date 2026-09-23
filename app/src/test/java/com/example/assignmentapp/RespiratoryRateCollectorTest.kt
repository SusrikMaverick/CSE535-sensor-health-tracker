package com.example.assignmentapp

import com.example.assignmentapp.sensor.AccelerometerRegistrationException
import com.example.assignmentapp.sensor.AccelerometerSample
import com.example.assignmentapp.sensor.AccelerometerSampleSource
import com.example.assignmentapp.sensor.AccelerometerUnavailableException
import com.example.assignmentapp.sensor.RESPIRATORY_COLLECTION_DURATION_MILLIS
import com.example.assignmentapp.sensor.collectRespiratorySamples
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RespiratoryRateCollectorTest {
    @Test
    fun missingAccelerometerReturnsClearErrorAndStopsSource() = runTest {
        val source = FakeAccelerometerSampleSource(isAvailable = false)

        try {
            collectRespiratorySamples(source)
            fail("Expected an unavailable-accelerometer error.")
        } catch (error: AccelerometerUnavailableException) {
            assertEquals(
                "An accelerometer is not available on this device.",
                error.message
            )
        }

        assertEquals(0, source.startCount)
        assertEquals(1, source.stopCount)
    }

    @Test
    fun completedCollectionReturnsSamplesReportsProgressAndStopsSource() = runTest {
        val source = FakeAccelerometerSampleSource()
        val progress = mutableListOf<Float>()
        val collection = async {
            collectRespiratorySamples(
                sampleSource = source,
                progressUpdateIntervalMillis = 15_000L,
                onProgress = progress::add
            )
        }

        runCurrent()
        source.emit(AccelerometerSample(1L, 1f, 2f, 3f))
        advanceTimeBy(15_000L)
        runCurrent()
        source.emit(AccelerometerSample(2L, 4f, 5f, 6f))
        advanceTimeBy(15_000L)
        runCurrent()
        source.emit(AccelerometerSample(3L, 7f, 8f, 9f))
        advanceTimeBy(15_000L)
        runCurrent()

        assertEquals(
            listOf(0f, 1f / 3f, 2f / 3f, 1f),
            progress
        )
        assertEquals(
            listOf(
                AccelerometerSample(1L, 1f, 2f, 3f),
                AccelerometerSample(2L, 4f, 5f, 6f),
                AccelerometerSample(3L, 7f, 8f, 9f)
            ),
            collection.await()
        )
        assertEquals(RESPIRATORY_COLLECTION_DURATION_MILLIS, testScheduler.currentTime)
        assertEquals(1, source.startCount)
        assertEquals(1, source.stopCount)
        assertNull(source.listener)
    }

    @Test
    fun cancellationStopsSourceAndDiscardsActiveListener() = runTest {
        val source = FakeAccelerometerSampleSource()
        val collection = async { collectRespiratorySamples(source) }

        runCurrent()
        assertTrue(source.listener != null)
        advanceTimeBy(1_000L)
        collection.cancel()
        runCurrent()

        assertTrue(collection.isCancelled)
        assertEquals(1, source.stopCount)
        assertNull(source.listener)
    }

    @Test
    fun registrationFailureReturnsClearErrorAndStopsSource() = runTest {
        val source = FakeAccelerometerSampleSource(registrationSucceeds = false)

        try {
            collectRespiratorySamples(source)
            fail("Expected an accelerometer-registration error.")
        } catch (error: AccelerometerRegistrationException) {
            assertEquals(
                "Accelerometer data collection couldn't start.",
                error.message
            )
        }

        assertEquals(1, source.startCount)
        assertEquals(1, source.stopCount)
        assertNull(source.listener)
    }
}

private class FakeAccelerometerSampleSource(
    override val isAvailable: Boolean = true,
    private val registrationSucceeds: Boolean = true
) : AccelerometerSampleSource {
    var startCount = 0
        private set
    var stopCount = 0
        private set
    var listener: ((AccelerometerSample) -> Unit)? = null
        private set

    override fun start(onSample: (AccelerometerSample) -> Unit): Boolean {
        startCount++
        if (!registrationSucceeds) return false
        listener = onSample
        return true
    }

    override fun stop() {
        stopCount++
        listener = null
    }

    fun emit(sample: AccelerometerSample) {
        listener?.invoke(sample)
    }
}
