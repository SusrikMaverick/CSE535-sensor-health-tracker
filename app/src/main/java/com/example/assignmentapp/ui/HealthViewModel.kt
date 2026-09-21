package com.example.assignmentapp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.data.Symptom

class HealthViewModel : ViewModel() {
    var session by mutableStateOf(HealthSession())
        private set

    var heartRateState by mutableStateOf<MeasurementState>(MeasurementState.Idle)
        private set

    var respiratoryRateState by mutableStateOf<MeasurementState>(MeasurementState.Idle)
        private set

    fun resetSession() {
        session = HealthSession()
        heartRateState = MeasurementState.Idle
        respiratoryRateState = MeasurementState.Idle
    }

    fun updateHeartRate(state: MeasurementState) {
        heartRateState = state
        if (state is MeasurementState.Success) {
            session = session.copy(heartRate = state.value)
        }
    }

    fun updateRespiratoryRate(state: MeasurementState) {
        respiratoryRateState = state
        if (state is MeasurementState.Success) {
            session = session.copy(respiratoryRate = state.value)
        }
    }

    fun updateSymptomRating(symptom: Symptom, rating: Int) {
        session = session.copy(
            symptomRatings = session.symptomRatings + (symptom to rating)
        )
    }
}
