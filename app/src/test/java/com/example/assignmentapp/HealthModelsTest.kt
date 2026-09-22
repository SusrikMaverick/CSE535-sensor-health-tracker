package com.example.assignmentapp

import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.data.Symptom
import com.example.assignmentapp.data.toEntity
import com.example.assignmentapp.data.toSession
import com.example.assignmentapp.ui.HealthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthModelsTest {
    @Test
    fun symptomRatingsDefaultToZero() {
        val ratings = HealthSession().symptomRatings

        assertEquals(Symptom.entries.size, ratings.size)
        assertTrue(ratings.values.all { it == 0 })
    }

    @Test
    fun symptomRatingsRejectValuesOutsideRange() {
        val ratings = Symptom.entries.associateWith { 0 }

        assertThrows(IllegalArgumentException::class.java) {
            HealthSession(symptomRatings = ratings + (Symptom.NAUSEA to -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HealthSession(symptomRatings = ratings + (Symptom.NAUSEA to 6))
        }
    }

    @Test
    fun viewModelAcceptsRatingBoundariesAndRejectsInvalidUpdates() {
        val viewModel = HealthViewModel()

        viewModel.updateSymptomRating(Symptom.NAUSEA, 0)
        viewModel.updateSymptomRating(Symptom.NAUSEA, 5)

        assertEquals(5, viewModel.session.symptomRatings[Symptom.NAUSEA])
        assertThrows(IllegalArgumentException::class.java) {
            viewModel.updateSymptomRating(Symptom.NAUSEA, -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            viewModel.updateSymptomRating(Symptom.NAUSEA, 6)
        }
        assertEquals(5, viewModel.session.symptomRatings[Symptom.NAUSEA])
        assertTrue(
            viewModel.session.symptomRatings
                .filterKeys { it != Symptom.NAUSEA }
                .values
                .all { it == 0 }
        )
    }

    @Test
    fun viewModelUpdatesAndResetsSessionState() {
        val viewModel = HealthViewModel()
        val heartRate = MeasurementState.Success(72.0)
        val respiratoryRate = MeasurementState.Success(16.0)

        viewModel.updateHeartRate(MeasurementState.Collecting)
        assertEquals(MeasurementState.Collecting, viewModel.heartRateState)

        viewModel.updateHeartRate(heartRate)
        viewModel.updateRespiratoryRate(respiratoryRate)
        viewModel.updateSymptomRating(Symptom.NAUSEA, 5)

        assertEquals(heartRate, viewModel.heartRateState)
        assertEquals(respiratoryRate, viewModel.respiratoryRateState)
        assertEquals(72.0, viewModel.session.heartRate)
        assertEquals(16.0, viewModel.session.respiratoryRate)
        assertEquals(5, viewModel.session.symptomRatings[Symptom.NAUSEA])

        viewModel.resetSession()

        assertEquals(MeasurementState.Idle, viewModel.heartRateState)
        assertEquals(MeasurementState.Idle, viewModel.respiratoryRateState)
        assertEquals(HealthSession(), viewModel.session)
    }

    @Test
    fun respiratoryRetryAndCancellationPreserveHeartRateResult() {
        val viewModel = HealthViewModel()
        val heartRate = MeasurementState.Success(72.0)
        viewModel.updateHeartRate(heartRate)

        viewModel.updateRespiratoryRate(MeasurementState.Error("Collection failed."))
        viewModel.updateRespiratoryRate(MeasurementState.Idle)
        viewModel.updateRespiratoryRate(MeasurementState.Collecting)
        viewModel.updateRespiratoryRate(MeasurementState.Idle)

        assertEquals(heartRate, viewModel.heartRateState)
        assertEquals(72.0, viewModel.session.heartRate)
        assertEquals(null, viewModel.session.respiratoryRate)
    }

    @Test
    fun sessionAndEntityMappingRoundTrips() {
        val session = HealthSession(
            heartRate = 72.0,
            respiratoryRate = 16.0,
            symptomRatings = Symptom.entries.associateWith { it.ordinal % 6 }
        )

        val entity = session.toEntity(timestamp = 123L)

        assertEquals(123L, entity.timestamp)
        assertEquals(session, entity.toSession())
    }

}
