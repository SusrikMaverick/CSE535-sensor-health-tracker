package com.example.assignmentapp

import com.example.assignmentapp.sensor.AccelerometerSample
import com.example.assignmentapp.sensor.HeartRateProcessingException
import com.example.assignmentapp.sensor.RespiratoryRateProcessingException
import com.example.assignmentapp.sensor.SampleRespiratoryRateProcessor
import com.example.assignmentapp.sensor.calculateBeatsPerMinute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MeasurementProcessorsTest {
    private val respiratoryProcessor = SampleRespiratoryRateProcessor()

    @Test
    fun heartRateAlgorithmCalculatesBeatsPerMinute() {
        val colorTotals = MutableList(9) { 0L }.apply {
            this[5] = 16_000L
        }

        assertEquals(15, calculateBeatsPerMinute(colorTotals))
    }

    @Test
    fun heartRateAlgorithmRejectsInsufficientSamples() {
        val error = assertThrows(HeartRateProcessingException::class.java) {
            calculateBeatsPerMinute(List(8) { 0L })
        }

        assertEquals(
            "The heart-rate video does not contain enough usable frames.",
            error.message
        )
    }

    @Test
    fun respiratoryRateAlgorithmCalculatesBreathsPerMinute() = runBlocking {
        val samples = List(56) { index ->
            AccelerometerSample(
                timestampNanos = index.toLong(),
                x = 0f,
                y = 0f,
                z = if (index >= 11 && (index - 11) % 2 == 0) 11f else 10f
            )
        }

        assertEquals(30, respiratoryProcessor.calculate(samples))
    }

    @Test
    fun respiratoryRateAlgorithmRejectsInsufficientSamples() {
        val samples = List(11) { index ->
            AccelerometerSample(index.toLong(), 10f, 0f, 0f)
        }

        val error = assertThrows(RespiratoryRateProcessingException::class.java) {
            runBlocking { respiratoryProcessor.calculate(samples) }
        }

        assertEquals("There are not enough respiratory-rate samples.", error.message)
    }

    @Test
    fun respiratoryRateAlgorithmRejectsMalformedSamples() {
        val samples = List(12) { index ->
            AccelerometerSample(
                timestampNanos = index.toLong(),
                x = if (index == 5) Float.NaN else 10f,
                y = 0f,
                z = 0f
            )
        }

        val error = assertThrows(RespiratoryRateProcessingException::class.java) {
            runBlocking { respiratoryProcessor.calculate(samples) }
        }

        assertEquals("The respiratory-rate samples are malformed.", error.message)
    }
}
