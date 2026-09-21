package com.example.assignmentapp.sensor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

interface RespiratoryRateProcessor {
    suspend fun calculate(samples: List<AccelerometerSample>): Int
}

class RespiratoryRateProcessingException(message: String) : Exception(message)

class SampleRespiratoryRateProcessor : RespiratoryRateProcessor {
    override suspend fun calculate(samples: List<AccelerometerSample>): Int =
        withContext(Dispatchers.Default) {
            if (samples.any { !it.isValid() }) {
                throw RespiratoryRateProcessingException(
                    "The respiratory-rate samples are malformed."
                )
            }
            if (samples.size < 12) {
                throw RespiratoryRateProcessingException(
                    "There are not enough respiratory-rate samples."
                )
            }

            var previousMagnitude = 10f
            var changeCount = 0

            for (index in 11 until samples.size) {
                val sample = samples[index]
                val currentMagnitude = sqrt(
                    sample.x.toDouble().pow(2.0) +
                        sample.y.toDouble().pow(2.0) +
                        sample.z.toDouble().pow(2.0)
                ).toFloat()

                if (abs(previousMagnitude - currentMagnitude) > 0.15f) {
                    changeCount++
                }
                previousMagnitude = currentMagnitude
            }

            ((changeCount.toDouble() / 45.0) * 30).toInt()
        }
}

private fun AccelerometerSample.isValid(): Boolean =
    timestampNanos >= 0 && x.isFinite() && y.isFinite() && z.isFinite()
